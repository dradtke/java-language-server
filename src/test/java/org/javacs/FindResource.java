package org.javacs;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/** Find java sources in test-project/workspace/src */
public class FindResource {
    public static URI uri(String resourcePath) {
        var path = path(resourcePath);

        return path.toUri();
    }

    public static String contents(String resourcePath) {
        var path = path(resourcePath);
        List<String> lines;
        try {
            lines = Files.readAllLines(path);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return String.join("\n", lines);
    }

    public static Path path(String resourcePath) {
        if (resourcePath.startsWith("/")) resourcePath = resourcePath.substring(1);

        return Paths.get("./src/test/examples/test-project/workspace/src")
                .resolve(resourcePath)
                .toAbsolutePath()
                .normalize();
    }
}
