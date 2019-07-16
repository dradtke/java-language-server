package org.javacs;

import com.sun.jdi.*;
import com.sun.jdi.connect.AttachingConnector;
import com.sun.jdi.connect.IllegalConnectorArgumentsException;
import com.sun.jdi.event.*;
import com.sun.jdi.request.EventRequest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.logging.*;
import java.util.stream.Collectors;
import org.javacs.debug.*;

class JavaDebugServer implements DebugServer {
    public static void main(String[] args) { // TODO don't show references for main method
        LOG.info(String.join(" ", args));
        new DebugAdapter(JavaDebugServer::new, System.in, System.out).run();
        System.exit(0);
    }

    private final DebugClient client;
    private List<Path> sourceRoots = List.of();
    private VirtualMachine vm;
    private final List<Breakpoint> pendingBreakpoints = new ArrayList<>();
    private static int breakPointCounter = 0;

    JavaDebugServer(DebugClient client) {
        this.client = client;
        class LogToConsole extends Handler {
            @Override
            public void publish(LogRecord r) {
                var evt = new OutputEventBody();
                evt.category = "console";
                evt.output = r.getSourceClassName() + "\t" + r.getSourceMethodName() + "\t" + r.getMessage() + "\n";
                client.output(evt);
            }

            @Override
            public void flush() {}

            @Override
            public void close() {}
        }
        Logger.getLogger("main").addHandler(new LogToConsole());
    }

    @Override
    public Capabilities initialize(InitializeRequestArguments req) {
        var resp = new Capabilities();
        resp.supportsConfigurationDoneRequest = true;
        return resp;
    }

    @Override
    public SetBreakpointsResponseBody setBreakpoints(SetBreakpointsArguments req) {
        // Add these breakpoints to the pending set
        var resp = new SetBreakpointsResponseBody();
        resp.breakpoints = new Breakpoint[req.breakpoints.length];
        for (var i = 0; i < req.breakpoints.length; i++) {
            var breakpoint = req.breakpoints[i];
            var pending = new Breakpoint();
            pending.id = breakPointCounter++;
            pending.source = new Source();
            pending.source.path = req.source.path;
            pending.line = breakpoint.line;
            pending.column = breakpoint.column;
            pending.verified = false;
            pending.message = "Class not yet loaded";
            resp.breakpoints[i] = pending;
            pendingBreakpoints.add(pending);
        }
        return resp;
    }

    @Override
    public SetFunctionBreakpointsResponseBody setFunctionBreakpoints(SetFunctionBreakpointsArguments req) {
        LOG.warning("Not yet implemented");
        return new SetFunctionBreakpointsResponseBody();
    }

    @Override
    public void setExceptionBreakpoints(SetExceptionBreakpointsArguments req) {
        LOG.warning("Not yet implemented");
    }

    @Override
    public void configurationDone() {
        listenForClassPrepareEvents();
        enablePendingBreakpointsInLoadedClasses();
        vm.resume();
    }

    @Override
    public void launch(LaunchRequestArguments req) {
        throw new UnsupportedOperationException();
    }

    private static AttachingConnector connector(String transport) {
        var found = new ArrayList<String>();
        for (var conn : Bootstrap.virtualMachineManager().attachingConnectors()) {
            if (conn.transport().name().equals(transport)) {
                return conn;
            }
            found.add(conn.transport().name());
        }
        throw new RuntimeException("Couldn't find connector for transport " + transport + " in " + found);
    }

    @Override
    public void attach(AttachRequestArguments req) {
        // Remember available source roots
        sourceRoots = new ArrayList<Path>();
        for (var string : req.sourceRoots) {
            var path = Paths.get(string);
            if (!Files.exists(path)) {
                LOG.warning(string + " does not exist");
                continue;
            } else if (!Files.isDirectory(path)) {
                LOG.warning(string + " is not a directory");
                continue;
            } else {
                LOG.info(path + " is a source root");
                sourceRoots.add(path);
            }
        }
        // Attach to the running VM
        var conn = connector("dt_socket");
        var args = conn.defaultArguments();
        args.get("port").setValue(Integer.toString(req.port));
        try {
            vm = conn.attach(args);
        } catch (IOException | IllegalConnectorArgumentsException e) {
            throw new RuntimeException(e);
        }
        // Create a thread that reads events from the VM
        var reader = new java.lang.Thread(new ReceiveEvents(), "receive-events");
        reader.setDaemon(true);
        reader.start();
        // Tell the client we are ready to receive breakpoints
        client.initialized();
    }

    class ReceiveEvents implements Runnable {
        @Override
        public void run() {
            var events = vm.eventQueue();
            while (true) {
                try {
                    var nextSet = events.remove();
                    for (var event : nextSet) {
                        process(event);
                    }
                } catch (Exception e) {
                    LOG.log(Level.SEVERE, e.getMessage(), e);
                    return;
                }
            }
        }

        private void process(com.sun.jdi.event.Event event) {
            LOG.info(event.toString());
            if (event instanceof ClassPrepareEvent) {
                var prepare = (ClassPrepareEvent) event;
                var type = prepare.referenceType();
                LOG.info("ClassPrepareRequest for class " + type.name() + " in source " + path(type));
                enableBreakpointsInClass(type);
                vm.resume();
            } else if (event instanceof com.sun.jdi.event.BreakpointEvent) {
                var breakpoint = (com.sun.jdi.event.BreakpointEvent) event;
                var evt = new StoppedEventBody();
                evt.reason = "breakpoint";
                evt.threadId = breakpoint.thread().uniqueID();
                evt.allThreadsStopped = breakpoint.request().suspendPolicy() == EventRequest.SUSPEND_ALL;
                client.stopped(evt);
            } else if (event instanceof VMDeathEvent) {
                client.exited(new ExitedEventBody());
            } else if (event instanceof VMDisconnectEvent) {
                client.terminated(new TerminatedEventBody());
            }
        }
    }

    /* Set breakpoints for already-loaded classes */
    private void enablePendingBreakpointsInLoadedClasses() {
        Objects.requireNonNull(vm, "vm has not been initialized");
        var paths = distinctSourceNames();
        for (var type : vm.allClasses()) {
            var path = path(type);
            if (paths.contains(path)) {
                enableBreakpointsInClass(type);
            }
        }
    }

    private void enableBreakpointsInClass(ReferenceType type) {
        // Check that class has source information
        var path = path(type);
        if (path == null) return;
        // Look for pending breakpoints that can be enabled
        var enabled = new ArrayList<Breakpoint>();
        for (var b : pendingBreakpoints) {
            if (b.source.path.endsWith(path)) {
                enableBreakpoint(b, type);
                enabled.add(b);
            }
        }
        pendingBreakpoints.removeAll(enabled);
    }

    private void enableBreakpoint(Breakpoint b, ReferenceType type) {
        LOG.info("Enable breakpoint at " + b.source.path + ":" + b.line);
        try {
            var locations = type.locationsOfLine(b.line);
            for (var line : locations) {
                var req = vm.eventRequestManager().createBreakpointRequest(line);
                req.setSuspendPolicy(EventRequest.SUSPEND_ALL);
                req.enable();
            }
            if (locations.isEmpty()) {
                var failed = new BreakpointEventBody();
                failed.reason = "changed";
                failed.breakpoint = b;
                b.verified = false;
                b.message = b.source.name + ":" + b.line + " could not be found or had no code on it";
                client.breakpoint(failed);
            } else {
                var ok = new BreakpointEventBody();
                ok.reason = "changed";
                ok.breakpoint = b;
                b.verified = true;
                b.message = null;
                client.breakpoint(ok);
            }
        } catch (AbsentInformationException e) {
            throw new RuntimeException(e);
        }
    }

    private String path(ReferenceType type) {
        try {
            for (var path : type.sourcePaths(vm.getDefaultStratum())) {
                return path;
            }
            return null;
        } catch (AbsentInformationException __) {
            return null;
        }
    }

    /* Request to be notified when files with pending breakpoints are loaded */
    private void listenForClassPrepareEvents() {
        Objects.requireNonNull(vm, "vm has not been initialized");
        for (var name : distinctSourceNames()) {
            LOG.info("Listen for ClassPrepareRequest in " + name);
            var requestClassEvent = vm.eventRequestManager().createClassPrepareRequest();
            requestClassEvent.addSourceNameFilter("*" + name);
            requestClassEvent.setSuspendPolicy(EventRequest.SUSPEND_ALL);
            requestClassEvent.enable();
        }
    }

    private Set<String> distinctSourceNames() {
        var distinctSourceNames = new HashSet<String>();
        for (var b : pendingBreakpoints) {
            var path = Paths.get(b.source.path);
            var name = path.getFileName();
            distinctSourceNames.add(name.toString());
        }
        return distinctSourceNames;
    }

    @Override
    public void disconnect(DisconnectArguments req) {
        try {
            vm.dispose();
        } catch (VMDisconnectedException __) {
            LOG.warning("VM has already terminated");
        }
        vm = null;
    }

    @Override
    public void terminate(TerminateArguments req) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void continue_(ContinueArguments req) {
        vm.resume();
    }

    @Override
    public void next(NextArguments req) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void stepIn(StepInArguments req) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void stepOut(StepOutArguments req) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ThreadsResponseBody threads() {
        var threads = new ThreadsResponseBody();
        threads.threads = vm.allThreads().stream().map(this::asThread).toArray(org.javacs.debug.Thread[]::new);
        return threads;
    }

    private org.javacs.debug.Thread asThread(ThreadReference t) {
        var thread = new org.javacs.debug.Thread();
        thread.id = t.uniqueID();
        thread.name = t.name();
        return thread;
    }

    @Override
    public StackTraceResponseBody stackTrace(StackTraceArguments req) {
        try {
            for (var t : vm.allThreads()) {
                if (t.uniqueID() == req.threadId) {
                    var length = t.frameCount() - req.startFrame;
                    if (req.levels != null && req.levels < length) {
                        length = req.levels;
                    }
                    var frames = t.frames(req.startFrame, length);
                    var resp = new StackTraceResponseBody();
                    resp.stackFrames =
                            frames.stream().map(this::asStackFrame).toArray(org.javacs.debug.StackFrame[]::new);
                    resp.totalFrames = t.frameCount();
                    return resp;
                }
            }
            throw new RuntimeException("Couldn't find thread " + req.threadId);
        } catch (IncompatibleThreadStateException e) {
            throw new RuntimeException(e);
        }
    }

    private org.javacs.debug.StackFrame asStackFrame(com.sun.jdi.StackFrame f) {
        var frame = new org.javacs.debug.StackFrame();
        frame.id = uniqueFrameId(f);
        frame.name = f.location().method().name();
        frame.source = asSource(f.location());
        frame.line = f.location().lineNumber();
        return frame;
    }

    private Source asSource(Location l) {
        try {
            var path = findSource(l);
            var src = new Source();
            src.name = l.sourceName();
            src.path = Objects.toString(path, null);
            return src;
        } catch (AbsentInformationException e) {
            throw new RuntimeException(e);
        }
    }

    private Path findSource(Location l) {
        for (var root : sourceRoots) {
            try {
                var path = root.resolve(l.sourcePath());
                if (Files.exists(path)) {
                    return path;
                }
            } catch (AbsentInformationException __) {
                LOG.warning(l + " has no location information");
                return null;
            }
        }
        var in = sourceRoots.stream().map(Path::toString).collect(Collectors.joining(", "));
        LOG.warning("Could not find " + l + " in " + in);
        return null;
    }

    /** Debug adapter protocol doesn't seem to like frame 0 */
    private static final int FRAME_OFFSET = 100;

    private long uniqueFrameId(com.sun.jdi.StackFrame f) {
        try {
            long count = FRAME_OFFSET;
            for (var thread : f.virtualMachine().allThreads()) {
                if (thread.equals(f.thread())) {
                    for (var frame : thread.frames()) {
                        if (frame.equals(f)) {
                            return count;
                        } else {
                            count++;
                        }
                    }
                } else {
                    count += thread.frameCount();
                }
            }
            return count;
        } catch (IncompatibleThreadStateException e) {
            throw new RuntimeException(e);
        }
    }

    private com.sun.jdi.StackFrame findFrame(long id) {
        try {
            long count = FRAME_OFFSET;
            for (var thread : vm.allThreads()) {
                if (id < count + thread.frameCount()) {
                    var offset = (int) (id - count);
                    return thread.frame(offset);
                } else {
                    count += thread.frameCount();
                }
            }
            throw new RuntimeException("Couldn't find frame " + id);
        } catch (IncompatibleThreadStateException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public ScopesResponseBody scopes(ScopesArguments req) {
        var resp = new ScopesResponseBody();
        var locals = new Scope();
        locals.name = "Locals";
        locals.presentationHint = "locals";
        locals.variablesReference = req.frameId * 2;
        var arguments = new Scope();
        arguments.name = "Arguments";
        arguments.presentationHint = "arguments";
        arguments.variablesReference = req.frameId * 2 + 1;
        resp.scopes = new Scope[] {locals, arguments};
        return resp;
    }

    @Override
    public VariablesResponseBody variables(VariablesArguments req) {
        var frameId = req.variablesReference / 2;
        var scopeId = (int) (req.variablesReference % 2);
        var frame = findFrame(frameId);
        var resp = new VariablesResponseBody();
        switch (scopeId) {
            case 0: // locals
                resp.variables = locals(frame);
                break;
            case 1: // arguments
                resp.variables = arguments(frame);
                break;
        }
        return resp;
    }

    private Variable[] locals(com.sun.jdi.StackFrame frame) {
        return visible(frame)
                .stream()
                .filter(v -> !v.isArgument())
                .map(v -> asVariable(v, frame))
                .toArray(Variable[]::new);
    }

    private Variable[] arguments(com.sun.jdi.StackFrame frame) {
        return visible(frame)
                .stream()
                .filter(v -> v.isArgument())
                .map(v -> asVariable(v, frame))
                .toArray(Variable[]::new);
    }

    private List<LocalVariable> visible(com.sun.jdi.StackFrame frame) {
        try {
            return frame.visibleVariables();
        } catch (AbsentInformationException __) {
            try {
                LOG.warning(
                        String.format(
                                "No visible variable information in %s:%d",
                                frame.location().sourceName(), frame.location().lineNumber()));
            } catch (AbsentInformationException _again) { // TODO this should not warn
                LOG.warning(String.format("No visible variable information in %s", frame.toString()));
            }
            return List.of();
        }
    }

    private Variable asVariable(LocalVariable v, com.sun.jdi.StackFrame frame) {
        Variable convert = new Variable();
        convert.name = v.name();
        convert.value = frame.getValue(v).toString();
        convert.type = v.typeName();
        convert.variablesReference =
                -1; // TODO set variablesReference and allow inspecting structure of collections and POJOs
        // TODO set variablePresentationHint
        return convert;
    }

    @Override
    public EvaluateResponseBody evaluate(EvaluateArguments req) {
        throw new UnsupportedOperationException();
    }

    private static final Logger LOG = Logger.getLogger("main");
}
