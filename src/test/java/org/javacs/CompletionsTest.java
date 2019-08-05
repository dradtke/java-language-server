package org.javacs;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.stream.Collectors;
import org.javacs.lsp.*;
import org.junit.Ignore;
import org.junit.Test;

public class CompletionsTest extends CompletionsBase {

    // TODO rename Autocomplete Complete because Autocomplete is long and ugly

    @Test
    public void staticMember() {
        var file = "/org/javacs/example/AutocompleteStaticMember.java";

        // Static methods
        var suggestions = filterText(file, 5, 38);

        assertThat(suggestions, hasItems("testFieldStatic", "testMethodStatic", "class"));
        assertThat(suggestions, not(hasItems("testField", "testMethod", "getClass")));
    }

    @Test
    public void staticReference() {
        var file = "/org/javacs/example/AutocompleteStaticReference.java";

        // Static methods
        var suggestions = filterText(file, 7, 48);

        assertThat(suggestions, hasItems("testMethod", "testMethodStatic", "new"));
        assertThat(suggestions, not(hasItems("class")));
    }

    @Test
    public void member() {
        var file = "/org/javacs/example/AutocompleteMember.java";

        // Virtual testMethods
        var suggestions = filterText(file, 5, 14);

        assertThat(
                "excludes static members",
                suggestions,
                not(
                        hasItems(
                                "testFieldStatic",
                                "testMethodStatic",
                                "testFieldStaticPrivate",
                                "testMethodStaticPrivate",
                                "class",
                                "AutocompleteMember")));
        assertThat(
                "includes non-static members",
                suggestions,
                hasItems("testFields", "testMethods", "testFieldsPrivate", "testMethodsPrivate", "getClass"));
        assertThat("excludes constructors", suggestions, not(hasItem(startsWith("AutocompleteMember"))));
    }

    @Test
    public void enumMapMembers() {
        var suggestions = filterText("/org/javacs/example/CompleteEnumMap.java", 9, 13);
        assertThat(
                suggestions,
                hasItems(
                        "clear",
                        "clone",
                        "containsKey",
                        "containsValue",
                        "entrySet",
                        "equals",
                        "get",
                        "hashCode",
                        "keySet",
                        "put",
                        "putAll",
                        "remove",
                        "size",
                        "values"));
    }

    @Test
    public void fieldFromInitBlock() {
        var file = "/org/javacs/example/AutocompleteMembers.java";

        // f
        var suggestions = filterText(file, 8, 10);

        assertThat(suggestions, hasItems("testFields", "testFieldStatic", "testMethods", "testMethodStatic"));
    }

    @Test
    public void thisDotFieldFromInitBlock() {
        var file = "/org/javacs/example/AutocompleteMembers.java";

        // this.f
        var suggestions = filterText(file, 9, 15);

        assertThat(suggestions, hasItems("testFields", "testMethods"));
        assertThat(suggestions, not(hasItems("testFieldStatic", "testMethodStatic")));
    }

    @Test
    public void classDotFieldFromInitBlock() {
        var file = "/org/javacs/example/AutocompleteMembers.java";

        // AutocompleteMembers.f
        var suggestions = filterText(file, 10, 30);

        assertThat(suggestions, hasItems("testFieldStatic", "testMethodStatic"));
        assertThat(suggestions, not(hasItems("testFields", "testMethods")));
    }

    @Test
    public void fieldFromMethod() {
        var file = "/org/javacs/example/AutocompleteMembers.java";

        // f
        var suggestions = filterText(file, 22, 10);

        assertThat(
                suggestions,
                hasItems("testFields", "testFieldStatic", "testMethods", "testMethodStatic", "testArguments"));
    }

    @Test
    public void thisDotFieldFromMethod() {
        var file = "/org/javacs/example/AutocompleteMembers.java";

        // this.f
        var suggestions = filterText(file, 23, 15);

        assertThat(suggestions, hasItems("testFields", "testMethods"));
        assertThat(suggestions, not(hasItems("testFieldStatic", "testMethodStatic", "testArguments")));
    }

    @Test
    public void classDotFieldFromMethod() {
        var file = "/org/javacs/example/AutocompleteMembers.java";

        // AutocompleteMembers.f
        var suggestions = filterText(file, 24, 30);

        assertThat(suggestions, hasItems("testFieldStatic", "testMethodStatic"));
        assertThat(suggestions, not(hasItems("testFields", "testMethods", "testArguments")));
    }

    @Test
    public void thisRefMethodFromMethod() {
        var file = "/org/javacs/example/AutocompleteMembers.java";

        // this::m
        var suggestions = filterText(file, 25, 59);

        assertThat(suggestions, hasItems("testMethods"));
        assertThat(suggestions, not(hasItems("testFields", "testFieldStatic", "testMethodStatic")));
    }

    @Test
    public void classRefMethodFromMethod() {
        var file = "/org/javacs/example/AutocompleteMembers.java";

        // AutocompleteMembers::m
        var suggestions = filterText(file, 26, 74);

        assertThat(suggestions, hasItems("testMethodStatic", "testMethods"));
        assertThat(suggestions, not(hasItems("testFields", "testFieldStatic")));
    }

    @Test
    @Ignore // javac doesn't give us helpful info about the fact that static initializers are static
    public void fieldFromStaticInitBlock() {
        var file = "/org/javacs/example/AutocompleteMembers.java";

        // f
        var suggestions = filterText(file, 16, 10);

        assertThat(suggestions, hasItems("testFieldStatic", "testMethodStatic"));
        assertThat(suggestions, not(hasItems("testFields", "testMethods")));
    }

    @Test
    public void classDotFieldFromStaticInitBlock() {
        var file = "/org/javacs/example/AutocompleteMembers.java";

        // AutocompleteMembers.f
        var suggestions = filterText(file, 17, 30);

        assertThat(suggestions, hasItems("testFieldStatic", "testMethodStatic"));
        assertThat(suggestions, not(hasItems("testFields", "testMethods")));
    }

    @Test
    public void classRefFieldFromStaticInitBlock() {
        var file = "/org/javacs/example/AutocompleteMembers.java";

        // AutocompleteMembers::m
        var suggestions = filterText(file, 17, 30);

        assertThat(suggestions, hasItems("testMethodStatic"));
        assertThat(suggestions, not(hasItems("testFields", "testFieldStatic", "testMethods")));
    }

    @Test
    public void fieldFromStaticMethod() {
        var file = "/org/javacs/example/AutocompleteMembers.java";

        // f
        var suggestions = filterText(file, 30, 10);

        assertThat(suggestions, hasItems("testFieldStatic", "testMethodStatic", "testArguments"));
        assertThat(suggestions, not(hasItems("testFields", "testMethods")));
    }

    @Test
    public void classDotFieldFromStaticMethod() {
        var file = "/org/javacs/example/AutocompleteMembers.java";

        // AutocompleteMembers.f
        var suggestions = filterText(file, 31, 30);

        assertThat(suggestions, hasItems("testFieldStatic", "testMethodStatic"));
        assertThat(suggestions, not(hasItems("testFields", "testMethods", "testArguments")));
    }

    @Test
    public void classRefFieldFromStaticMethod() {
        var file = "/org/javacs/example/AutocompleteMembers.java";

        // TODO
        // AutocompleteMembers::m
        var suggestions = filterText(file, 17, 30);

        assertThat(suggestions, hasItems("testMethodStatic"));
        assertThat(suggestions, not(hasItems("testFields", "testFieldStatic", "testMethods")));
    }

    private static String sortText(CompletionItem i) {
        if (i.sortText != null) return i.sortText;
        else return i.label;
    }

    @Test
    public void otherMethod() {
        var file = "/org/javacs/example/AutocompleteOther.java";

        // new AutocompleteMember().
        var suggestions = filterText(file, 5, 34);

        assertThat(suggestions, not(hasItems("testFieldStatic", "testMethodStatic", "class")));
        assertThat(suggestions, not(hasItems("testFieldStaticPrivate", "testMethodStaticPrivate")));
        assertThat(suggestions, not(hasItems("testFieldsPrivate", "testMethodsPrivate")));
        assertThat(suggestions, hasItems("testFields", "testMethods", "getClass"));
    }

    @Test
    public void otherStatic() {
        var file = "/org/javacs/example/AutocompleteOther.java";

        // AutocompleteMember.
        var suggestions = filterText(file, 7, 28);

        assertThat(suggestions, hasItems("testFieldStatic", "testMethodStatic", "class"));
        assertThat(suggestions, not(hasItems("testFieldStaticPrivate", "testMethodStaticPrivate")));
        assertThat(suggestions, not(hasItems("testFieldsPrivate", "testMethodsPrivate")));
        assertThat(suggestions, not(hasItems("testFields", "testMethods", "getClass")));
    }

    @Test
    public void otherDotClassDot() {
        var file = "/org/javacs/example/AutocompleteOther.java";

        // AutocompleteMember.class.
        var suggestions = filterText(file, 8, 33);

        assertThat(suggestions, hasItems("getName", "getClass"));
        assertThat(suggestions, not(hasItems("testFieldStatic", "testMethodStatic", "class")));
        assertThat(suggestions, not(hasItems("testFieldStaticPrivate", "testMethodStaticPrivate")));
        assertThat(suggestions, not(hasItems("testFieldsPrivate", "testMethodsPrivate")));
        assertThat(suggestions, not(hasItems("testFields", "testMethods")));
    }

    @Test
    public void otherClass() {
        var file = "/org/javacs/example/AutocompleteOther.java";

        // Auto?
        var suggestions = filterText(file, 6, 13);

        assertThat(suggestions, hasItems("AutocompleteOther", "AutocompleteMember"));
    }

    @Test
    public void arrayLength() {
        var file = "/org/javacs/example/AutocompleteArray.java";

        // a.?
        var suggestions = filterText(file, 7, 11);

        assertThat(suggestions, hasItems("length"));
    }

    @Test
    public void indirectSuper() {
        var file = "/org/javacs/example/CompleteIndirectSuper.java";

        // a.?
        var suggestions = filterText(file, 5, 14);

        assertThat(suggestions, hasItems("selfMethod", "super1Method", "super2Method"));
    }

    @Ignore // We are now managing imports with FixImports
    @Test
    public void addImport() {
        var file = "/org/javacs/example/AutocompleteOther.java";

        // Name of class
        var items = items(file, 9, 17);

        for (var item : items) {
            if ("ArrayList".equals(item.label)) {
                assertThat(item.additionalTextEdits, not(nullValue()));
                assertThat(item.additionalTextEdits, not(empty()));

                return;
            }
        }

        fail("No ArrayList in " + items);
    }

    @Ignore // We are now managing imports with FixImports
    @Test
    public void dontImportSamePackage() {
        var file = "/org/javacs/example/AutocompleteOther.java";

        // Name of class
        var items = items(file, 6, 10);

        for (var item : items) {
            if ("AutocompleteMember".equals(item.label)) {
                assertThat(item.additionalTextEdits, either(empty()).or(nullValue()));

                return;
            }
        }

        fail("No AutocompleteMember in " + items);
    }

    @Ignore // We are now managing imports with FixImports
    @Test
    public void dontImportJavaLang() {
        var file = "/org/javacs/example/AutocompleteOther.java";

        // Name of class
        var items = items(file, 11, 38);

        for (var item : items) {
            if ("ArrayIndexOutOfBoundsException".equals(item.label)) {
                assertThat(item.additionalTextEdits, either(empty()).or(nullValue()));

                return;
            }
        }

        fail("No ArrayIndexOutOfBoundsException in " + items);
    }

    @Ignore // We are now managing imports with FixImports
    @Test
    public void dontImportSelf() {
        var file = "/org/javacs/example/AutocompleteOther.java";

        // Name of class
        var items = items(file, 6, 10);

        for (var item : items) {
            if ("AutocompleteOther".equals(item.label)) {
                assertThat(item.additionalTextEdits, either(empty()).or(nullValue()));

                return;
            }
        }

        fail("No AutocompleteOther in " + items);
    }

    @Ignore // We are now managing imports with FixImports
    @Test
    public void dontImportAlreadyImported() {
        var file = "/org/javacs/example/AutocompleteOther.java";

        // Name of class
        var items = items(file, 12, 14);

        for (var item : items) {
            if ("Arrays".equals(item.label)) {
                assertThat(item.additionalTextEdits, either(empty()).or(nullValue()));

                return;
            }
        }

        fail("No Arrays in " + items);
    }

    @Ignore // We are now managing imports with FixImports
    @Test
    public void dontImportAlreadyImportedStar() {
        var file = "/org/javacs/example/AutocompleteOther.java";

        // Name of class
        var items = items(file, 10, 26);

        for (var item : items) {
            if ("ArrayBlockingQueue".equals(item.label)) {
                assertThat(item.additionalTextEdits, either(empty()).or(nullValue()));

                return;
            }
        }

        fail("No ArrayBlockingQueue in " + items);
    }

    @Test
    public void fromClasspath() {
        var file = "/org/javacs/example/AutocompleteFromClasspath.java";

        // Static methods
        var suggestions = filterText(file, 8, 17);

        assertThat(suggestions, hasItems("add", "addAll"));
    }

    @Test
    public void betweenLines() {
        var file = "/org/javacs/example/AutocompleteBetweenLines.java";

        // Static methods
        var suggestions = filterText(file, 9, 18);

        assertThat(suggestions, hasItems("add"));
    }

    @Test
    public void reference() {
        var file = "/org/javacs/example/AutocompleteReference.java";

        // Static methods
        var suggestions = insertTemplate(file, 7, 21);

        assertThat(suggestions, not(hasItems("testMethodStatic")));
        assertThat(suggestions, hasItems("testMethods", "getClass"));
    }

    @Test
    @Ignore // This has been subsumed by Javadocs
    public void docstring() {
        var file = "/org/javacs/example/AutocompleteDocstring.java";
        var docstrings = documentation(file, 8, 14);

        assertThat(docstrings, hasItems("A testMethods", "A testFields"));

        docstrings = documentation(file, 12, 31);

        assertThat(docstrings, hasItems("A testFieldStatic", "A testMethodStatic"));
    }

    @Test
    public void classes() {
        var file = "/org/javacs/example/AutocompleteClasses.java";

        // Fix?
        var suggestions = filterText(file, 5, 12);

        assertThat(suggestions, hasItems("FixParseErrorAfter"));

        // Some?
        suggestions = filterText(file, 6, 13);

        assertThat(suggestions, hasItems("SomeInnerClass"));

        // List?
        suggestions = filterText(file, 7, 12);

        assertThat(suggestions, hasItems("List"));
    }

    @Test
    public void editMethodName() {
        var file = "/org/javacs/example/AutocompleteEditMethodName.java";

        // Static methods
        var suggestions = filterText(file, 5, 21);

        assertThat(suggestions, hasItems("getClass"));
    }

    @Test
    @Ignore // This has been subsumed by Javadocs
    public void restParams() {
        var file = "/org/javacs/example/AutocompleteRest.java";

        // Static methods
        var items = items(file, 5, 18);
        var suggestions = items.stream().map(i -> i.label).collect(Collectors.toSet());
        var details = items.stream().map(i -> i.detail).collect(Collectors.toSet());

        assertThat(suggestions, hasItems("restMethod"));
        assertThat(details, hasItems("void (String... params)"));
    }

    @Test
    public void constructor() {
        var file = "/org/javacs/example/AutocompleteConstructor.java";

        // Static methods
        var suggestions = filterText(file, 5, 25);

        assertThat(suggestions, hasItem(startsWith("AutocompleteConstructor")));
        assertThat(suggestions, hasItem(startsWith("AutocompleteMember")));
    }

    @Ignore // We are now managing imports with FixImports
    @Test
    public void autoImportConstructor() {
        var file = "/org/javacs/example/AutocompleteConstructor.java";

        // Static methods
        var items = items(file, 6, 19);
        var suggestions = items.stream().map(i -> i.insertText).collect(Collectors.toList());

        assertThat(suggestions, hasItems("ArrayList<>($0)"));

        for (var each : items) {
            if (each.insertText.equals("ArrayList<>"))
                assertThat("new ? auto-imports", each.additionalTextEdits, both(not(empty())).and(not(nullValue())));
        }
    }

    @Ignore
    @Test
    public void importFromSource() {
        var file = "/org/javacs/example/AutocompletePackage.java";
        var suggestions = filterText(file, 3, 12);

        assertThat("Does not have own package class", suggestions, hasItems("javacs"));
    }

    @Test
    public void importFromClasspath() {
        var file = "/org/javacs/example/AutocompletePackage.java";
        var suggestions = filterText(file, 5, 13);

        assertThat("Has class from classpath", suggestions, hasItems("util"));
    }

    // TODO top level of import
    @Ignore
    @Test
    public void importFirstId() {
        var file = "/org/javacs/example/AutocompletePackage.java";

        // import ?
        var suggestions = filterText(file, 7, 9);

        assertThat("Has class from classpath", suggestions, hasItems("com", "org"));
    }

    @Test
    public void emptyClasspath() {
        var file = "/org/javacs/example/AutocompletePackage.java";

        // Static methods
        var suggestions = filterText(file, 6, 12);

        assertThat("Has deeply nested class", suggestions, not(hasItems("google.common.collect.Lists")));
    }

    @Test
    public void importClass() {
        var file = "/org/javacs/example/AutocompletePackage.java";

        // Static methods
        var items = items(file, 4, 25);
        var suggestions = items.stream().map(i -> i.label).collect(Collectors.toList());

        assertThat(suggestions, hasItems("OtherPackagePublic"));
        assertThat(suggestions, not(hasItems("OtherPackagePrivate")));

        // Imports are now being managed by FixImports
        // for (var item : items) {
        //     if (item.label.equals("OtherPackagePublic"))
        //         assertThat(
        //                 "Don't import when completing imports",
        //                 item.additionalTextEdits,
        //                 either(empty()).or(nullValue()));
        // }
    }

    @Test
    public void otherPackageId() {
        var file = "/org/javacs/example/AutocompleteOtherPackage.java";

        // Static methods
        var items = items(file, 5, 14);
        var suggestions = items.stream().map(i -> i.label).collect(Collectors.toList());

        assertThat(suggestions, hasItems("OtherPackagePublic"));
        assertThat(suggestions, not(hasItems("OtherPackagePrivate")));

        // for (var item : items) {
        //     if (item.label.equals("OtherPackagePublic"))
        //         assertThat("Auto-import OtherPackagePublic", item.additionalTextEdits, not(empty()));
        // }
    }

    @Test
    public void fieldFromStaticInner() {
        var file = "/org/javacs/example/AutocompleteOuter.java";

        // Initializer of static inner class
        var suggestions = filterText(file, 12, 14);

        assertThat(suggestions, hasItems("testMethodStatic", "testFieldStatic"));
        // TODO this is not visible
        // assertThat(suggestions, not(hasItems("testMethods", "testFields")));
    }

    @Test
    public void fieldFromInner() {
        var file = "/org/javacs/example/AutocompleteOuter.java";

        // Initializer of inner class
        var suggestions = filterText(file, 18, 14);

        assertThat(suggestions, hasItems("testMethodStatic", "testFieldStatic"));
        assertThat(suggestions, hasItems("testMethods", "testFields"));
    }

    @Test
    public void classDotClassFromMethod() {
        var file = "/org/javacs/example/AutocompleteInners.java";

        // AutocompleteInners.I
        var suggestions = filterText(file, 5, 29);

        assertThat("suggests qualified inner class declaration", suggestions, hasItem("InnerClass"));
        assertThat("suggests qualified inner enum declaration", suggestions, hasItem("InnerEnum"));
    }

    @Test
    public void innerClassFromMethod() {
        var file = "/org/javacs/example/AutocompleteInners.java";

        // I
        var suggestions = filterText(file, 6, 10);

        assertThat("suggests unqualified inner class declaration", suggestions, hasItem("InnerClass"));
        assertThat("suggests unqualified inner enum declaration", suggestions, hasItem("InnerEnum"));
    }

    @Test
    public void newClassDotInnerClassFromMethod() {
        var file = "/org/javacs/example/AutocompleteInners.java";

        // new AutocompleteInners.I
        var suggestions = filterText(file, 10, 33);

        assertThat("suggests qualified inner class declaration", suggestions, hasItem("InnerClass"));
        // TODO you can't actually make an inner enum
        // assertThat("does not suggest enum", suggestions, not(hasItem("InnerEnum")));
    }

    @Test
    public void newInnerClassFromMethod() {
        var file = "/org/javacs/example/AutocompleteInners.java";

        // new Inner?
        var suggestions = filterText(file, 11, 18);

        assertThat("suggests unqualified inner class declaration", suggestions, hasItem("InnerClass"));
        // TODO you can't actually make an inner enum
        // assertThat("does not suggest enum", suggestions, not(hasItem("InnerEnum")));
    }

    @Test
    public void innerEnum() {
        var file = "/org/javacs/example/AutocompleteInners.java";
        var suggestions = filterText(file, 15, 40);

        assertThat("suggests enum constants", suggestions, hasItems("Foo"));
    }

    @Test
    public void enumConstantFromSourcePath() {
        var file = "/org/javacs/example/AutocompleteCase.java";
        var suggestions = filterText(file, 6, 18);

        assertThat("suggests enum options", suggestions, containsInAnyOrder("Foo", "Bar"));
    }

    @Test
    public void enumConstantFromSourcePathPartial() {
        var file = "/org/javacs/example/AutocompleteCasePartial.java";
        var suggestions = filterText(file, 6, 19);

        assertThat("suggests enum options", suggestions, hasItem("Foo"));
    }

    @Test
    public void enumConstantFromClassPath() {
        var file = "/org/javacs/example/AutocompleteCaseFromClasspath.java";
        var suggestions = filterText(file, 8, 18);

        assertThat("suggests enum options", suggestions, containsInAnyOrder("FULL", "LONG", "MEDIUM", "SHORT"));
    }

    @Test
    public void enumConstantFromClassPathPartial() {
        var file = "/org/javacs/example/AutocompleteCaseFromClasspathPartial.java";
        var suggestions = filterText(file, 8, 19);

        assertThat("suggests enum options", suggestions, hasItem("FULL"));
    }

    @Test
    public void staticStarImport() {
        var file = "/org/javacs/example/AutocompleteStaticImport.java";
        var suggestions = filterText(file, 9, 15);

        assertThat("suggests star-imported static method", suggestions, hasItems("emptyList"));
    }

    @Test
    public void staticImport() {
        var file = "/org/javacs/example/AutocompleteStaticImport.java";
        var suggestions = filterText(file, 10, 10);

        assertThat("suggests star-imported static field", suggestions, hasItems("BC"));
    }

    @Test
    public void staticImportSourcePath() {
        var file = "/org/javacs/example/AutocompleteStaticImport.java";
        var suggestions = filterText(file, 11, 10);

        assertThat(
                "suggests star-imported public static field from source path",
                suggestions,
                hasItems("publicStaticFinal"));
        assertThat(
                "suggests star-imported package-private static field from source path",
                suggestions,
                hasItems("packagePrivateStaticFinal"));
    }

    @Test
    public void withinConstructor() {
        var file = "/org/javacs/example/AutocompleteContext.java";
        var suggestions = filterText(file, 8, 38);

        assertThat("suggests local variable", suggestions, hasItems("length"));
    }

    @Test
    @Ignore
    public void onlySuggestOnce() {
        var file = "/org/javacs/example/AutocompleteOnce.java";
        var suggestions = insertCount(file, 5, 18);

        assertThat("suggests Signatures", suggestions, hasKey("Signatures"));
        assertThat("suggests Signatures only once", suggestions, hasEntry("Signatures", 1));
    }

    @Test
    public void overloadedOnSourcePath() {
        var file = "/org/javacs/example/OverloadedMethod.java";
        var labels = label(file, 9, 13);

        assertThat("suggests overloads", labels, hasItem("overloaded() (+2 overloads)"));
    }

    @Test
    public void overloadedOnClassPath() {
        var file = "/org/javacs/example/OverloadedMethod.java";
        var labels = label(file, 10, 26);

        assertThat("suggests overloads", labels, hasItem("of() (+11 overloads)"));
    }

    @Test
    public void packageName() {
        var file = "/org/javacs/example/AutocompletePackageName.java";
        var suggestions = insertText(file, 1, 5);

        assertThat(suggestions, hasItem(startsWith("package org.javacs.example;")));
    }

    @Test
    public void className() {
        var file = "/org/javacs/example/AutocompleteClassName.java";
        var suggestions = filterText(file, 1, 2);

        assertThat(suggestions, hasItem(startsWith("class AutocompleteClassName")));
    }

    @Test
    public void annotationInInnerClass() {
        var file = "/org/javacs/example/AnnotationInInnerClass.java";
        var suggestions = filterText(file, 6, 17);

        assertThat(suggestions, hasItem(startsWith("Override")));
    }

    @Test
    public void overrideMethod() {
        var file = "/org/javacs/example/AutocompleteOverride.java";
        var suggestions = insertText(file, 8, 15);

        assertThat(suggestions, hasItem(containsString("void superMethod() {")));
    }

    @Test
    public void stringBuilderLength() {
        var file = "/org/javacs/example/CompleteStringBuilderLength.java";
        var suggestions = filterText(file, 6, 12);

        assertThat(suggestions, hasItem(containsString("length")));
    }

    @Test
    public void implementsKeyword() {
        var file = "/org/javacs/example/AutocompleteImplements.java";
        var suggestions = filterText(file, 3, 34);

        assertThat(suggestions, hasItem(containsString("implements")));
    }

    @Test
    public void importStaticPackage() {
        var file = "/org/javacs/example/AutocompleteImportStatic.java";
        var suggestions = filterText(file, 3, 20);

        assertThat(suggestions, hasItem(containsString("util")));
    }

    @Test
    public void newlyCreatedClass() throws IOException {
        var file = FindResource.path("/org/javacs/example/NewlyCreatedFile.java");
        try {
            // Create a file that didn't exist when we created the server
            try (var writer = Files.newBufferedWriter(file, StandardOpenOption.CREATE_NEW)) {
                writer.write("package org.javacs.example;\nclass NewlyCreatedFile { }");
            }
            // Send a 'file created' notification
            var created = new FileEvent();
            created.uri = file.toUri();
            created.type = FileChangeType.Created;
            var changes = new DidChangeWatchedFilesParams();
            changes.changes = List.of(created);
            server.didChangeWatchedFiles(changes);
            // Autocomplete `New`
            var suggestions = filterText("/org/javacs/example/AutocompleteNewFile.java", 5, 12);
            assertThat(suggestions, hasItem(containsString("NewlyCreatedFile")));
        } finally {
            Files.delete(file);
            // Send a 'file deleted' notification
            var deleted = new FileEvent();
            deleted.uri = file.toUri();
            deleted.type = FileChangeType.Deleted;
            var changes = new DidChangeWatchedFilesParams();
            changes.changes = List.of(deleted);
            server.didChangeWatchedFiles(changes);
        }
    }

    @Test
    public void completeParens() {
        var inserts = insertText("/org/javacs/example/CompleteParens.java", 5, 12);
        assertThat(inserts, hasItem("returnsVoid()$0"));
        assertThat(inserts, hasItem("returnsString()$0"));
        assertThat(inserts, hasItem("returnsArg($0)"));
    }

    @Test
    public void dontCompleteParens() {
        var inserts = insertText("/org/javacs/example/DontCompleteParens.java", 5, 19);
        assertThat(inserts, hasItem("returnsString"));
    }
}
