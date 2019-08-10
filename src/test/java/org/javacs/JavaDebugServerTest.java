package org.javacs;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.logging.Logger;
import org.javacs.debug.*;
import org.javacs.debug.proto.*;
import org.junit.Before;
import org.junit.Test;

public class JavaDebugServerTest {
    Path workingDirectory = Paths.get("src/test/examples/debug");
    DebugClient client = new MockClient();
    JavaDebugServer server = new JavaDebugServer(client);
    Process process;
    ArrayBlockingQueue<StoppedEventBody> stoppedEvents = new ArrayBlockingQueue<>(10);

    class MockClient implements DebugClient {
        @Override
        public void initialized() {}

        @Override
        public void stopped(StoppedEventBody evt) {
            stoppedEvents.add(evt);
        }

        @Override
        public void terminated(TerminatedEventBody evt) {}

        @Override
        public void exited(ExitedEventBody evt) {}

        @Override
        public void output(OutputEventBody evt) {
            LOG.info(evt.output);
        }

        @Override
        public void breakpoint(BreakpointEventBody evt) {
            if (evt.breakpoint.verified) {
                LOG.info(
                        String.format(
                                "Breakpoint at %s:%d is verified", evt.breakpoint.source.path, evt.breakpoint.line));
            } else {
                LOG.info(
                        String.format(
                                "Breakpoint at %s:%d cannot be verified because %s",
                                evt.breakpoint.source.path, evt.breakpoint.line, evt.breakpoint.message));
            }
        }

        @Override
        public RunInTerminalResponseBody runInTerminal(RunInTerminalRequest req) {
            throw new UnsupportedOperationException();
        }
    }

    @Before
    public void launchProcess() throws IOException, InterruptedException {
        var command =
                List.of("java", "-Xdebug", "-Xrunjdwp:transport=dt_socket,address=5005,server=y,suspend=y", "Hello");
        LOG.info("Launch " + String.join(", ", command));
        process = new ProcessBuilder().command(command).directory(workingDirectory.toFile()).inheritIO().start();
        java.lang.Thread.sleep(1000);
    }

    private void attach(int port) {
        var attach = new AttachRequestArguments();
        attach.port = port;
        attach.sourceRoots = new String[] {};
        server.attach(attach);
    }

    private void setBreakpoint(int line) {
        var set = new SetBreakpointsArguments();
        var point = new SourceBreakpoint();
        point.line = line;
        set.source.path = workingDirectory.resolve("Hello.java").toString();
        set.breakpoints = new SourceBreakpoint[] {point};
        server.setBreakpoints(set);
    }

    @Test
    public void attachToProcess() throws InterruptedException {
        attach(5005);
        server.configurationDone();
        process.waitFor();
    }

    @Test
    public void setBreakpoint() throws InterruptedException {
        // Attach to the process
        attach(5005);
        // Set a breakpoint at HelloWorld.java:4
        setBreakpoint(4);
        server.configurationDone();
        // Wait for stop
        stoppedEvents.take();
        // Find the main thread
        var threads = server.threads().threads;
        for (var t : threads) {
            if (t.name.equals("main")) {
                // Get the stack trace
                var requestTrace = new StackTraceArguments();
                requestTrace.threadId = t.id;
                var stack = server.stackTrace(requestTrace);
                System.out.println("Thread main:");
                for (var frame : stack.stackFrames) {
                    System.out.println(String.format("\t%s:%d (%s)", frame.name, frame.line, frame.source.path));
                }
                // Get variables
                var requestScopes = new ScopesArguments();
                requestScopes.frameId = stack.stackFrames[0].id;
                var scopes = server.scopes(requestScopes).scopes;
                // Get locals
                var requestLocals = new VariablesArguments();
                requestLocals.variablesReference = scopes[0].variablesReference;
                var locals = server.variables(requestLocals).variables;
                System.out.println("Locals:");
                for (var v : locals) {
                    System.out.println(String.format("\t%s %s = %s", v.type, v.name, v.value));
                }
                // Get arguments
                var requestArgs = new VariablesArguments();
                requestArgs.variablesReference = scopes[1].variablesReference;
                var arguments = server.variables(requestArgs).variables;
                System.out.println("Arguments:");
                for (var v : arguments) {
                    System.out.println(String.format("\t%s %s = %s", v.type, v.name, v.value));
                }
            }
        }
        // Wait for process to exit
        server.continue_(new ContinueArguments());
        process.waitFor();
    }

    @Test
    public void step() throws InterruptedException {
        // Attach to the process
        attach(5005);
        // Set a breakpoint at HelloWorld.java:4
        setBreakpoint(4);
        server.configurationDone();
        // Wait for stop
        stoppedEvents.take();
        // Find the main thread
        var threads = server.threads().threads;
        for (var t : threads) {
            if (t.name.equals("main")) {
                var next = new NextArguments();
                next.threadId = t.id;
                server.next(next);
                // Wait for stop
                stoppedEvents.take();
            }
        }
        // Wait for process to exit
        server.continue_(new ContinueArguments());
        process.waitFor();
    }

    @Test
    public void addBreakpoint() throws InterruptedException {
        // Attach to the process
        attach(5005);
        // Stop at 4
        setBreakpoint(4);
        server.configurationDone();
        stoppedEvents.take();
        // Stop at 6
        setBreakpoint(6);
        server.continue_(new ContinueArguments());
        stoppedEvents.take();
        // Wait for process to exit
        server.continue_(new ContinueArguments());
        process.waitFor();
    }

    private static final Logger LOG = Logger.getLogger("main");
}
