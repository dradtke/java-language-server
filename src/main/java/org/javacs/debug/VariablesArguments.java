package org.javacs.debug;

/** Arguments for 'variables' request. */
public class VariablesArguments {
    /** The Variable reference. */
    int variablesReference;
    /**
     * Optional filter to limit the child variables to either named or indexed. If ommited, both types are fetched.
     * 'indexed' | 'named'.
     */
    String filter;
    /** The index of the first variable to return; if omitted children start at 0. */
    Integer start;
    /** The number of variables to return. If count is missing or 0, all variables are returned. */
    Integer count;
    /** Specifies details on how to format the Variable values. */
    ValueFormat format;
}
