package org.javacs.debug;

/** A Scope is a named container for variables. Optionally a scope can map to a source or a range within a source. */
public class Scope {
    /**
     * Name of the scope such as 'Arguments', 'Locals', or 'Registers'. This string is shown in the UI as is and can be
     * translated.
     */
    String name;
    /**
     * An optional hint for how to present this scope in the UI. If this attribute is missing, the scope is shown with a
     * generic UI. Values: 'arguments': Scope contains method arguments. 'locals': Scope contains local variables.
     * 'registers': Scope contains registers. Only a single 'registers' scope should be returned from a 'scopes'
     * request. etc.
     */
    String presentationHint;
    /**
     * The variables of this scope can be retrieved by passing the value of variablesReference to the VariablesRequest.
     */
    int variablesReference;
    /**
     * The number of named variables in this scope. The client can use this optional information to present the
     * variables in a paged UI and fetch them in chunks.
     */
    Integer namedVariables;
    /**
     * The number of indexed variables in this scope. The client can use this optional information to present the
     * variables in a paged UI and fetch them in chunks.
     */
    Integer indexedVariables;
    /** If true, the number of variables in this scope is large or expensive to retrieve. */
    boolean expensive;
    /** Optional source for this scope. */
    Source source;
    /** Optional start line of the range covered by this scope. */
    Integer line;
    /** Optional start column of the range covered by this scope. */
    Integer column;
    /** Optional end line of the range covered by this scope. */
    Integer endLine;
    /** Optional end column of the range covered by this scope. */
    Integer endColumn;
}
