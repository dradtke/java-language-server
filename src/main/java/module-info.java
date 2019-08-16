open module javacs {
    requires jdk.compiler;
    requires jdk.zipfs;
    requires jdk.jdi;
    requires java.logging;
    requires java.xml;
    requires gson;
    requires gradle.tooling.api;

    uses javax.tools.JavaCompiler;
}
