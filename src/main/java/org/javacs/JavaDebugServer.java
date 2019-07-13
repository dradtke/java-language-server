package org.javacs;

import com.sun.jdi.*;
import com.sun.jdi.connect.AttachingConnector;
import com.sun.jdi.connect.IllegalConnectorArgumentsException;
import com.sun.jdi.event.*;
import com.sun.jdi.request.EventRequest;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Logger;
import org.javacs.debug.*;

class JavaDebugServer implements DebugServer {
    public static void main(String[] args) { // TODO don't show references for main method
        new DebugAdapter(JavaDebugServer::new, System.in, System.out).run();
    }

    private final DebugClient client;
    private VirtualMachine vm;
    private final List<Breakpoint> pendingBreakpoints = new ArrayList<>();
    private static int breakPointCounter = 0;

    JavaDebugServer(DebugClient client) {
        this.client = client;
    }

    @Override
    public Capabilities initialize(InitializeRequestArguments req) {
        var resp = new Capabilities();
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
        throw new UnsupportedOperationException();
    }

    @Override
    public void setExceptionBreakpoints(SetExceptionBreakpointsArguments req) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void configurationDone() {}

    @Override
    public void launch(LaunchRequestArguments req) {}

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
        var conn = connector("dt_socket");
        var args = conn.defaultArguments();
        args.get("port").setValue(Integer.toString(req.port));
        try {
            vm = conn.attach(args);
        } catch (IOException | IllegalConnectorArgumentsException e) {
            throw new RuntimeException(e);
        }
        listenForClassPrepareEvents();
        enablePendingBreakpointsInLoadedClasses();
        new java.lang.Thread(new ReceiveEvents(), "receive-events").start();
        vm.resume();
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
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }

        private void process(com.sun.jdi.event.Event event) {
            if (event instanceof ClassPrepareEvent) {
                var prepare = (ClassPrepareEvent) event;
                var type = prepare.referenceType();
                LOG.info("ClassPrepareRequest for class " + type.name() + " in source " + path(type));
                enableBreakpointsInClass(type);
                event.virtualMachine().resume();
            } else if (event instanceof com.sun.jdi.event.BreakpointEvent) {
                var evt = new StoppedEventBody();
                evt.reason = "breakpoint";
                client.stopped(evt);
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
                vm.eventRequestManager().createBreakpointRequest(line).enable();
            }
            if (locations.isEmpty()) {
                var failed = new BreakpointEventBody();
                var msg = "Class was loaded, but line " + b.line + " could not be found or had no code on it";
                failed.reason = msg;
                failed.breakpoint = b;
                b.verified = false;
                b.message = msg;
                client.breakpoint(failed);
            } else {
                var ok = new BreakpointEventBody();
                var msg = "Class was loaded, line " + b.line + " was found";
                ok.reason = msg;
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
    public void disconnect(DisconnectArguments req) {}

    @Override
    public void terminate(TerminateArguments req) {}

    @Override
    public void continue_(ContinueArguments req) {
        vm.resume();
    }

    @Override
    public void next(NextArguments req) {}

    @Override
    public void stepIn(StepInArguments req) {}

    @Override
    public void stepOut(StepOutArguments req) {}

    @Override
    public ThreadsResponseBody threads() {
        throw new UnsupportedOperationException();
    }

    @Override
    public VariablesResponseBody variables(VariablesArguments req) {
        throw new UnsupportedOperationException();
    }

    @Override
    public EvaluateResponseBody evaluate(EvaluateArguments req) {
        throw new UnsupportedOperationException();
    }

    private static final Logger LOG = Logger.getLogger("main");
}
