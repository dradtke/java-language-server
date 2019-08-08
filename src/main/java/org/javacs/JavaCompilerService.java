package org.javacs;

import java.io.File;
import java.net.URI;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javax.tools.*;
import org.javacs.lsp.SymbolInformation;

// TODO eliminate uses of URI in favor of Path
class JavaCompilerService {
    // Not modifiable! If you want to edit these, you need to create a new instance
    final Set<Path> classPath, docPath;
    final Set<String> addExports;
    final ReusableCompiler compiler = new ReusableCompiler();
    final Set<String> warmPackages = new HashSet<>();
    final Docs docs;
    final Set<String> jdkClasses = Classes.jdkTopLevelClasses(), classPathClasses;
    // Diagnostics from the last compilation task
    final List<Diagnostic<? extends JavaFileObject>> diags = new ArrayList<>();
    // Use the same file manager for multiple tasks, so we don't repeatedly re-compile the same files
    // TODO intercept files that aren't in the batch and erase method bodies so compilation is faster
    final SourceFileManager fileManager;

    JavaCompilerService(Set<Path> classPath, Set<Path> docPath, Set<String> addExports) {
        System.err.println("Class path:");
        for (var p : classPath) {
            System.err.println("  " + p);
        }
        System.err.println("Doc path:");
        for (var p : docPath) {
            System.err.println("  " + p);
        }
        // classPath can't actually be modified, because JavaCompiler remembers it from task to task
        this.classPath = Collections.unmodifiableSet(classPath);
        this.docPath = Collections.unmodifiableSet(docPath);
        this.addExports = Collections.unmodifiableSet(addExports);
        this.docs = new Docs(docPath);
        this.classPathClasses = Classes.classPathTopLevelClasses(classPath);
        this.fileManager = new SourceFileManager();
    }

    /** Combine source path or class path entries using the system separator, for example ':' in unix */
    private static String joinPath(Collection<Path> classOrSourcePath) {
        return classOrSourcePath.stream().map(p -> p.toString()).collect(Collectors.joining(File.pathSeparator));
    }

    static List<String> options(Set<Path> classPath, Set<String> addExports) {
        var list = new ArrayList<String>();

        Collections.addAll(list, "-classpath", joinPath(classPath));
        Collections.addAll(list, "--add-modules", "ALL-MODULE-PATH");
        // Collections.addAll(list, "-verbose");
        Collections.addAll(list, "-proc:none");
        Collections.addAll(list, "-g");
        // You would think we could do -Xlint:all,
        // but some lints trigger fatal errors in the presence of parse errors
        Collections.addAll(
                list,
                "-Xlint:cast",
                "-Xlint:deprecation",
                "-Xlint:empty",
                "-Xlint:fallthrough",
                "-Xlint:finally",
                "-Xlint:path",
                "-Xlint:unchecked",
                "-Xlint:varargs",
                "-Xlint:static");
        for (var export : addExports) {
            list.add("--add-exports");
            list.add(export + "=ALL-UNNAMED");
        }

        return list;
    }

    Docs docs() {
        return docs;
    }

    CompileBatch compileFocus(URI uri, int line, int character) {
        var contents = Parser.parseFile(uri).prune(line, character);
        var file = new SourceFileObject(uri, contents, Instant.now());
        return compileBatch(List.of(file));
    }

    CompileBatch compileFile(URI uri) {
        return compileUris(Collections.singleton(uri));
    }

    CompileBatch compileUris(Collection<URI> uris) {
        if (uris.isEmpty()) throw new RuntimeException("No source files");
        var files = new ArrayList<File>();
        for (var p : uris) files.add(new File(p));
        var sources = fileManager.getJavaFileObjectsFromFiles(files);
        return compileBatch(sources);
    }

    CompileBatch compilePaths(Collection<Path> paths) {
        if (paths.isEmpty()) throw new RuntimeException("No source files");
        var files = new ArrayList<File>();
        for (var path : paths) files.add(path.toFile());
        var sources = fileManager.getJavaFileObjectsFromFiles(files);
        return compileBatch(sources);
    }

    CompileBatch compileBatch(Collection<? extends JavaFileObject> sources) {
        warmUpPackages(sources);
        return new CompileBatch(this, sources);
    }

    /**
     * The first time we compile a file in a new package, we need to compile all files in that package to discover
     * package-private classes.
     */
    private void warmUpPackages(Collection<? extends JavaFileObject> sources) {
        var needsCompile = new HashSet<Path>();
        for (var source : sources) {
            var uri = source.toUri();
            var path = Paths.get(uri);
            var pkg = FileStore.packageName(path);
            if (!warmPackages.contains(pkg)) {
                LOG.info("...first time compiling sources in package " + pkg);
                var filesInPackage = FileStore.list(pkg);
                for (var f : filesInPackage) {
                    if (containsPackagePrivateClass(f)) {
                        needsCompile.add(f);
                    }
                }
                warmPackages.add(pkg);
            }
        }
        if (needsCompile.isEmpty()) {
            return;
        }
        // TODO consider pruning each source to speed up compile times
        LOG.info(String.format("...compile %d files that contain package-private classes", needsCompile.size()));
        var batch = compilePaths(needsCompile);
        batch.close();
    }

    private boolean containsPackagePrivateClass(Path file) {
        var parse = Parser.parseFile(file.toUri());
        return parse.containsPackagePrivateClass();
    }

    List<SymbolInformation> findSymbols(String query, int limit) {
        LOG.info(String.format("Searching for `%s`...", query));

        var result = new ArrayList<SymbolInformation>();
        var files = FileStore.all();
        var checked = 0;
        var parsed = 0;
        for (var file : files) {
            checked++;
            // First do a fast check if the query matches anything in a file
            if (!StringSearch.containsWordMatching(file, query)) continue;
            // Parse the file and check class members for matches
            LOG.info(String.format("...%s contains text matches", file.getFileName()));
            var parse = Parser.parseFile(file.toUri());
            var symbols = parse.findSymbolsMatching(query);
            parsed++;
            // If we confirm matches, add them to the results
            if (symbols.size() > 0) {
                LOG.info(String.format("...found %d occurrences", symbols.size()));
            }
            result.addAll(symbols);
            // If results are full, stop
            if (result.size() >= limit) break;
        }
        LOG.info(String.format("Found %d matches in %d/%d/%d files", result.size(), checked, parsed, files.size()));

        return result;
    }

    private static final Logger LOG = Logger.getLogger("main");
}
