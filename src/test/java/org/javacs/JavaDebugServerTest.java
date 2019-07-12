package org.javacs;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.javacs.debug.*;
import org.junit.Test;

public class JavaDebugServerTest {
    Path workingDirectory = Paths.get("src/test/debug");
    DebugClient client = new MockClient();
    JavaDebugServer server = new JavaDebugServer(client);

    class MockClient implements DebugClient {
        @Override
        public void initialized() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void stopped(StoppedEventBody evt) {
            throw new UnsupportedOperationException();
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
            throw new UnsupportedOperationException();
        }

        @Override
        public RunInTerminalResponseBody runInTerminal(RunInTerminalRequest req) {
            throw new UnsupportedOperationException();
        }
    }

    @Test
    public void attachToProcess() throws IOException, InterruptedException {
        var command =
                List.of("java", "-Xdebug", "-Xrunjdwp:transport=dt_socket,address=8000,server=y,suspend=y", "Hello");
        var process =
                new ProcessBuilder()
                        .command(command)
                        .directory(workingDirectory.toFile())
                        .redirectError(ProcessBuilder.Redirect.INHERIT)
                        .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                        .start();
        java.lang.Thread.sleep(1000);
        var req = new AttachRequestArguments();
        req.port = 8000;
        server.attach(req);
        process.waitFor();
    }
}
