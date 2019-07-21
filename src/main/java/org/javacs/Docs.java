package org.javacs;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javax.tools.*;

public class Docs {

    /** File manager with source-path + platform sources, which we will use to look up individual source files */
    private final SourceFileManager fileManager = new SourceFileManager();

    Docs(Set<Path> docPath) {
        // Path to source .jars + src.zip
        var sourcePathFiles = docPath.stream().map(Path::toFile).collect(Collectors.toSet());

        try {
            fileManager.setLocation(StandardLocation.SOURCE_PATH, sourcePathFiles);
            Optional<Path> srcZipPath = srcZip();
            if (srcZipPath.isPresent()) {
                fileManager.setLocationFromPaths(StandardLocation.MODULE_SOURCE_PATH, Set.of(srcZipPath.get()));
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public Optional<JavaFileObject> find(Ptr ptr) {
        LOG.info(String.format("...looking for file for `%s`...", ptr));

        // Find the file el was declared in
        var className = ptr.qualifiedClassName();
        try {
            var fromSourcePath =
                    fileManager.getJavaFileForInput(
                            StandardLocation.SOURCE_PATH, className, JavaFileObject.Kind.SOURCE);
            if (fromSourcePath != null) {
                LOG.info(String.format("...found %s on source path", fromSourcePath.toUri().getPath()));
                return Optional.of(fromSourcePath);
            }
            for (var module : Classes.JDK_MODULES) {
                var moduleLocation = fileManager.getLocationForModule(StandardLocation.MODULE_SOURCE_PATH, module);
                if (moduleLocation == null) continue;
                var fromModuleSourcePath =
                        fileManager.getJavaFileForInput(moduleLocation, className, JavaFileObject.Kind.SOURCE);
                if (fromModuleSourcePath != null) {
                    LOG.info(String.format("...found %s in module %s of jdk", fromModuleSourcePath.toUri(), module));
                    return Optional.of(fromModuleSourcePath);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        LOG.info(String.format("...couldn't find file for top-level class `%s`", className));
        return Optional.empty();
    }

    private static Optional<Path> cacheSrcZip;

    private static Optional<Path> srcZip() {
        if (cacheSrcZip == null) {
            cacheSrcZip = findSrcZip();
        }
        if (cacheSrcZip.isEmpty()) {
            return Optional.empty();
        }
        try {
            var fs = FileSystems.newFileSystem(cacheSrcZip.get(), Docs.class.getClassLoader());
            return Optional.of(fs.getPath("/"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static Optional<Path> findSrcZip() {
        // TODO try something else when JAVA_HOME isn't defined
        var javaHome = System.getenv("JAVA_HOME");
        if (javaHome == null) {
            LOG.warning("Couldn't find src.zip because JAVA_HOME is not defined");
            return Optional.empty();
        }
        String[] locations = {
            "lib/src.zip", "src.zip",
        };
        for (var rel : locations) {
            var abs = Paths.get(javaHome).resolve(rel);
            if (Files.exists(abs)) {
                LOG.info("Found " + abs);
                return Optional.of(abs);
            }
        }
        LOG.warning("Couldn't find src.zip in " + javaHome);
        return Optional.empty();
    }

    private static final Logger LOG = Logger.getLogger("main");
}
