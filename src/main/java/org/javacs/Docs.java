package org.javacs;

import com.overzealous.remark.Options;
import com.overzealous.remark.Remark;
import com.sun.source.doctree.DocCommentTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.DocTrees;
import com.sun.source.util.TreeScanner;
import com.sun.source.util.Trees;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;

class Docs {

    /** File manager with source-path + platform sources, which we will use to look up individual source files */
    private final StandardJavaFileManager fileManager;

    private static Path srcZip() {
        try {
            var fs = FileSystems.newFileSystem(Lib.SRC_ZIP, Docs.class.getClassLoader());
            return fs.getPath("/");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    Docs(Set<Path> sourcePath) {
        this.fileManager =
                ServiceLoader.load(JavaCompiler.class).iterator().next().getStandardFileManager(__ -> {}, null, null);

        // Compute the full source path, including src.zip for platform classes
        var sourcePathFiles = sourcePath.stream().map(Path::toFile).collect(Collectors.toSet());

        try {
            fileManager.setLocation(StandardLocation.SOURCE_PATH, sourcePathFiles);
            fileManager.setLocationFromPaths(StandardLocation.MODULE_SOURCE_PATH, Set.of(srcZip()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private Optional<JavaFileObject> file(String className) {
        try {
            var fromSourcePath =
                    fileManager.getJavaFileForInput(
                            StandardLocation.SOURCE_PATH, className, JavaFileObject.Kind.SOURCE);
            if (fromSourcePath != null) return Optional.of(fromSourcePath);
            for (var module : Classes.JDK_MODULES) {
                var moduleLocation = fileManager.getLocationForModule(StandardLocation.MODULE_SOURCE_PATH, module);
                if (moduleLocation == null) continue;
                var fromModuleSourcePath =
                        fileManager.getJavaFileForInput(moduleLocation, className, JavaFileObject.Kind.SOURCE);
                if (fromModuleSourcePath != null) return Optional.of(fromModuleSourcePath);
            }
            return Optional.empty();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private boolean memberNameEquals(Tree member, String name) {
        if (member instanceof VariableTree) {
            var variable = (VariableTree) member;
            return variable.getName().contentEquals(name);
        } else if (member instanceof MethodTree) {
            var method = (MethodTree) member;
            return method.getName().contentEquals(name);
        } else return false;
    }

    private Optional<DocCommentTree> findDoc(String className, String memberName) {
        var file = file(className);
        if (!file.isPresent()) return Optional.empty();
        var task = Parser.parseTask(file.get());
        CompilationUnitTree root;
        try {
            var it = task.parse().iterator();
            if (!it.hasNext()) {
                LOG.warning("Found no CompilationUnitTree in " + file);
                return Optional.empty();
            }
            root = it.next();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        var docs = DocTrees.instance(task);
        var trees = Trees.instance(task);
        class Find extends TreeScanner<Void, Void> {
            Optional<DocCommentTree> result = Optional.empty();

            @Override
            public Void visitClass(ClassTree node, Void aVoid) {
                // TODO this will be wrong when inner class has same name as top-level class
                if (node.getSimpleName().contentEquals(Parser.lastName(className))) {
                    if (memberName == null) {
                        var path = trees.getPath(root, node);
                        result = Optional.ofNullable(docs.getDocCommentTree(path));
                    } else {
                        for (var member : node.getMembers()) {
                            if (memberNameEquals(member, memberName)) {
                                var path = trees.getPath(root, member);
                                result = Optional.ofNullable(docs.getDocCommentTree(path));
                            }
                        }
                    }
                }
                return null;
            }
        }
        var find = new Find();
        find.scan(root, null);
        return find.result;
    }

    Optional<DocCommentTree> memberDoc(String className, String memberName) {
        Objects.requireNonNull(className);
        Objects.requireNonNull(memberName);

        return findDoc(className, memberName);
    }

    Optional<DocCommentTree> classDoc(String className) {
        Objects.requireNonNull(className);

        return findDoc(className, null);
    }

    Optional<MethodTree> findMethod(String className, String methodName) {
        Objects.requireNonNull(className);
        Objects.requireNonNull(methodName);

        var file = file(className);
        if (!file.isPresent()) return Optional.empty();
        var task = Parser.parseTask(file.get());
        CompilationUnitTree root;
        try {
            root = task.parse().iterator().next();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        class Find extends TreeScanner<Void, Void> {
            Optional<MethodTree> result = Optional.empty();

            @Override
            public Void visitClass(ClassTree node, Void aVoid) {
                // TODO this will be wrong when inner class has same name as top-level class
                if (node.getSimpleName().contentEquals(Parser.lastName(className))) {
                    for (var member : node.getMembers()) {
                        if (member instanceof MethodTree) {
                            var method = (MethodTree) member;
                            if (method.getName().contentEquals(methodName)) result = Optional.of(method);
                        }
                    }
                }
                return null;
            }
        }
        var find = new Find();
        find.scan(root, null);
        return find.result;
    }

    private static final Pattern HTML_TAG = Pattern.compile("<(\\w+)>");

    private static boolean isHtml(String text) {
        Matcher tags = HTML_TAG.matcher(text);
        while (tags.find()) {
            String tag = tags.group(1);
            String close = String.format("</%s>", tag);
            int findClose = text.indexOf(close, tags.end());
            if (findClose != -1) return true;
        }
        return false;
    }

    // TODO is this still necessary?
    /** If `commentText` looks like HTML, convert it to markdown */
    static String htmlToMarkdown(String commentText) {
        if (isHtml(commentText)) {
            Options options = new Options();

            options.tables = Options.Tables.CONVERT_TO_CODE_BLOCK;
            options.hardwraps = true;
            options.inlineLinks = true;
            options.autoLinks = true;
            options.reverseHtmlSmartPunctuation = true;

            return new Remark(options).convertFragment(commentText);
        } else return commentText;
    }

    private static final Logger LOG = Logger.getLogger("main");
}
