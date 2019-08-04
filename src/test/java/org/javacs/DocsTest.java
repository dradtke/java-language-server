package org.javacs;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

import java.nio.file.Paths;
import java.util.Collections;
import java.util.Set;
import java.util.logging.Logger;
import org.junit.Before;
import org.junit.Test;

public class DocsTest {

    @Before
    public void setWorkspaceRoot() {
        FileStore.setWorkspaceRoots(Set.of(LanguageServerFixture.SIMPLE_WORKSPACE_ROOT));
    }

    @Test
    public void classDoc() {
        var sourcePath = Set.of(JavaCompilerServiceTest.simpleProjectSrc());
        var docs = new Docs(sourcePath);
        var ptr = new Ptr("ClassDoc");
        var file = docs.find(ptr).get();
        var parse = Parser.parseJavaFileObject(file);
        var path = parse.fuzzyFind(ptr).get();
        var tree = parse.doc(path);
        assertThat(tree.getFirstSentence(), hasToString("A great class"));
    }

    @Test
    public void memberDoc() {
        var sourcePath = Set.of(JavaCompilerServiceTest.simpleProjectSrc());
        var docs = new Docs(sourcePath);
        var ptr = new Ptr("LocalMethodDoc#targetMethod(int)");
        var file = docs.find(ptr).get();
        var parse = Parser.parseJavaFileObject(file);
        var path = parse.fuzzyFind(ptr).get();
        var tree = parse.doc(path);
        assertThat(tree.getFirstSentence(), hasToString("A great method"));
    }

    @Test
    public void platformDoc() {
        var docs = new Docs(Set.of());
        var ptr = new Ptr("java.util/List");
        var file = docs.find(ptr).get();
        var parse = Parser.parseJavaFileObject(file);
        var path = parse.fuzzyFind(ptr).get();
        var tree = parse.doc(path);
        assertThat(tree.getFirstSentence(), not(empty()));
    }

    @Test
    public void classPathDoc() {
        var bazelWorkspace = Paths.get("src/test/examples/bazel-project");
        var infer = new InferConfig(bazelWorkspace, Collections.emptySet(), Paths.get("nowhere"), Paths.get("nowhere"));
        var docPath = infer.buildDocPath();
        LOG.info("Doc path is " + docPath);
        var docs = new Docs(docPath);
        var ptr = new Ptr("com.google.common.collect/ImmutableSet");
        var file = docs.find(ptr).get();
        var parse = Parser.parseJavaFileObject(file);
        var path = parse.fuzzyFind(ptr).get();
        var tree = parse.doc(path);
        assertThat(tree.getFirstSentence(), not(empty()));
    }

    private static final Logger LOG = Logger.getLogger("main");
}
