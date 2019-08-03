package org.javacs;

import com.google.gson.*;
import com.sun.source.tree.*;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.function.Predicate;
import java.util.logging.Logger;
import javax.lang.model.element.*;
import javax.tools.JavaFileObject;
import org.javacs.lsp.*;

class JavaLanguageServer extends LanguageServer {
    // TODO allow multiple workspace roots
    private Path workspaceRoot;
    private final LanguageClient client;
    private JavaCompilerService cacheCompiler;
    private JsonObject cacheSettings;
    private JsonObject settings = new JsonObject();

    JavaCompilerService compiler() {
        if (!settings.equals(cacheSettings)) {
            LOG.info("Recreating compiler because\n\t" + settings + "\nis different than\n\t" + cacheSettings);
            cacheCompiler = createCompiler();
            cacheSettings = settings;
        }
        return cacheCompiler;
    }

    void lint(Collection<URI> uris) {
        // TODO only lint the current focus, merging errors/decorations with existing
        LOG.info("Lint " + uris.size() + " files...");
        var started = Instant.now();
        if (uris.isEmpty()) return;
        try (var batch = compiler().compileUris(uris)) {
            // Report compilation errors
            for (var ds : batch.reportErrors()) {
                client.publishDiagnostics(ds);
            }
            uncheckedChanges = false;
        }
        var elapsed = Duration.between(started, Instant.now());
        LOG.info(String.format("...done linting in %d ms", elapsed.toMillis()));
    }

    private static final Gson gson = new Gson();

    private void javaStartProgress(JavaStartProgressParams params) {
        client.customNotification("java/startProgress", gson.toJsonTree(params));
    }

    private void javaReportProgress(JavaReportProgressParams params) {
        client.customNotification("java/reportProgress", gson.toJsonTree(params));
    }

    private void javaEndProgress() {
        client.customNotification("java/endProgress", JsonNull.INSTANCE);
    }

    private JavaCompilerService createCompiler() {
        Objects.requireNonNull(workspaceRoot, "Can't create compiler because workspaceRoot has not been initialized");

        javaStartProgress(new JavaStartProgressParams("Configure javac"));
        javaReportProgress(new JavaReportProgressParams("Finding source roots"));

        var externalDependencies = externalDependencies();
        var classPath = classPath();
        var addExports = addExports();
        // If classpath is specified by the user, don't infer anything
        if (!classPath.isEmpty()) {
            javaEndProgress();
            return new JavaCompilerService(classPath, Collections.emptySet(), addExports);
        }
        // Otherwise, combine inference with user-specified external dependencies
        else {
            var infer = new InferConfig(workspaceRoot, externalDependencies);

            javaReportProgress(new JavaReportProgressParams("Inferring class path"));
            classPath = infer.classPath();

            javaReportProgress(new JavaReportProgressParams("Inferring doc path"));
            var docPath = infer.buildDocPath();

            javaEndProgress();
            return new JavaCompilerService(classPath, docPath, addExports);
        }
    }

    private Set<String> externalDependencies() {
        if (!settings.has("externalDependencies")) return Set.of();
        var array = settings.getAsJsonArray("externalDependencies");
        var strings = new HashSet<String>();
        for (var each : array) {
            strings.add(each.getAsString());
        }
        return strings;
    }

    private Set<Path> classPath() {
        if (!settings.has("classPath")) return Set.of();
        var array = settings.getAsJsonArray("classPath");
        var paths = new HashSet<Path>();
        for (var each : array) {
            paths.add(Paths.get(each.getAsString()).toAbsolutePath());
        }
        return paths;
    }

    private Set<String> addExports() {
        if (!settings.has("addExports")) return Set.of();
        var array = settings.getAsJsonArray("addExports");
        var strings = new HashSet<String>();
        for (var each : array) {
            strings.add(each.getAsString());
        }
        return strings;
    }

    @Override
    public InitializeResult initialize(InitializeParams params) {
        this.workspaceRoot = Paths.get(params.rootUri);
        FileStore.setWorkspaceRoots(Set.of(Paths.get(params.rootUri)));

        var c = new JsonObject();
        c.addProperty("textDocumentSync", 2); // Incremental
        c.addProperty("hoverProvider", true);
        var completionOptions = new JsonObject();
        completionOptions.addProperty("resolveProvider", true);
        var triggerCharacters = new JsonArray();
        triggerCharacters.add(".");
        completionOptions.add("triggerCharacters", triggerCharacters);
        c.add("completionProvider", completionOptions);
        var signatureHelpOptions = new JsonObject();
        var signatureTrigger = new JsonArray();
        signatureTrigger.add("(");
        signatureTrigger.add(",");
        signatureHelpOptions.add("triggerCharacters", signatureTrigger);
        c.add("signatureHelpProvider", signatureHelpOptions);
        c.addProperty("referencesProvider", true);
        c.addProperty("definitionProvider", true);
        c.addProperty("workspaceSymbolProvider", true);
        c.addProperty("documentSymbolProvider", true);
        c.addProperty("documentFormattingProvider", true);
        var codeLensOptions = new JsonObject();
        codeLensOptions.addProperty("resolveProvider", true);
        c.add("codeLensProvider", codeLensOptions);
        c.addProperty("foldingRangeProvider", true);

        return new InitializeResult(c);
    }

    @Override
    public void initialized() {
        // Register for didChangeWatchedFiles notifications
        var options = new JsonObject();
        var watchers = new JsonArray();
        var watchJava = new JsonObject();
        watchJava.addProperty("globPattern", "**/*.java");
        watchers.add(watchJava);
        options.add("watchers", watchers);
        client.registerCapability("workspace/didChangeWatchedFiles", gson.toJsonTree(options));
    }

    @Override
    public void shutdown() {}

    public JavaLanguageServer(LanguageClient client) {
        this.client = client;
    }

    @Override
    public List<SymbolInformation> workspaceSymbols(WorkspaceSymbolParams params) {
        return compiler().findSymbols(params.query, 50);
    }

    @Override
    public void didChangeConfiguration(DidChangeConfigurationParams change) {
        var java = change.settings.getAsJsonObject().get("java");
        LOG.info("Received java settings " + java);
        settings = java.getAsJsonObject();
    }

    @Override
    public void didChangeWatchedFiles(DidChangeWatchedFilesParams params) {
        // TODO update config when pom.xml changes
        for (var c : params.changes) {
            if (!FileStore.isJavaFile(c.uri)) continue;
            var file = Paths.get(c.uri);
            switch (c.type) {
                case FileChangeType.Created:
                    FileStore.externalCreate(file);
                    break;
                case FileChangeType.Changed:
                    FileStore.externalChange(file);
                    break;
                case FileChangeType.Deleted:
                    FileStore.externalDelete(file);
                    break;
            }
        }
    }

    @Override
    public Optional<CompletionList> completion(TextDocumentPositionParams position) {
        var started = Instant.now();
        var uri = position.textDocument.uri;
        if (!FileStore.isJavaFile(uri)) return Optional.empty();
        var line = position.position.line + 1;
        var column = position.position.character + 1;
        LOG.info(String.format("Complete at %s(%d,%d)", uri.getPath(), line, column));
        // Figure out what kind of completion we want to do
        // TODO don't complete inside of comments
        var ctx = Parser.parseFile(uri).completionContext(line, column);
        if (ctx == CompletionContext.UNKNOWN) {
            var items = new ArrayList<CompletionItem>();
            for (var name : CompileBatch.TOP_LEVEL_KEYWORDS) {
                var i = new CompletionItem();
                i.label = name;
                i.kind = CompletionItemKind.Keyword;
                i.detail = "keyword";
                items.add(i);
            }
            return Optional.of(new CompletionList(true, items));
        }
        // Compile again, focusing on a region that depends on what type of completion we want to do
        List<CompletionItem> cs;
        boolean isIncomplete;
        try (var focus = compiler().compileFocus(uri, ctx.line, ctx.character)) {
            // Do a specific type of completion
            switch (ctx.kind) {
                case MemberSelect:
                    cs = focus.completeMembers(uri, ctx.line, ctx.character);
                    isIncomplete = false;
                    break;
                case MemberReference:
                    cs = focus.completeReferences(uri, ctx.line, ctx.character);
                    isIncomplete = false;
                    break;
                case Identifier:
                    cs =
                            focus.completeIdentifiers(
                                    uri, ctx.line, ctx.character, ctx.inClass, ctx.inMethod, ctx.partialName);
                    isIncomplete = cs.size() >= CompileBatch.MAX_COMPLETION_ITEMS;
                    break;
                case Annotation:
                    cs = focus.completeAnnotations(uri, ctx.line, ctx.character, ctx.partialName);
                    isIncomplete = cs.size() >= CompileBatch.MAX_COMPLETION_ITEMS;
                    break;
                case Case:
                    cs = focus.completeCases(uri, ctx.line, ctx.character);
                    isIncomplete = false;
                    break;
                default:
                    throw new RuntimeException("Unexpected completion context " + ctx.kind);
            }
        }
        // Log timing
        var elapsedMs = Duration.between(started, Instant.now()).toMillis();
        if (isIncomplete) LOG.info(String.format("Found %d items (incomplete) in %,d ms", cs.size(), elapsedMs));
        else LOG.info(String.format("...found %d items in %,d ms", cs.size(), elapsedMs));

        return Optional.of(new CompletionList(isIncomplete, cs));
    }

    private Optional<MarkupContent> findDocs(Ptr ptr) {
        LOG.info(String.format("Find docs for `%s`...", ptr));

        // Find el in the doc path
        var file = compiler().docs().find(ptr);
        if (!file.isPresent()) return Optional.empty();
        // Parse file and find el
        var parse = Parser.parseJavaFileObject(file.get());
        var path = parse.fuzzyFind(ptr);
        if (!path.isPresent()) return Optional.empty();
        // Parse the doctree associated with el
        var docTree = parse.doc(path.get());
        var string = Parser.asMarkupContent(docTree);
        return Optional.of(string);
    }

    private Optional<String> findMethodDetails(Ptr ptr) {
        LOG.info(String.format("Find details for method `%s`...", ptr));

        // TODO find and parse happens twice
        // Find method in the doc path
        var file = compiler().docs().find(ptr);
        if (!file.isPresent()) return Optional.empty();
        // Parse file and find method
        var parse = Parser.parseJavaFileObject(file.get());
        var path = parse.fuzzyFind(ptr);
        if (!path.isPresent()) return Optional.empty();
        // Should be a MethodTree
        var tree = path.get().getLeaf();
        if (!(tree instanceof MethodTree)) {
            LOG.warning(String.format("...method `%s` associated with non-method tree `%s`", ptr, tree));
            return Optional.empty();
        }
        // Write description of method using info from source
        var methodTree = (MethodTree) tree;
        var args = new StringJoiner(", ");
        for (var p : methodTree.getParameters()) {
            args.add(p.getName());
        }
        var details = String.format("%s %s(%s)", methodTree.getReturnType(), methodTree.getName(), args);
        return Optional.of(details);
    }

    @Override
    public CompletionItem resolveCompletionItem(CompletionItem unresolved) {
        if (unresolved.data == null) return unresolved;
        var ptr = new Ptr(unresolved.data.getAsString());
        var markdown = findDocs(ptr);
        if (markdown.isPresent()) {
            unresolved.documentation = markdown.get();
        }
        if (ptr.isMethod()) {
            var details = findMethodDetails(ptr);
            if (details.isPresent()) {
                unresolved.detail = details.get();
            }
        }
        return unresolved;
    }

    private String hoverTypeDeclaration(TypeElement t) {
        var result = new StringBuilder();
        switch (t.getKind()) {
            case ANNOTATION_TYPE:
                result.append("@interface");
                break;
            case INTERFACE:
                result.append("interface");
                break;
            case CLASS:
                result.append("class");
                break;
            case ENUM:
                result.append("enum");
                break;
            default:
                LOG.warning("Don't know what to call type element " + t);
                result.append("???");
        }
        result.append(" ").append(ShortTypePrinter.print(t.asType()));
        var superType = ShortTypePrinter.print(t.getSuperclass());
        switch (superType) {
            case "Object":
            case "none":
                break;
            default:
                result.append(" extends ").append(superType);
        }
        return result.toString();
    }

    private String hoverCode(Element e) {
        if (e instanceof ExecutableElement) {
            var m = (ExecutableElement) e;
            return ShortTypePrinter.printMethod(m);
        } else if (e instanceof VariableElement) {
            var v = (VariableElement) e;
            return ShortTypePrinter.print(v.asType()) + " " + v;
        } else if (e instanceof TypeElement) {
            var t = (TypeElement) e;
            var lines = new StringJoiner("\n");
            lines.add(hoverTypeDeclaration(t) + " {");
            for (var member : t.getEnclosedElements()) {
                // TODO check accessibility
                if (member instanceof ExecutableElement || member instanceof VariableElement) {
                    lines.add("  " + hoverCode(member) + ";");
                } else if (member instanceof TypeElement) {
                    lines.add("  " + hoverTypeDeclaration((TypeElement) member) + " { /* removed */ }");
                }
            }
            lines.add("}");
            return lines.toString();
        } else {
            return e.toString();
        }
    }

    private Optional<String> hoverDocs(Element e) {
        var ptr = new Ptr(e);
        var file = compiler().docs().find(ptr);
        if (!file.isPresent()) return Optional.empty();
        var parse = Parser.parseJavaFileObject(file.get());
        var path = parse.fuzzyFind(ptr);
        if (!path.isPresent()) return Optional.empty();
        var doc = parse.doc(path.get());
        var md = Parser.asMarkdown(doc);
        return Optional.of(md);
    }

    @Override
    public Optional<Hover> hover(TextDocumentPositionParams position) {
        var uri = position.textDocument.uri;
        var line = position.position.line + 1;
        var column = position.position.character + 1;
        if (!FileStore.isJavaFile(uri)) return Optional.empty();
        // Log start time
        LOG.info(String.format("Hover over %s(%d,%d) ...", uri.getPath(), line, column));
        var started = Instant.now();
        // Compile entire file
        try (var compile = compiler().compileFile(uri)) {
            // Find element under cursor
            var el = compile.element(uri, line, column);
            if (!el.isPresent()) {
                LOG.info("...no element under cursor");
                return Optional.empty();
            }
            // Result is combination of docs and code
            var result = new ArrayList<MarkedString>();
            // Add docs hover message
            var docs = hoverDocs(el.get());
            docs.filter(Predicate.not(String::isBlank))
                    .ifPresent(
                            doc -> {
                                result.add(new MarkedString(doc));
                            });

            // Add code hover message
            var code = hoverCode(el.get());
            result.add(new MarkedString("java.hover", code));
            // Log duration
            var elapsed = Duration.between(started, Instant.now());
            LOG.info(String.format("...found hover in %d ms", elapsed.toMillis()));

            return Optional.of(new Hover(result));
        }
    }

    @Override
    public Optional<SignatureHelp> signatureHelp(TextDocumentPositionParams position) {
        var uri = position.textDocument.uri;
        if (!FileStore.isJavaFile(uri)) return Optional.empty();
        var line = position.position.line + 1;
        var column = position.position.character + 1;
        try (var focus = compiler().compileFocus(uri, line, column)) {
            return focus.methodInvocation(uri, line, column);
        }
    }

    @Override
    public Optional<List<Location>> gotoDefinition(TextDocumentPositionParams position) {
        var fromUri = position.textDocument.uri;
        if (!FileStore.isJavaFile(fromUri)) return Optional.empty();
        var fromLine = position.position.line + 1;
        var fromColumn = position.position.character + 1;

        // Compile from-file and identify element under cursor
        LOG.info(String.format("Go-to-def at %s:%d...", fromUri, fromLine));
        Optional<Element> toEl;
        try (var compile = compiler().compileFile(fromUri)) {
            toEl = compile.element(fromUri, fromLine, fromColumn);
            if (!toEl.isPresent()) {
                LOG.info(String.format("...no element at cursor"));
                return Optional.empty();
            }
        }

        // Compile all files that *might* contain definitions of fromEl
        var toFiles = Parser.potentialDefinitions(toEl.get());
        toFiles.add(fromUri);
        var eraseCode = pruneWord(toFiles, toEl.get());
        try (var batch = compiler().compileBatch(eraseCode)) {
            // Find fromEl again, so that we have an Element from the current batch
            var fromElAgain = batch.element(fromUri, fromLine, fromColumn).get();

            // Find all definitions of fromElAgain
            var toTreePaths = batch.definitions(fromElAgain);
            if (!toTreePaths.isPresent()) return Optional.empty();
            var result = new ArrayList<Location>();
            for (var path : toTreePaths.get()) {
                var toUri = path.getCompilationUnit().getSourceFile().toUri();
                var toRange = batch.range(path);
                if (!toRange.isPresent()) {
                    LOG.warning(String.format("Couldn't locate `%s`", path.getLeaf()));
                    continue;
                }
                var from = new Location(toUri, toRange.get());
                result.add(from);
            }
            return Optional.of(result);
        }
    }

    @Override
    public Optional<List<Location>> findReferences(ReferenceParams position) {
        var toUri = position.textDocument.uri;
        if (!FileStore.isJavaFile(toUri)) return Optional.empty();
        var toLine = position.position.line + 1;
        var toColumn = position.position.character + 1;

        // Compile from-file and identify element under cursor
        LOG.warning(String.format("Looking for references to %s(%d,%d)...", toUri.getPath(), toLine, toColumn));
        Optional<Element> toEl;
        try (var compile = compiler().compileFile(toUri)) {
            toEl = compile.element(toUri, toLine, toColumn);
            if (!toEl.isPresent()) {
                LOG.warning("...no element under cursor");
                return Optional.empty();
            }
        }

        // Compile all files that *might* contain references to toEl
        var fromUris = Parser.potentialReferences(toEl.get());
        fromUris.add(toUri);
        var eraseCode = pruneWord(fromUris, toEl.get());
        try (var batch = compiler().compileBatch(eraseCode)) {
            var fromTreePaths = batch.references(toUri, toLine, toColumn);
            LOG.info(String.format("...found %d references", fromTreePaths.map(List::size).orElse(0)));
            if (!fromTreePaths.isPresent()) return Optional.empty();
            var result = new ArrayList<Location>();
            for (var path : fromTreePaths.get()) {
                var fromUri = path.getCompilationUnit().getSourceFile().toUri();
                var fromRange = batch.range(path);
                if (!fromRange.isPresent()) {
                    LOG.warning(String.format("...couldn't locate `%s`", path.getLeaf()));
                    continue;
                }
                var from = new Location(fromUri, fromRange.get());
                result.add(from);
            }
            return Optional.of(result);
        }
    }

    private List<JavaFileObject> pruneWord(Collection<URI> files, Element el) {
        var name = el.getSimpleName().toString();
        if (name.equals("<init>")) {
            name = el.getEnclosingElement().getSimpleName().toString();
        }
        LOG.info(String.format("...prune code that doesn't contain `%s`", name));
        var sources = new ArrayList<JavaFileObject>();
        for (var f : files) {
            var pruned = Parser.parseFile(f).prune(name);
            sources.add(new SourceFileObject(f, pruned, Instant.EPOCH));
        }
        return sources;
    }

    private Parser cacheParse;
    private URI cacheParseFile = URI.create("file:///NONE");;
    private int cacheParseVersion = -1;

    private void updateCachedParse(URI file) {
        if (file.equals(cacheParseFile) && FileStore.version(file) == cacheParseVersion) return;
        LOG.info(String.format("Updating cached parse file to %s", file));
        cacheParse = Parser.parseFile(file);
        cacheParseFile = file;
        cacheParseVersion = FileStore.version(file);
    }

    @Override
    public List<SymbolInformation> documentSymbol(DocumentSymbolParams params) {
        var uri = params.textDocument.uri;
        if (!FileStore.isJavaFile(uri)) return List.of();
        updateCachedParse(uri);
        var infos = cacheParse.documentSymbols();
        return infos;
    }

    @Override
    public List<CodeLens> codeLens(CodeLensParams params) {
        // TODO just create a blank code lens on every method, then resolve it async
        var uri = params.textDocument.uri;
        if (!FileStore.isJavaFile(uri)) return List.of();
        updateCachedParse(uri);
        var declarations = cacheParse.declarations();
        var result = new ArrayList<CodeLens>();
        for (var d : declarations) {
            var range = cacheParse.range(d);
            if (!range.isPresent()) continue;
            var className = Parser.className(d);
            var memberName = Parser.memberName(d);
            // If test class or method, add "Run Test" code lens
            if (cacheParse.isTestClass(d)) {
                var arguments = new JsonArray();
                arguments.add(uri.toString());
                arguments.add(className);
                arguments.add(JsonNull.INSTANCE);
                var command = new Command("Run All Tests", "java.command.test.run", arguments);
                var lens = new CodeLens(range.get(), command, null);
                result.add(lens);
                // TODO run all tests in file
                // TODO run all tests in package
            }
            if (cacheParse.isTestMethod(d)) {
                var arguments = new JsonArray();
                arguments.add(uri.toString());
                arguments.add(className);
                if (memberName.isPresent()) arguments.add(memberName.get());
                else arguments.add(JsonNull.INSTANCE);
                // 'Run Test' code lens
                var command = new Command("Run Test", "java.command.test.run", arguments);
                var lens = new CodeLens(range.get(), command, null);
                result.add(lens);
                // 'Debug Test' code lens
                // TODO this could be a CPU hot spot
                var sourceRoots = new JsonArray();
                for (var path : FileStore.sourceRoots()) {
                    sourceRoots.add(path.toString());
                }
                arguments.add(sourceRoots);
                command = new Command("Debug Test", "java.command.test.debug", arguments);
                lens = new CodeLens(range.get(), command, null);
                result.add(lens);
            }
            if (!cacheParse.isTestMethod(d)
                    && !cacheParse.isTestClass(d)
                    && !cacheParse.isCalledByTestFramework(d)
                    && !cacheParse.isOverride(d)
                    && !cacheParse.isMainMethod(d)) {
                // Unresolved "_ references" code lens
                var start = range.get().start;
                var line = start.line;
                var character = start.character;
                var data = new JsonArray();
                data.add("java.command.findReferences");
                data.add(uri.toString());
                data.add(line);
                data.add(character);
                var lens = new CodeLens(range.get(), null, data);
                result.add(lens);
            }
        }
        return result;
    }

    @Override
    public CodeLens resolveCodeLens(CodeLens unresolved) {
        // Unpack data
        var data = unresolved.data;
        var command = data.get(0).getAsString();
        assert command.equals("java.command.findReferences");
        var uriString = data.get(1).getAsString();
        var uri = URI.create(uriString);
        var line = data.get(2).getAsInt() + 1;
        var character = data.get(3).getAsInt() + 1;
        // Update command
        var count = countReferences(uri, line, character);
        String title;
        if (count == -1) title = "? references";
        else if (count == 1) title = "1 reference";
        else if (count == TOO_EXPENSIVE) title = "Find references";
        else title = String.format("%d references", count);
        var arguments = new JsonArray();
        arguments.add(uri.toString());
        arguments.add(line - 1);
        arguments.add(character - 1);
        unresolved.command = new Command(title, command, arguments);

        return unresolved;
    }

    private Cache<Ptr, Integer> cacheCountReferences = new Cache<>();
    private static final int TOO_EXPENSIVE = 100;

    private int countReferences(URI toUri, int toLine, int toColumn) {
        // Count within-file references
        Optional<Element> toEl;
        Ptr toPtr;
        int count;
        try (var compile = compiler().compileFile(toUri)) {
            // Find the element we want to count references to
            toEl = compile.element(toUri, toLine, toColumn);
            if (!toEl.isPresent()) {
                LOG.warning("No element at code lens!");
                return -1;
            }
            toPtr = new Ptr(toEl.get());
            count = compile.references(toUri, toLine, toColumn).map(List::size).orElse(0);
        }
        var crossFile = countCrossFileReferences(toUri, toLine, toColumn, toEl.get(), toPtr);
        if (crossFile == TOO_EXPENSIVE) return TOO_EXPENSIVE;
        return count + crossFile;
    }

    private int countCrossFileReferences(URI toUri, int toLine, int toColumn, Element toEl, Ptr toPtr) {
        // Identify all files that *might* contain references to toEl
        LOG.info(String.format("Count cross-file references to `%s`...", toPtr));
        var fromUris = Parser.potentialReferences(toEl);
        fromUris.remove(toUri);
        // If it's too expensive to compute the code lens
        if (fromUris.size() > 100) {
            LOG.info(
                    String.format(
                            "...there are %d potential references, which is too expensive to compile",
                            fromUris.size()));
            return TOO_EXPENSIVE;
        }
        // Figure out what files need to be updated
        var todo = new HashSet<URI>();
        for (var fromUri : fromUris) {
            if (cacheCountReferences.needs(Paths.get(fromUri), toPtr)) {
                todo.add(fromUri);
            }
        }
        // Update the cache
        if (!todo.isEmpty()) {
            todo.add(toUri);
            LOG.info(String.format("...compile %d files", todo.size()));
            var eraseCode = pruneWord(todo, toEl);
            var countByFile = new HashMap<URI, Integer>();
            try (var batch = compiler().compileBatch(eraseCode)) {
                var fromPaths = batch.references(toUri, toLine, toColumn).orElse(List.of());
                for (var fromPath : fromPaths) {
                    var fromUri = fromPath.getCompilationUnit().getSourceFile().toUri();
                    var newCount = countByFile.getOrDefault(fromUri, 0) + 1;
                    countByFile.put(fromUri, newCount);
                }
            }
            for (var fromUri : todo) {
                var count = countByFile.getOrDefault(fromUri, 0);
                // TODO consider not caching if fromUri contains errors
                cacheCountReferences.load(Paths.get(fromUri), toPtr, count);
            }
            LOG.info(String.format("...found cross-file references in %d files", countByFile.size()));
        }
        // Sum up the count
        var count = 0;
        for (var fromUri : fromUris) {
            count += cacheCountReferences.get(Paths.get(fromUri), toPtr);
        }
        return count;
    }

    @Override
    public List<TextEdit> formatting(DocumentFormattingParams params) {
        try (var compile = compiler().compileFile(params.textDocument.uri)) {
            var edits = new ArrayList<TextEdit>();
            edits.addAll(fixImports(compile, params.textDocument.uri));
            edits.addAll(addOverrides(compile, params.textDocument.uri));
            // TODO replace var with type name when vars are copy-pasted into fields
            // TODO replace ThisClass.staticMethod() with staticMethod() when ThisClass is useless
            return edits;
        }
    }

    private List<TextEdit> fixImports(CompileBatch compile, URI file) {
        // TODO if imports already match fixed-imports, return empty list
        // TODO preserve comments and other details of existing imports
        var imports = compile.fixImports(file);
        var pos = compile.sourcePositions();
        var lines = compile.lineMap(file);
        var edits = new ArrayList<TextEdit>();
        // Delete all existing imports
        for (var i : compile.imports(file)) {
            if (!i.isStatic()) {
                var offset = pos.getStartPosition(compile.root(file), i);
                var line = (int) lines.getLineNumber(offset) - 1;
                var delete = new TextEdit(new Range(new Position(line, 0), new Position(line + 1, 0)), "");
                edits.add(delete);
            }
        }
        if (imports.isEmpty()) return edits;
        // Find a place to insert the new imports
        long insertLine = -1;
        var insertText = new StringBuilder();
        // If there are imports, use the start of the first import as the insert position
        for (var i : compile.imports(file)) {
            if (!i.isStatic() && insertLine == -1) {
                long offset = pos.getStartPosition(compile.root(file), i);
                insertLine = lines.getLineNumber(offset) - 1;
            }
        }
        // If there are no imports, insert after the package declaration
        if (insertLine == -1 && compile.root(file).getPackageName() != null) {
            long offset = pos.getEndPosition(compile.root(file), compile.root(file).getPackageName());
            insertLine = lines.getLineNumber(offset);
            insertText.append("\n");
        }
        // If there are no imports and no package, insert at the top of the file
        if (insertLine == -1) {
            insertLine = 0;
        }
        // Insert each import
        for (var i : imports) {
            insertText.append("import ").append(i).append(";\n");
        }
        var insertPosition = new Position((int) insertLine, 0);
        var insert = new TextEdit(new Range(insertPosition, insertPosition), insertText.toString());
        edits.add(insert);

        return edits;
    }

    private List<TextEdit> addOverrides(CompileBatch compile, URI file) {
        var edits = new ArrayList<TextEdit>();
        var methods = compile.needsOverrideAnnotation(file);
        var pos = compile.sourcePositions();
        var lines = compile.lineMap(file);
        for (var t : methods) {
            var methodStart = pos.getStartPosition(t.getCompilationUnit(), t.getLeaf());
            var insertLine = lines.getLineNumber(methodStart);
            var indent = methodStart - lines.getPosition(insertLine, 0);
            var insertText = new StringBuilder();
            for (var i = 0; i < indent; i++) insertText.append(' ');
            insertText.append("@Override");
            insertText.append('\n');
            var insertPosition = new Position((int) insertLine - 1, 0);
            var insert = new TextEdit(new Range(insertPosition, insertPosition), insertText.toString());
            edits.add(insert);
        }
        return edits;
    }

    @Override
    public List<FoldingRange> foldingRange(FoldingRangeParams params) {
        updateCachedParse(params.textDocument.uri);
        return cacheParse.foldingRanges();
    }

    @Override
    public Optional<RenameResponse> prepareRename(TextDocumentPositionParams params) {
        throw new RuntimeException("TODO");
    }

    @Override
    public WorkspaceEdit rename(RenameParams params) {
        throw new RuntimeException("TODO");
    }

    @Override
    public void didOpenTextDocument(DidOpenTextDocumentParams params) {
        FileStore.open(params);
        if (FileStore.isJavaFile(params.textDocument.uri)) {
            // So that subsequent documentSymbol and codeLens requests will be faster
            updateCachedParse(params.textDocument.uri);
            uncheckedChanges = true;
        }
    }

    @Override
    public void didChangeTextDocument(DidChangeTextDocumentParams params) {
        FileStore.change(params);
        uncheckedChanges = true;
    }

    @Override
    public void didCloseTextDocument(DidCloseTextDocumentParams params) {
        FileStore.close(params);

        if (FileStore.isJavaFile(params.textDocument.uri)) {
            // Clear diagnostics
            client.publishDiagnostics(new PublishDiagnosticsParams(params.textDocument.uri, List.of()));
        }
    }

    @Override
    public void didSaveTextDocument(DidSaveTextDocumentParams params) {
        if (FileStore.isJavaFile(params.textDocument.uri)) {
            // Re-lint all active documents
            lint(FileStore.activeDocuments());
        }
    }

    private boolean uncheckedChanges = false;

    @Override
    public void doAsyncWork() {
        if (uncheckedChanges) {
            // Re-lint all active documents
            lint(FileStore.activeDocuments());
        }
    }

    private static final Logger LOG = Logger.getLogger("main");
}
