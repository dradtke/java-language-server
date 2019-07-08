package org.javacs.debug;

/** Arguments for 'completions' request. */
public class CompletionsArguments {
    /**
     * Returns completions in the scope of this stack frame. If not specified, the completions are returned for the
     * global scope.
     */
    Integer frameId;
    /**
     * One or more source lines. Typically this is the text a user has typed into the debug console before he asked for
     * completion.
     */
    String text;
    /** The character position for which to determine the completion proposals. */
    int column;
    /**
     * An optional line for which to determine the completion proposals. If missing the first line of the text is
     * assumed.
     */
    Integer line;
}
