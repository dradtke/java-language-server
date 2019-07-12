package org.javacs;

import com.sun.jdi.*;
import com.sun.jdi.connect.AttachingConnector;
import com.sun.jdi.connect.IllegalConnectorArgumentsException;
import com.sun.jdi.event.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
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
            LOG.info("Process " + event.getClass().getSimpleName());

            if (event instanceof ClassPrepareEvent) {
                // Check for pending breakpoints in this class
                var prepare = (ClassPrepareEvent) event;
                var type = prepare.referenceType();
                var path = path(type);
                if (path == null) return;
                var enabled = new ArrayList<Breakpoint>();
                for (var b : pendingBreakpoints) {
                    if (b.source.path.endsWith(path)) {
                        if (enableBreakpoint(b, type)) {
                            enabled.add(b);
                        }
                    }
                }
                pendingBreakpoints.removeAll(enabled);
                // Notify VSCode we've enabled breakpoints
                for (var b : enabled) {
                    var evt = new BreakpointEventBody();
                    evt.reason = "new";
                    evt.breakpoint = b;
                    client.breakpoint(evt);
                }
            }
        }

        private boolean enableBreakpoint(Breakpoint b, ReferenceType type) {
            try {
                var locations = type.locationsOfLine(b.line);
                for (var line : locations) {
                    vm.eventRequestManager().createBreakpointRequest(line).enable();
                }
                return !locations.isEmpty();
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
            } catch (AbsentInformationException e) {
                throw new RuntimeException(e);
            }
        }
    }

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
        // Request to be notified when classes in this file are loaded
        var requestClassEvent = vm.eventRequestManager().createClassPrepareRequest();
        requestClassEvent.addSourceNameFilter(req.source.path);
        requestClassEvent.enable();
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
        new java.lang.Thread(new ReceiveEvents(), "receive-events").start();
        vm.resume();
    }

    @Override
    public void disconnect(DisconnectArguments req) {}

    @Override
    public void terminate(TerminateArguments req) {}

    @Override
    public void continue_(ContinueArguments req) {}

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
