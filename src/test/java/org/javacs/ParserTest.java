package org.javacs;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

import java.nio.file.Paths;
import java.util.Collections;
import org.javacs.lsp.DidChangeTextDocumentParams;
import org.javacs.lsp.DidCloseTextDocumentParams;
import org.javacs.lsp.DidOpenTextDocumentParams;
import org.javacs.lsp.TextDocumentContentChangeEvent;
import org.junit.Test;

public class ParserTest {
    @Test
    public void testMatchesTitleCase() {
        assertTrue(Parser.matchesTitleCase("FooBar", "fb"));
        assertTrue(Parser.matchesTitleCase("FooBar", "fob"));
        assertTrue(Parser.matchesTitleCase("AnyPrefixFooBar", "fb"));
        assertTrue(Parser.matchesTitleCase("AutocompleteBetweenLines", "ABetweenLines"));
        assertTrue(Parser.matchesTitleCase("UPPERFooBar", "fb"));
        assertFalse(Parser.matchesTitleCase("Foobar", "fb"));

        assertTrue(Parser.matchesTitleCase("Prefix FooBar", "fb"));
        assertTrue(Parser.matchesTitleCase("Prefix FooBar", "fob"));
        assertTrue(Parser.matchesTitleCase("Prefix AnyPrefixFooBar", "fb"));
        assertTrue(Parser.matchesTitleCase("Prefix AutocompleteBetweenLines", "ABetweenLines"));
        assertTrue(Parser.matchesTitleCase("Prefix UPPERFooBar", "fb"));
        assertFalse(Parser.matchesTitleCase("Foo Bar", "fb"));
    }

    @Test
    public void searchLargeFile() {
        var largeFile = Paths.get(FindResource.uri("/org/javacs/example/LargeFile.java"));
        assertTrue(Parser.containsWordMatching(largeFile, "removeMethodBodies"));
        assertFalse(Parser.containsWordMatching(largeFile, "removeMethodBodiez"));
    }

    @Test
    public void searchSmallFile() {
        var smallFile = Paths.get(FindResource.uri("/org/javacs/example/Goto.java"));
        assertTrue(Parser.containsWordMatching(smallFile, "nonDefaultConstructor"));
        assertFalse(Parser.containsWordMatching(smallFile, "removeMethodBodies"));
    }

    @Test
    public void searchOpenFile() {
        // Open file
        var smallFile = Paths.get(FindResource.uri("/org/javacs/example/Goto.java"));
        var open = new DidOpenTextDocumentParams();
        open.textDocument.uri = smallFile.toUri();
        FileStore.open(open);
        // Edit file
        var change = new DidChangeTextDocumentParams();
        change.textDocument.uri = smallFile.toUri();
        var evt = new TextDocumentContentChangeEvent();
        evt.text = "package org.javacs.example; class Foo { }";
        change.contentChanges.add(evt);
        FileStore.change(change);
        // Check that Parser sees the edits
        try {
            assertTrue(Parser.containsWordMatching(smallFile, "Foo"));
        } finally {
            // Close file
            var close = new DidCloseTextDocumentParams();
            close.textDocument.uri = smallFile.toUri();
            FileStore.close(close);
        }
    }

    @Test
    public void largeFilePossibleReference() {
        var largeFile = Paths.get(FindResource.uri("/org/javacs/example/LargeFile.java"));
        assertTrue(Parser.containsImport(largeFile, "java.util.logging", "Logger"));
        assertTrue(Parser.containsWord(largeFile, "removeMethodBodies"));
        assertFalse(Parser.containsWord(largeFile, "removeMethodBodiez"));
    }

    @Test
    public void findAutocompleteBetweenLines() {
        var rel = Paths.get("src", "org", "javacs", "example", "AutocompleteBetweenLines.java");
        var file = LanguageServerFixture.DEFAULT_WORKSPACE_ROOT.resolve(rel);
        assertTrue(Parser.containsWordMatching(file, "ABetweenLines"));
    }

    @Test
    public void findExistingImports() {
        var rel = Paths.get("src", "org", "javacs", "doimport");
        var dir = LanguageServerFixture.DEFAULT_WORKSPACE_ROOT.resolve(rel);
        FileStore.setWorkspaceRoots(Collections.singleton(dir));
        var existing = Parser.existingImports(FileStore.all());
        assertThat(existing.classes, hasItems("java.util.List"));
        assertThat(existing.packages, hasItems("java.util", "java.io"));
    }
}
