package org.javacs;

import com.google.gson.JsonPrimitive;
import com.sun.source.doctree.DocTree;
import com.sun.source.doctree.ParamTree;
import com.sun.source.tree.*;
import com.sun.source.util.*;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.lang.model.element.*;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.*;
import javax.tools.*;
import org.javacs.lsp.*;

class CompileBatch implements AutoCloseable {
    static final int MAX_COMPLETION_ITEMS = 50;

    private final JavaCompilerService parent;
    private final ReusableCompiler.Borrow borrow;
    private final Trees trees;
    private final Elements elements;
    private final Types types;
    private final List<CompilationUnitTree> roots;

    CompileBatch(JavaCompilerService parent, Collection<? extends JavaFileObject> files) {
        this.parent = parent;
        this.borrow = batchTask(parent, files);
        this.trees = Trees.instance(borrow.task);
        this.elements = borrow.task.getElements();
        this.types = borrow.task.getTypes();
        this.roots = new ArrayList<CompilationUnitTree>();
        // Compile all roots
        try {
            for (var t : borrow.task.parse()) roots.add(t);
            // The results of borrow.task.analyze() are unreliable when errors are present
            // You can get at `Element` values using `Trees`
            borrow.task.analyze();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close() {
        borrow.close();
    }

    private static ReusableCompiler.Borrow batchTask(
            JavaCompilerService parent, Collection<? extends JavaFileObject> sources) {
        parent.diags.clear();

        return parent.compiler.getTask(
                null,
                parent.fileManager,
                parent.diags::add,
                JavaCompilerService.options(parent.classPath, parent.addExports),
                List.of(),
                sources);
    }

    CompilationUnitTree root(URI uri) {
        for (var root : roots) {
            if (root.getSourceFile().toUri().equals(uri)) {
                return root;
            }
        }
        // Somehow, uri was not in batch
        var names = new StringJoiner(", ");
        for (var r : roots) {
            names.add(StringSearch.fileName(r.getSourceFile().toUri()));
        }
        throw new RuntimeException("File " + uri + " isn't in batch " + names);
    }

    private String contents(CompilationUnitTree root) {
        try {
            return root.getSourceFile().getCharContent(true).toString();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    Optional<Element> element(URI uri, int line, int character) {
        var path = findPath(uri, line, character);
        var el = trees.getElement(path);
        return Optional.ofNullable(el);
    }

    private boolean okUnused(Name name) {
        return name.charAt(0) == '_'; // TODO quick fix
    }

    Collection<PublishDiagnosticsParams> reportErrors() {
        // Construct empty lists
        var byUri = new HashMap<URI, PublishDiagnosticsParams>();
        for (var r : roots) {
            var params = new PublishDiagnosticsParams();
            params.uri = r.getSourceFile().toUri();
            byUri.put(params.uri, params);
        }
        // Convert diags
        for (var d : parent.diags) {
            var source = d.getSource();
            if (source == null) continue;
            var uri = source.toUri();
            if (!byUri.containsKey(uri)) continue;
            var convert = asDiagnostic(d);
            byUri.get(uri).diagnostics.add(convert);
        }
        // Check for unused privates
        for (var r : roots) {
            var uri = r.getSourceFile().toUri();
            var warnUnused = new WarnUnused(borrow.task);
            warnUnused.scan(r, null);
            for (var unusedEl : warnUnused.notUsed()) {
                if (okUnused(unusedEl.getSimpleName())) continue;
                var warn = warnUnused(unusedEl);
                byUri.get(uri).diagnostics.add(warn);
            }
        }
        // TODO hint fields that could be final
        // TODO hint unused exception

        return byUri.values();
    }

    private static int severity(javax.tools.Diagnostic.Kind kind) {
        switch (kind) {
            case ERROR:
                return DiagnosticSeverity.Error;
            case WARNING:
            case MANDATORY_WARNING:
                return DiagnosticSeverity.Warning;
            case NOTE:
                return DiagnosticSeverity.Information;
            case OTHER:
            default:
                return DiagnosticSeverity.Hint;
        }
    }

    private static Position position(String content, long offset) {
        int line = 0, column = 0;
        for (int i = 0; i < offset; i++) {
            if (content.charAt(i) == '\n') {
                line++;
                column = 0;
            } else column++;
        }
        return new Position(line, column);
    }

    private org.javacs.lsp.Diagnostic warnUnused(Element unusedEl) {
        var path = trees.getPath(unusedEl);
        var root = path.getCompilationUnit();
        var leaf = path.getLeaf();
        var pos = trees.getSourcePositions();
        var start = pos.getStartPosition(root, leaf);
        var end = pos.getEndPosition(root, leaf);
        var uri = root.getSourceFile().toUri();
        var contents = FileStore.contents(uri);
        if (leaf instanceof VariableTree) {
            var v = (VariableTree) leaf;
            var name = v.getName().toString();
            var offset = pos.getEndPosition(root, v.getType());
            if (offset == -1) offset = start;
            offset = contents.indexOf(name, (int) offset);
            end = offset + name.length();
        }
        var d = new org.javacs.lsp.Diagnostic();
        d.range = new Range(position(contents, start), position(contents, end));
        d.message = String.format("`%s` is not used", unusedEl.getSimpleName());
        d.code = "unused";
        if (unusedEl instanceof ExecutableElement || unusedEl instanceof TypeElement) {
            d.severity = DiagnosticSeverity.Hint;
        } else {
            d.severity = DiagnosticSeverity.Information;
        }
        d.tags = List.of(DiagnosticTag.Unnecessary);
        return d;
    }

    private org.javacs.lsp.Diagnostic asDiagnostic(javax.tools.Diagnostic<? extends JavaFileObject> java) {
        // Check that error is in an open file
        var uri = java.getSource().toUri();
        // Find start and end position
        var content = FileStore.contents(uri);
        var start = position(content, java.getStartPosition());
        var end = position(content, java.getEndPosition());
        var d = new org.javacs.lsp.Diagnostic();
        d.severity = severity(java.getKind());
        d.range = new Range(start, end);
        d.code = java.getCode();
        d.message = java.getMessage(null);
        return d;
    }

    Optional<List<TreePath>> definitions(Element el) {
        LOG.info(String.format("Search for definitions of `%s` in %d files...", el, roots.size()));

        if (el.asType().getKind() == TypeKind.ERROR) {
            LOG.info(String.format("...`%s` is an error type, giving up", el.asType()));
            return Optional.empty();
        }

        var refs = new ArrayList<TreePath>();
        class FindDefinitions extends TreePathScanner<Void, Void> {
            boolean sameSymbol(Element found) {
                return el.equals(found);
            }

            boolean isSubMethod(Element found) {
                if (!(el instanceof ExecutableElement)) return false;
                if (!(found instanceof ExecutableElement)) return false;
                var superMethod = (ExecutableElement) el;
                var subMethod = (ExecutableElement) found;
                var subType = (TypeElement) subMethod.getEnclosingElement();
                // TODO need to check if class is compatible as well
                if (elements.overrides(subMethod, superMethod, subType)) {
                    // LOG.info(String.format("...`%s.%s` overrides `%s`", subType, subMethod, superMethod));
                    return true;
                }
                return false;
            }

            void check(TreePath from) {
                var found = trees.getElement(from);
                var match = sameSymbol(found) || isSubMethod(found);
                if (match) refs.add(from);
            }

            @Override
            public Void visitClass(ClassTree t, Void __) {
                check(getCurrentPath());
                return super.visitClass(t, null);
            }

            @Override
            public Void visitMethod(MethodTree t, Void __) {
                check(getCurrentPath());
                return super.visitMethod(t, null);
            }

            @Override
            public Void visitVariable(VariableTree t, Void __) {
                check(getCurrentPath());
                return super.visitVariable(t, null);
            }

            @Override
            public Void visitTypeParameter(TypeParameterTree t, Void __) {
                check(getCurrentPath());
                return super.visitTypeParameter(t, null);
            }
        }
        var finder = new FindDefinitions();
        for (var r : roots) {
            finder.scan(r, null);
        }
        return Optional.of(refs);
    }

    Optional<List<TreePath>> references(URI toUri, int toLine, int toColumn) {
        var to = element(toUri, toLine, toColumn);
        if (to.isEmpty()) {
            LOG.info(String.format("...no element at %s(%d, %d), giving up", toUri.getPath(), toLine, toColumn));
            return Optional.empty();
        }
        // If to is an error, we won't be able to find anything
        if (to.get().asType().getKind() == TypeKind.ERROR) {
            LOG.info(String.format("...`%s` is an error type, giving up", to.get().asType()));
            return Optional.empty();
        }
        // Otherwise, scan roots for references
        List<TreePath> list = new ArrayList<TreePath>();
        var map = Map.of(to.get(), list);
        var finder = new FindReferences(borrow.task);
        for (var r : roots) {
            // TODO jump to scan takes me to a specific method in this file, which is misleading. The actual
            // implementation is in the super of FindReferences.
            finder.scan(r, map);
        }
        return Optional.of(list);
    }

    /**
     * Find all elements in `file` that get turned into code-lenses. This needs to match the result of
     * `Parser#declarations`
     */
    List<Element> declarations(URI file) {
        for (var r : roots) {
            if (!r.getSourceFile().toUri().equals(file)) continue;
            var paths = Parser.declarations(r);
            var els = new ArrayList<Element>();
            for (var p : paths) {
                var e = trees.getElement(p);
                assert e != null;
                els.add(e);
            }
            return els;
        }

        // Couldn't find file! Throw an error.
        var message = new StringJoiner(", ");
        for (var r : roots) {
            message.add(StringSearch.fileName(r.getSourceFile().toUri()));
        }
        throw new RuntimeException(file + " is not in " + message);
    }

    Optional<Range> range(TreePath path) {
        var uri = path.getCompilationUnit().getSourceFile().toUri();
        var contents = FileStore.contents(uri);
        return Parser.range(borrow.task, contents, path);
    }

    SourcePositions sourcePositions() {
        return trees.getSourcePositions();
    }

    LineMap lineMap(URI uri) {
        return root(uri).getLineMap();
    }

    List<? extends ImportTree> imports(URI uri) {
        return root(uri).getImports();
    }

    private List<Element> overrides(ExecutableElement method) {
        var elements = borrow.task.getElements();
        var types = borrow.task.getTypes();
        var results = new ArrayList<Element>();
        var enclosingClass = (TypeElement) method.getEnclosingElement();
        var enclosingType = enclosingClass.asType();
        for (var superClass : types.directSupertypes(enclosingType)) {
            var e = (TypeElement) types.asElement(superClass);
            for (var other : e.getEnclosedElements()) {
                if (!(other instanceof ExecutableElement)) continue;
                if (elements.overrides(method, (ExecutableElement) other, enclosingClass)) {
                    results.add(other);
                }
            }
        }
        return results;
    }

    private boolean hasOverrideAnnotation(ExecutableElement method) {
        for (var ann : method.getAnnotationMirrors()) {
            var type = ann.getAnnotationType();
            var el = type.asElement();
            var name = el.toString();
            if (name.equals("java.lang.Override")) {
                return true;
            }
        }
        return false;
    }

    /** Find methods that override a method from a superclass but don't have an @Override annotation. */
    List<TreePath> needsOverrideAnnotation(URI uri) {
        LOG.info(String.format("Looking for methods that need an @Override annotation in %s ...", uri.getPath()));

        var root = root(uri);
        var results = new ArrayList<TreePath>();
        class FindMissingOverride extends TreePathScanner<Void, Void> {
            @Override
            public Void visitMethod(MethodTree t, Void __) {
                var method = (ExecutableElement) trees.getElement(getCurrentPath());
                var supers = overrides(method);
                if (!supers.isEmpty() && !hasOverrideAnnotation(method)) {
                    var overridesMethod = supers.get(0);
                    var overridesClass = overridesMethod.getEnclosingElement();
                    LOG.info(
                            String.format(
                                    "...`%s` has no @Override annotation but overrides `%s.%s`",
                                    method, overridesClass, overridesMethod));
                    results.add(getCurrentPath());
                }
                return super.visitMethod(t, null);
            }
        }
        new FindMissingOverride().scan(root, null);
        return results;
    }

    /**
     * Figure out what imports this file should have. Star-imports like `import java.util.*` are converted to individual
     * class imports. Missing imports are inferred by looking at imports in other source files.
     */
    List<String> fixImports(URI uri) {
        var root = root(uri);
        var contents = contents(root);
        // Check diagnostics for missing imports
        var unresolved = new HashSet<String>();
        for (var d : parent.diags) {
            if (d.getCode().equals("compiler.err.cant.resolve.location") && d.getSource().toUri().equals(uri)) {
                long start = d.getStartPosition(), end = d.getEndPosition();
                var id = contents.substring((int) start, (int) end);
                if (id.matches("[A-Z]\\w+")) {
                    unresolved.add(id);
                } else LOG.warning(id + " doesn't look like a class");
            }
        }
        // Look at imports in other classes to help us guess how to fix imports
        // TODO cache parsed imports on a per-file basis
        var sourcePathImports = StringSearch.existingImports(FileStore.all());
        var classes = new HashSet<String>();
        classes.addAll(parent.jdkClasses);
        classes.addAll(parent.classPathClasses);
        var fixes = StringSearch.resolveSymbols(unresolved, sourcePathImports, classes);
        // Figure out which existing imports are actually used
        var trees = Trees.instance(borrow.task);
        var references = new HashSet<String>();
        class FindUsedImports extends TreePathScanner<Void, Void> {
            @Override
            public Void visitIdentifier(IdentifierTree node, Void nothing) {
                var e = trees.getElement(getCurrentPath());
                if (e instanceof TypeElement) {
                    var t = (TypeElement) e;
                    var qualifiedName = t.getQualifiedName().toString();
                    var lastDot = qualifiedName.lastIndexOf('.');
                    var packageName = lastDot == -1 ? "" : qualifiedName.substring(0, lastDot);
                    var thisPackage = Objects.toString(root.getPackageName(), "");
                    // java.lang.* and current package are imported by default
                    if (!packageName.equals("java.lang")
                            && !packageName.equals(thisPackage)
                            && !packageName.equals("")) {
                        references.add(qualifiedName);
                    }
                }
                return null;
            }
        }
        new FindUsedImports().scan(root, null);
        // If `uri` contains errors, don't try to fix imports, it's too inaccurate
        var hasErrors = hasErrors(uri);
        // Take the intersection of existing imports ^ existing identifiers
        var qualifiedNames = new HashSet<String>();
        for (var i : root.getImports()) {
            var imported = i.getQualifiedIdentifier().toString();
            if (imported.endsWith(".*")) {
                var packageName = StringSearch.mostName(imported);
                var isUsed = hasErrors || references.stream().anyMatch(r -> r.startsWith(packageName));
                if (isUsed) qualifiedNames.add(imported);
                else LOG.warning("There are no references to package " + imported);
            } else {
                if (hasErrors || references.contains(imported)) qualifiedNames.add(imported);
                else LOG.warning("There are no references to class " + imported);
            }
        }
        // Add qualified names from fixes
        qualifiedNames.addAll(fixes.values());
        // Sort in alphabetical order
        var sorted = new ArrayList<String>();
        sorted.addAll(qualifiedNames);
        Collections.sort(sorted);
        return sorted;
    }

    private boolean hasErrors(URI uri) {
        for (var d : parent.diags) {
            if (d.getKind() != javax.tools.Diagnostic.Kind.ERROR) continue;
            if (!d.getSource().toUri().equals(uri)) continue;
            if (d.getCode().equals("compiler.err.cant.resolve.location")) continue;
            return true;
        }
        return false;
    }

    /** Find all overloads for the smallest method call that includes the cursor */
    Optional<SignatureHelp> signatureHelp(URI file, int line, int character) {
        LOG.info(String.format("Find method invocation around %s(%d,%d)...", file, line, character));
        var cursor = findPath(file, line, character);
        for (var path = cursor; path != null; path = path.getParentPath()) {
            if (path.getLeaf() instanceof MethodInvocationTree) {
                // Find all overloads of method
                LOG.info(String.format("...`%s` is a method invocation", path.getLeaf()));
                var invoke = (MethodInvocationTree) path.getLeaf();
                var method = trees.getElement(trees.getPath(path.getCompilationUnit(), invoke.getMethodSelect()));
                var results = new ArrayList<ExecutableElement>();
                for (var m : method.getEnclosingElement().getEnclosedElements()) {
                    if (m.getKind() == ElementKind.METHOD && m.getSimpleName().equals(method.getSimpleName())) {
                        results.add((ExecutableElement) m);
                    }
                }
                // Figure out which parameter is active
                var activeParameter = invoke.getArguments().indexOf(cursor.getLeaf());
                LOG.info(String.format("...active parameter `%s` is %d", cursor.getLeaf(), activeParameter));
                // Figure out which method is active, if possible
                Optional<ExecutableElement> activeMethod =
                        method instanceof ExecutableElement
                                ? Optional.of((ExecutableElement) method)
                                : Optional.empty();
                return Optional.of(asSignatureHelp(activeMethod, activeParameter, results));
            } else if (path.getLeaf() instanceof NewClassTree) {
                // Find all overloads of method
                LOG.info(String.format("...`%s` is a constructor invocation", path.getLeaf()));
                var invoke = (NewClassTree) path.getLeaf();
                var method = trees.getElement(path);
                var results = new ArrayList<ExecutableElement>();
                for (var m : method.getEnclosingElement().getEnclosedElements()) {
                    if (m.getKind() == ElementKind.CONSTRUCTOR) {
                        results.add((ExecutableElement) m);
                    }
                }
                // Figure out which parameter is active
                var activeParameter = invoke.getArguments().indexOf(cursor.getLeaf());
                LOG.info(String.format("...active parameter `%s` is %d", cursor.getLeaf(), activeParameter));
                // Figure out which method is active, if possible
                Optional<ExecutableElement> activeMethod =
                        method instanceof ExecutableElement
                                ? Optional.of((ExecutableElement) method)
                                : Optional.empty();
                return Optional.of(asSignatureHelp(activeMethod, activeParameter, results));
            }
        }
        return Optional.empty();
    }

    private SignatureHelp asSignatureHelp(
            Optional<ExecutableElement> activeMethod, int activeParameter, List<ExecutableElement> overloads) {
        // TODO use docs to get parameter names
        var sigs = new ArrayList<SignatureInformation>();
        for (var e : overloads) {
            sigs.add(asSignatureInformation(e));
        }
        var activeSig = activeMethod.map(overloads::indexOf).orElse(0);
        return new SignatureHelp(sigs, activeSig, activeParameter);
    }

    private SignatureInformation asSignatureInformation(ExecutableElement e) {
        var i = new SignatureInformation();
        // Get docs from source if possible, ExecutableElement if not
        if (!addSignatureDocs(e, i)) {
            i.parameters = signatureParamsFromMethod(e);
        }
        // Compute label from params (which came from either source or ExecutableElement)
        var name = e.getSimpleName();
        if (name.contentEquals("<init>")) name = e.getEnclosingElement().getSimpleName();
        var args = new StringJoiner(", ");
        for (var p : i.parameters) {
            args.add(p.label);
        }
        i.label = name + "(" + args + ")";

        return i;
    }

    private boolean addSignatureDocs(ExecutableElement e, SignatureInformation sig) {
        // Find the file that contains e
        var ptr = new Ptr(e);
        var file = parent.docs().find(ptr);
        if (!file.isPresent()) return false;
        var parse = Parser.parseJavaFileObject(file.get());
        // Find the tree
        var path = parse.fuzzyFind(ptr);
        if (!path.isPresent()) return false;
        if (!(path.get().getLeaf() instanceof MethodTree)) return false;
        var method = (MethodTree) path.get().getLeaf();
        // Find the docstring on method, or empty doc if there is none
        var doc = parse.doc(path.get());
        sig.documentation = Parser.asMarkupContent(doc);
        // Get param docs from @param tags
        var paramComments = new HashMap<String, String>();
        for (var tag : doc.getBlockTags()) {
            if (tag.getKind() == DocTree.Kind.PARAM) {
                var param = (ParamTree) tag;
                paramComments.put(param.getName().toString(), Parser.asMarkdown(param.getDescription()));
            }
        }
        // Get param names from source
        sig.parameters = new ArrayList<ParameterInformation>();
        for (var i = 0; i < e.getParameters().size(); i++) {
            var fromSource = method.getParameters().get(i);
            var fromType = e.getParameters().get(i);
            var info = new ParameterInformation();
            var name = fromSource.getName().toString();
            var type = ShortTypePrinter.print(fromType.asType());
            info.label = type + " " + name;
            if (paramComments.containsKey(name)) {
                var markdown = paramComments.get(name);
                info.documentation = new MarkupContent("markdown", markdown);
            }
            sig.parameters.add(info);
        }
        return true;
    }

    private List<ParameterInformation> signatureParamsFromMethod(ExecutableElement e) {
        var missingParamNames = ShortTypePrinter.missingParamNames(e);
        var ps = new ArrayList<ParameterInformation>();
        for (var v : e.getParameters()) {
            var p = new ParameterInformation();
            if (missingParamNames) p.label = ShortTypePrinter.print(v.asType());
            else p.label = v.getSimpleName().toString();
            ps.add(p);
        }
        return ps;
    }

    List<CompletionItem> completeIdentifiers(
            URI uri, int line, int character, boolean insideClass, boolean insideMethod, String partialName) {
        LOG.info(String.format("Completing identifiers starting with `%s`...", partialName));

        var root = root(uri);
        var result = new ArrayList<CompletionItem>();

        // Add snippets
        if (!insideClass) {
            // If no package declaration is present, suggest package [inferred name];
            if (root.getPackage() == null) {
                var name = FileStore.suggestedPackageName(Paths.get(uri));
                result.add(snippetCompletion("package " + name, "package " + name + ";\n\n"));
            }
            // If no class declaration is present, suggest class [file name]
            var hasClassDeclaration = false;
            for (var t : root.getTypeDecls()) {
                if (!(t instanceof ErroneousTree)) {
                    hasClassDeclaration = true;
                }
            }
            if (!hasClassDeclaration) {
                var name = Paths.get(uri).getFileName().toString();
                name = name.substring(0, name.length() - ".java".length());
                result.add(snippetCompletion("class " + name, "class " + name + " {\n    $0\n}"));
            }
        }
        // Add identifiers
        completeScopeIdentifiers(uri, line, character, partialName, result);
        // Add keywords
        if (!insideClass) {
            addKeywords(TOP_LEVEL_KEYWORDS, partialName, result);
        } else if (!insideMethod) {
            addKeywords(CLASS_BODY_KEYWORDS, partialName, result);
        } else {
            addKeywords(METHOD_BODY_KEYWORDS, partialName, result);
        }

        return result;
    }

    List<CompletionItem> completeAnnotations(URI uri, int line, int character, String partialName) {
        var result = new ArrayList<CompletionItem>();
        // Add @Override ... snippet
        if ("Override".startsWith(partialName)) {
            // TODO filter out already-implemented methods using thisMethods
            var alreadyShown = new HashSet<String>();
            for (var method : superMethods(uri, line, character)) {
                var mods = method.getModifiers();
                if (mods.contains(Modifier.STATIC) || mods.contains(Modifier.PRIVATE)) continue;

                var label = "@Override " + ShortTypePrinter.printMethod(method);
                var snippet = "Override\n" + new TemplatePrinter().printMethod(method) + " {\n    $0\n}";
                var override = snippetCompletion(label, snippet);
                if (!alreadyShown.contains(label)) {
                    result.add(override);
                    alreadyShown.add(label);
                }
            }
        }
        // Add @Override, @Test, other simple class names
        // We use 0 as the column number because if we focus javac on the partial @Annotation it will crash
        completeScopeIdentifiers(uri, line, 0, partialName, result);
        return result;
    }

    /** Find all case options in the switch expression surrounding line:character */
    List<CompletionItem> completeCases(URI uri, int line, int character) {
        var cursor = findPath(uri, line, character);
        LOG.info(String.format("Complete enum constants following `%s`...", cursor.getLeaf()));

        // Find surrounding switch
        var path = cursor;
        while (!(path.getLeaf() instanceof SwitchTree)) path = path.getParentPath();
        var leaf = (SwitchTree) path.getLeaf();
        path = new TreePath(path, leaf.getExpression());
        LOG.info(String.format("...found switch expression `%s`", leaf.getExpression()));

        // Get members of switched type
        var type = trees.getTypeMirror(path);
        if (type == null) {
            LOG.info("...no type at " + leaf.getExpression());
            return List.of();
        }
        LOG.info(String.format("...switched expression has type `%s`", type));
        var types = borrow.task.getTypes();
        var definition = types.asElement(type);
        if (definition == null) {
            LOG.info("...type has no definition, completing identifiers instead");
            return completeIdentifiers(uri, line, character, true, true, ""); // TODO pass partial name
        }
        LOG.info(String.format("...switched expression has definition `%s`", definition));
        var result = new ArrayList<CompletionItem>();
        for (var member : definition.getEnclosedElements()) {
            if (member.getKind() == ElementKind.ENUM_CONSTANT) result.add(elementCompletion(member));
        }

        return result;
    }

    /** Find all members of expression ending at line:character */
    List<CompletionItem> completeMembers(URI uri, int line, int character) {
        var path = findPath(uri, line, character);
        var element = trees.getElement(path);
        var type = trees.getTypeMirror(path);

        if (element instanceof PackageElement) {
            return completePackageMembers(path);
        } else if (element instanceof TypeElement) {
            var members = completeTypeMembers(path);
            var result = groupByOverload(members, true);
            result.add(keywordCompletion("class"));
            result.add(keywordCompletion("this"));
            result.add(keywordCompletion("super"));
            return result;
        } else {
            var members = completeInstanceMembers(path);
            var result = groupByOverload(members, true);
            if (type instanceof ArrayType) {
                result.add(keywordCompletion("length"));
            }
            return result;
        }
    }

    List<CompletionItem> completeReferences(URI uri, int line, int character) {
        var path = findPath(uri, line, character);
        var scope = trees.getScope(path);
        var element = trees.getElement(path);
        if (element instanceof TypeElement) {
            var t = (TypeElement) element;
            LOG.info(String.format("...completing static methods of %s", t.getQualifiedName()));
            // Add members
            var methods = new ArrayList<Element>();
            for (var member : t.getEnclosedElements()) {
                if (member.getKind() == ElementKind.METHOD
                        && trees.isAccessible(scope, member, (DeclaredType) t.asType())) {
                    methods.add(member);
                }
            }
            // Form result
            var result = groupByOverload(methods, false);
            // Add ::new
            result.add(keywordCompletion("new"));

            return result;
        } else {
            var members = completeInstanceMembers(path);
            var result = groupByOverload(members, false);
            return result;
        }
    }

    private List<CompletionItem> completePackageMembers(TreePath path) {
        var result = new ArrayList<CompletionItem>();
        var scope = trees.getScope(path);
        var element = (PackageElement) trees.getElement(path);

        LOG.info(String.format("...completing members of package %s", element.getQualifiedName()));

        // Add class-names resolved as Element by javac
        for (var member : element.getEnclosedElements()) {
            // If the package member is a TypeElement, like a class or interface, check if it's accessible
            if (member instanceof TypeElement) {
                if (trees.isAccessible(scope, (TypeElement) member)) {
                    result.add(elementCompletion(member));
                }
            }
            // Otherwise, just assume it's accessible and add it to the list
            else result.add(elementCompletion(member));
        }
        // Add sub-package names resolved as String by guava ClassPath
        var parent = element.getQualifiedName().toString();
        var subs = subPackages(parent);
        for (var sub : subs) {
            result.add(packageCompletion(sub, StringSearch.lastName(sub)));
        }

        return result;
    }

    private List<Element> completeTypeMembers(TreePath path) {
        var result = new ArrayList<Element>();
        var scope = trees.getScope(path);
        var element = (TypeElement) trees.getElement(path);

        LOG.info(String.format("...completing static members of %s", element.getQualifiedName()));

        // Add static members
        for (var member : element.getEnclosedElements()) {
            if (member.getModifiers().contains(Modifier.STATIC)
                    && trees.isAccessible(scope, member, (DeclaredType) element.asType())) {
                result.add(member);
            }
        }

        return result;
    }

    private List<Element> completeInstanceMembers(TreePath path) {
        var scope = trees.getScope(path);
        var type = trees.getTypeMirror(path);
        if (type == null) {
            LOG.warning(String.format("`...%s` has no type", path.getLeaf()));
            return List.of();
        }
        if (!hasMembers(type)) {
            LOG.warning("...don't know how to complete members of type " + type);
            return List.of();
        }
        var result = new ArrayList<Element>();
        var ts = supersWithSelf(type);
        var alreadyAdded = new HashSet<String>();
        LOG.info(String.format("...completing virtual members of %s and %d supers", type, ts.size()));
        for (var t : ts) {
            var e = types.asElement(t);
            if (e == null) {
                LOG.warning(String.format("...can't convert supertype `%s` to element, skipping", t));
                continue;
            }
            for (var member : e.getEnclosedElements()) {
                // Don't add statics
                if (member.getModifiers().contains(Modifier.STATIC)) continue;
                // Don't add constructors
                if (member.getSimpleName().contentEquals("<init>")) continue;
                // Skip overridden members from superclass
                if (alreadyAdded.contains(member.toString())) continue;

                // If type is a DeclaredType, check accessibility of member
                if (type instanceof DeclaredType) {
                    if (trees.isAccessible(scope, member, (DeclaredType) type)) {
                        result.add(member);
                        alreadyAdded.add(member.toString());
                    }
                }
                // Otherwise, accessibility rules are very complicated
                // Give up and just declare that everything is accessible
                else {
                    result.add(member);
                    alreadyAdded.add(member.toString());
                }
            }
        }
        return result;
    }

    private List<CompletionItem> groupByOverload(List<Element> members, boolean isInvocation) {
        var result = new ArrayList<CompletionItem>();
        var methods = new HashMap<Name, List<ExecutableElement>>();
        for (var member : members) {
            if (member instanceof ExecutableElement) {
                var method = (ExecutableElement) member;
                var name = method.getSimpleName();
                if (!methods.containsKey(name)) {
                    methods.put(name, new ArrayList<ExecutableElement>());
                }
                methods.get(name).add(method);
            } else {
                result.add(elementCompletion(member));
            }
        }
        for (var overload : methods.values()) {
            var i = overloadCompletion(overload);
            if (isInvocation) {
                completeInvocation(overload, i);
            }
            result.add(i);
        }
        return result;
    }

    static String[] TOP_LEVEL_KEYWORDS = {
        "package",
        "import",
        "public",
        "private",
        "protected",
        "abstract",
        "class",
        "interface",
        "extends",
        "implements",
    };

    private static String[] CLASS_BODY_KEYWORDS = {
        "public",
        "private",
        "protected",
        "static",
        "final",
        "native",
        "synchronized",
        "abstract",
        "default",
        "class",
        "interface",
        "void",
        "boolean",
        "int",
        "long",
        "float",
        "double",
    };

    private static String[] METHOD_BODY_KEYWORDS = {
        "new",
        "assert",
        "try",
        "catch",
        "finally",
        "throw",
        "return",
        "break",
        "case",
        "continue",
        "default",
        "do",
        "while",
        "for",
        "switch",
        "if",
        "else",
        "instanceof",
        "var",
        "final",
        "class",
        "void",
        "boolean",
        "int",
        "long",
        "float",
        "double",
    };

    private List<ExecutableElement> virtualMethods(DeclaredType type) {
        var result = new ArrayList<ExecutableElement>();
        for (var member : type.asElement().getEnclosedElements()) {
            if (member instanceof ExecutableElement) {
                var method = (ExecutableElement) member;
                if (!method.getSimpleName().contentEquals("<init>")
                        && !method.getModifiers().contains(Modifier.STATIC)) {
                    result.add(method);
                }
            }
        }
        return result;
    }

    private TypeMirror enclosingClass(URI uri, int line, int character) {
        var cursor = findPath(uri, line, character);
        var path = cursor;
        while (!(path.getLeaf() instanceof ClassTree)) path = path.getParentPath();
        var enclosingClass = trees.getElement(path);

        return enclosingClass.asType();
    }

    private void collectSuperMethods(TypeMirror thisType, List<ExecutableElement> result) {
        var types = borrow.task.getTypes();

        for (var superType : types.directSupertypes(thisType)) {
            if (superType instanceof DeclaredType) {
                var type = (DeclaredType) superType;
                result.addAll(virtualMethods(type));
                collectSuperMethods(type, result);
            }
        }
    }

    private List<ExecutableElement> superMethods(URI uri, int line, int character) {
        var thisType = enclosingClass(uri, line, character);
        var result = new ArrayList<ExecutableElement>();

        collectSuperMethods(thisType, result);

        return result;
    }

    private boolean isImported(URI uri, String qualifiedName) {
        var root = root(uri);
        var packageName = StringSearch.mostName(qualifiedName);
        var className = StringSearch.lastName(qualifiedName);
        for (var i : root.getImports()) {
            var importName = i.getQualifiedIdentifier().toString();
            var importPackage = StringSearch.mostName(importName);
            var importClass = StringSearch.lastName(importName);
            if (importClass.equals("*") && importPackage.equals(packageName)) return true;
            if (importClass.equals(className) && importPackage.equals(packageName)) return true;
        }
        return false;
    }

    private Set<TypeMirror> supersWithSelf(TypeMirror t) {
        var types = new HashSet<TypeMirror>();
        collectSupers(t, types);
        // Object type is not included by default
        // We need to add it to get members like .equals(other) and .hashCode()
        types.add(borrow.task.getElements().getTypeElement("java.lang.Object").asType());
        return types;
    }

    private void collectSupers(TypeMirror t, Set<TypeMirror> supers) {
        supers.add(t);
        for (var s : types.directSupertypes(t)) {
            collectSupers(s, supers);
        }
    }

    private boolean hasMembers(TypeMirror type) {
        switch (type.getKind()) {
            case ARRAY:
            case DECLARED:
            case ERROR:
            case TYPEVAR:
            case WILDCARD:
            case UNION:
            case INTERSECTION:
                return true;
            case BOOLEAN:
            case BYTE:
            case SHORT:
            case INT:
            case LONG:
            case CHAR:
            case FLOAT:
            case DOUBLE:
            case VOID:
            case NONE:
            case NULL:
            case PACKAGE:
            case EXECUTABLE:
            case OTHER:
            default:
                return false;
        }
    }

    /** Find all identifiers in scope at line:character */
    List<Element> scopeMembers(URI uri, int line, int character, String partialName) {
        var path = findPath(uri, line, character);
        var types = borrow.task.getTypes();
        var start = trees.getScope(path);

        class Walk {
            List<Element> result = new ArrayList<>();

            boolean isStatic(Scope s) {
                var method = s.getEnclosingMethod();
                if (method != null) {
                    return method.getModifiers().contains(Modifier.STATIC);
                } else return false;
            }

            boolean isStatic(Element e) {
                return e.getModifiers().contains(Modifier.STATIC);
            }

            boolean isThisOrSuper(Element e) {
                var name = e.getSimpleName();
                return name.contentEquals("this") || name.contentEquals("super");
            }

            // Place each member of `this` or `super` directly into `results`
            void unwrapThisSuper(VariableElement ve) {
                var thisType = ve.asType();
                // `this` and `super` should always be instances of DeclaredType, which we'll use to check accessibility
                if (!(thisType instanceof DeclaredType)) {
                    LOG.warning(String.format("%s is not a DeclaredType", thisType));
                    return;
                }
                var thisDeclaredType = (DeclaredType) thisType;
                var thisElement = types.asElement(thisDeclaredType);
                for (var thisMember : thisElement.getEnclosedElements()) {
                    if (isStatic(start) && !isStatic(thisMember)) continue;
                    if (thisMember.getSimpleName().contentEquals("<init>")) continue;
                    if (!StringSearch.matchesPartialName(thisMember.getSimpleName(), partialName)) continue;

                    // Check if member is accessible from original scope
                    if (trees.isAccessible(start, thisMember, thisDeclaredType)) {
                        result.add(thisMember);
                    }
                }
            }

            // Place each member of `s` into results, and unwrap `this` and `super`
            void walkLocals(Scope s) {
                try {
                    for (var e : s.getLocalElements()) {
                        if (StringSearch.matchesPartialName(e.getSimpleName(), partialName)) {
                            if (e instanceof TypeElement) {
                                var te = (TypeElement) e;
                                if (trees.isAccessible(start, te)) result.add(te);
                            } else if (isThisOrSuper(e)) {
                                if (!isStatic(s)) result.add(e);
                            } else {
                                result.add(e);
                            }
                        }
                        if (isThisOrSuper(e)) {
                            unwrapThisSuper((VariableElement) e);
                        }
                        if (tooManyItems(result.size())) return;
                    }
                } catch (Exception e) {
                    LOG.log(Level.WARNING, "error walking locals in scope", e);
                }
            }

            // Walk each enclosing scope, placing its members into `results`
            List<Element> walkScopes() {
                var scopes = new ArrayList<Scope>();
                for (var s = start; s != null; s = s.getEnclosingScope()) {
                    scopes.add(s);
                }
                // Scopes may be contained in an enclosing scope.
                // The outermost scope contains those elements available via "star import" declarations;
                // the scope within that contains the top level elements of the compilation unit, including any named
                // imports.
                // https://parent.docs.oracle.com/en/java/javase/11/docs/api/jdk.compiler/com/sun/source/tree/Scope.html
                for (var i = 0; i < scopes.size() - 2; i++) {
                    var s = scopes.get(i);
                    walkLocals(s);
                    // Return early?
                    if (tooManyItems(result.size())) {
                        return result;
                    }
                }

                return result;
            }
        }
        return new Walk().walkScopes();
    }

    private boolean tooManyItems(int count) {
        var test = count >= MAX_COMPLETION_ITEMS;
        if (test) LOG.warning(String.format("...# of items %d reached max %s", count, MAX_COMPLETION_ITEMS));
        return test;
    }

    private Set<String> subPackages(String parentPackage) {
        var result = new HashSet<String>();
        Consumer<String> checkClassName =
                name -> {
                    var packageName = StringSearch.mostName(name);
                    if (packageName.startsWith(parentPackage) && packageName.length() > parentPackage.length()) {
                        var start = parentPackage.length() + 1;
                        var end = packageName.indexOf('.', start);
                        if (end == -1) end = packageName.length();
                        var prefix = packageName.substring(0, end);
                        result.add(prefix);
                    }
                };
        for (var name : parent.jdkClasses) checkClassName.accept(name);
        for (var name : parent.classPathClasses) checkClassName.accept(name);
        return result;
    }

    private void addKeywords(String[] keywords, String partialName, List<CompletionItem> result) {
        for (var k : keywords) {
            if (StringSearch.matchesPartialName(k, partialName)) {
                result.add(keywordCompletion(k));
            }
        }
    }

    private void completeScopeIdentifiers(
            URI uri, int line, int character, String partialName, List<CompletionItem> result) {
        var root = root(uri);
        // Add locals
        var locals = scopeMembers(uri, line, character, partialName);
        result.addAll(groupByOverload(locals, true));
        LOG.info(String.format("...found %d locals", locals.size()));

        // Add static imports
        var staticImports = staticImports(uri, partialName);
        result.addAll(groupByOverload(staticImports, true));
        LOG.info(String.format("...found %d static imports", staticImports.size()));

        // Add classes
        var startsWithUpperCase = partialName.length() > 0 && Character.isUpperCase(partialName.charAt(0));
        if (startsWithUpperCase) {
            var packageName = Objects.toString(root.getPackageName(), "");
            Predicate<String> matchesPartialName =
                    qualifiedName -> {
                        var className = StringSearch.lastName(qualifiedName);
                        return StringSearch.matchesPartialName(className, partialName);
                    };

            // Check JDK
            LOG.info("...checking JDK");
            for (var c : parent.jdkClasses) {
                if (tooManyItems(result.size())) return;
                if (!matchesPartialName.test(c)) continue;
                if (isSamePackage(c, packageName) || isPublicClassFile(c)) {
                    result.add(classNameCompletion(c, isImported(uri, c)));
                }
            }

            // Check classpath
            LOG.info("...checking classpath");
            var classPathNames = new HashSet<String>();
            for (var c : parent.classPathClasses) {
                if (tooManyItems(result.size())) return;
                if (!matchesPartialName.test(c)) continue;
                if (isSamePackage(c, packageName) || isPublicClassFile(c)) {
                    result.add(classNameCompletion(c, isImported(uri, c)));
                    classPathNames.add(c);
                }
            }

            // Check sourcepath
            LOG.info("...checking source path");
            for (var file : FileStore.all()) {
                if (tooManyItems(result.size())) return;
                // If file is in the same package, any class defined in the file is accessible
                var otherPackageName = FileStore.packageName(file);
                var samePackage = otherPackageName.equals(packageName) || otherPackageName.isEmpty();
                // If file is in a different package, only a class with the same name as the file is accessible
                var maybePublic = StringSearch.matchesPartialName(file.getFileName().toString(), partialName);
                if (samePackage || maybePublic) {
                    result.addAll(accessibleClasses(uri, file, partialName, packageName, classPathNames));
                }
            }
        }
    }

    private boolean isSamePackage(String className, String fromPackage) {
        return StringSearch.mostName(className).equals(fromPackage);
    }

    private boolean isPublicClassFile(String className) {
        try {
            var platform =
                    parent.fileManager.getJavaFileForInput(
                            StandardLocation.PLATFORM_CLASS_PATH, className, JavaFileObject.Kind.CLASS);
            if (platform != null) return isPublic(platform);
            var classpath =
                    parent.fileManager.getJavaFileForInput(
                            StandardLocation.CLASS_PATH, className, JavaFileObject.Kind.CLASS);
            if (classpath != null) return isPublic(classpath);
            return false;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private boolean isPublic(JavaFileObject classFile) {
        try (var in = classFile.openInputStream()) {
            var header = ClassHeader.of(in);
            return header.isPublic;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private List<CompletionItem> accessibleClasses(
            URI fromUri, Path toFile, String partialName, String fromPackage, Set<String> skip) {
        var parse = Parser.parseFile(toFile.toUri());
        var classNames = parse.accessibleClasses(partialName, fromPackage);
        var result = new ArrayList<CompletionItem>();
        for (var name : classNames) {
            // If class was already autocompleted using the classpath, skip it
            if (skip.contains(name)) continue;
            // Otherwise, add this name!
            result.add(classNameCompletion(name, isImported(fromUri, name)));
        }
        return result;
    }

    private List<Element> staticImports(URI uri, String partialName) {
        var root = root(uri);
        var result = new ArrayList<Element>();
        for (var i : root.getImports()) {
            if (!i.isStatic()) continue;
            var id = (MemberSelectTree) i.getQualifiedIdentifier();
            var path = trees.getPath(root, id.getExpression());
            var el = (TypeElement) trees.getElement(path);
            if (id.getIdentifier().contentEquals("*")) {
                for (var member : el.getEnclosedElements()) {
                    if (StringSearch.matchesPartialName(member.getSimpleName(), partialName)
                            && member.getModifiers().contains(Modifier.STATIC)) {
                        result.add(member);
                        if (tooManyItems(result.size())) return result;
                    }
                }
            } else {
                for (var member : el.getEnclosedElements()) {
                    if (StringSearch.matchesPartialName(member.getSimpleName(), partialName)
                            && member.getModifiers().contains(Modifier.STATIC)) {
                        result.add(member);
                        if (tooManyItems(result.size())) return result;
                    }
                }
            }
        }
        return result;
    }

    /** Find the smallest tree that includes the cursor */
    TreePath findPath(URI uri, int line, int character) {
        var root = root(uri);
        var trees = Trees.instance(borrow.task);
        var pos = trees.getSourcePositions();
        var cursor = root.getLineMap().getPosition(line, character);

        // Search for the smallest element that encompasses line:column
        class FindSmallest extends TreePathScanner<Void, Void> {
            TreePath found = null;

            boolean containsCursor(Tree tree) {
                long start = pos.getStartPosition(root, tree), end = pos.getEndPosition(root, tree);
                // If element has no position, give up
                if (start == -1 || end == -1) return false;
                // int x = 1, y = 2, ... requires special handling
                if (tree instanceof VariableTree) {
                    var v = (VariableTree) tree;
                    // Get contents of source
                    String source;
                    try {
                        source = root.getSourceFile().getCharContent(true).toString();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    // Find name in contents
                    var name = v.getName().toString();
                    start = source.indexOf(name, (int) start);
                    if (start == -1) {
                        LOG.warning(String.format("Can't find name `%s` in variable declaration `%s`", name, v));
                        return false;
                    }
                    end = start + name.length();
                }
                // Check if `tree` contains line:column
                return start <= cursor && cursor <= end;
            }

            @Override
            public Void scan(Tree tree, Void nothing) {
                // This is pre-order traversal, so the deepest element will be the last one remaining in `found`
                if (containsCursor(tree)) {
                    found = new TreePath(getCurrentPath(), tree);
                }
                super.scan(tree, nothing);
                return null;
            }

            @Override
            public Void visitErroneous(ErroneousTree node, Void nothing) {
                if (node.getErrorTrees() == null) return null;
                for (var t : node.getErrorTrees()) {
                    scan(t, nothing);
                }
                return null;
            }
        }
        var find = new FindSmallest();
        find.scan(root, null);
        if (find.found == null) {
            var message = String.format("No TreePath to %s %d:%d", uri, line, character);
            throw new RuntimeException(message);
        }
        return find.found;
    }

    private CompletionItem snippetCompletion(String label, String snippet) {
        var i = new CompletionItem();
        i.label = label;
        i.kind = CompletionItemKind.Snippet;
        i.insertText = snippet;
        i.insertTextFormat = InsertTextFormat.Snippet;
        i.sortText = 1 + i.label;
        return i;
    }

    private CompletionItem elementCompletion(Element e) {
        var i = new CompletionItem();
        i.label = e.getSimpleName().toString();
        i.kind = completionItemKind(e);
        i.detail = ShortTypePrinter.print(e.asType());
        // TODO prioritize based on usage?
        // TODO prioritize based on scope
        if (isMemberOfObject(e)) {
            i.sortText = 9 + i.label;
        } else {
            i.sortText = 2 + i.label;
        }
        // Save pointer for method and class doc resultion
        var ptr = new Ptr(e);
        i.data = new JsonPrimitive(ptr.toString());
        return i;
    }

    private CompletionItem overloadCompletion(List<ExecutableElement> methods) {
        var first = methods.get(0);
        var i = elementCompletion(first);
        i.label = methodLabel(first);
        i.insertText = first.getSimpleName().toString();
        i.filterText = first.getSimpleName().toString();
        // Add (+n overloads)
        if (methods.size() > 1) {
            var overloads = methods.size() - 1;
            i.label += " (+" + overloads + " overloads)";
        }
        // Save pointer for method and class doc resultion
        var ptr = new Ptr(first);
        i.data = new JsonPrimitive(ptr.toString());
        return i;
    }

    private void completeInvocation(List<ExecutableElement> methods, CompletionItem i) {
        var first = methods.get(0);
        i.insertText = first.getSimpleName() + "($0)";
        i.insertTextFormat = 2; // Snippet
        i.command = new Command();
        i.command.command = "editor.action.triggerParameterHints";
        if (methods.size() == 1 && first.getParameters().isEmpty()) {
            if (first.getReturnType().getKind() == TypeKind.VOID) {
                i.insertText = first.getSimpleName() + "();$0";
            } else {
                i.insertText = first.getSimpleName() + "()$0";
            }
        }
    }

    private String methodLabel(ExecutableElement method) {
        var args = new StringJoiner(", ");
        for (var p : method.getParameters()) {
            args.add(ShortTypePrinter.print(p.asType()));
        }
        return method.getSimpleName() + "(" + args + ")";
    }

    private Integer completionItemKind(Element e) {
        switch (e.getKind()) {
            case ANNOTATION_TYPE:
                return CompletionItemKind.Interface;
            case CLASS:
                return CompletionItemKind.Class;
            case CONSTRUCTOR:
                return CompletionItemKind.Constructor;
            case ENUM:
                return CompletionItemKind.Enum;
            case ENUM_CONSTANT:
                return CompletionItemKind.EnumMember;
            case EXCEPTION_PARAMETER:
                return CompletionItemKind.Property;
            case FIELD:
                return CompletionItemKind.Field;
            case STATIC_INIT:
            case INSTANCE_INIT:
                return CompletionItemKind.Function;
            case INTERFACE:
                return CompletionItemKind.Interface;
            case LOCAL_VARIABLE:
                return CompletionItemKind.Variable;
            case METHOD:
                return CompletionItemKind.Method;
            case PACKAGE:
                return CompletionItemKind.Module;
            case PARAMETER:
                return CompletionItemKind.Property;
            case RESOURCE_VARIABLE:
                return CompletionItemKind.Variable;
            case TYPE_PARAMETER:
                return CompletionItemKind.TypeParameter;
            case OTHER:
            default:
                return null;
        }
    }

    private boolean isMemberOfObject(Element e) {
        var parent = e.getEnclosingElement();
        if (parent instanceof TypeElement) {
            var type = (TypeElement) parent;
            return type.getQualifiedName().contentEquals("java.lang.Object");
        }
        return false;
    }

    private CompletionItem keywordCompletion(String keyword) {
        var i = new CompletionItem();
        i.label = keyword;
        i.kind = CompletionItemKind.Keyword;
        i.detail = "keyword";
        i.sortText = 3 + i.label;
        return i;
    }

    private CompletionItem packageCompletion(String fullName, String name) {
        var i = new CompletionItem();
        i.label = name;
        i.kind = CompletionItemKind.Module;
        i.detail = fullName;
        i.sortText = 2 + i.label;
        return i;
    }

    private CompletionItem classNameCompletion(String name, boolean isImported) {
        var i = new CompletionItem();
        i.label = StringSearch.lastName(name);
        i.kind = CompletionItemKind.Class;
        i.detail = name;
        if (isImported) {
            i.sortText = 2 + i.label;
        } else {
            i.sortText = 4 + i.label;
        }
        return i;
    }

    private static final Logger LOG = Logger.getLogger("main");
}
