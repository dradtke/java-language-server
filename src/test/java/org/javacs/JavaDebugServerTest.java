package org.javacs;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.logging.Logger;
import org.javacs.debug.*;
import org.junit.Before;
import org.junit.Test;

public class JavaDebugServerTest {
    Path workingDirectory = Paths.get("src/test/debug");
    DebugClient client = new MockClient();
    JavaDebugServer server = new JavaDebugServer(client);
    Process process;
    ArrayBlockingQueue<StoppedEventBody> stopped = new ArrayBlockingQueue<>(10);

    class MockClient implements DebugClient {
        @Override
        public void initialized() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void stopped(StoppedEventBody evt) {
            stopped.add(evt);
        }

        @Override
        public void exited(ExitedEventBody evt) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void output(OutputEventBody evt) {
            throw new UnsupportedOperationException();
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
                List.of("java", "-Xdebug", "-Xrunjdwp:transport=dt_socket,address=8000,server=y,suspend=y", "Hello");
        LOG.info("Launch " + String.join(", ", command));
        process = new ProcessBuilder().command(command).directory(workingDirectory.toFile()).inheritIO().start();
        java.lang.Thread.sleep(1000);
    }

    @Test
    public void attachToProcess() throws InterruptedException {
        var attach = new AttachRequestArguments();
        attach.port = 8000;
        server.attach(attach);
        process.waitFor();
    }

    @Test
    public void setBreakpoint() throws InterruptedException {
        // Set a breakpoint at HelloWorld.java:4
        var set = new SetBreakpointsArguments();
        var point = new SourceBreakpoint();
        point.line = 4;
        set.source.path = workingDirectory.resolve("Hello.java").toString();
        set.breakpoints = new SourceBreakpoint[] {point};
        server.setBreakpoints(set);
        // Attach to the process
        var attach = new AttachRequestArguments();
        attach.port = 8000;
        server.attach(attach);
        // Wait for stop
        stopped.take();
        // Wait for process to exit
        server.continue_(new ContinueArguments());
        process.waitFor();
    }

    private static final Logger LOG = Logger.getLogger("main");
}
