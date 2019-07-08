package org.javacs.debug;

public class SetExpressionResponseBody {
    /** The new value of the expression. */
    String value;
    /** The optional type of the value. */
    String type;
    /** Properties of a value that can be used to determine how to render the result in the UI. */
    VariablePresentationHint presentationHint;
    /**
     * If variablesReference is > 0, the value is structured and its children can be retrieved by passing
     * variablesReference to the VariablesRequest.
     */
    Integer variablesReference;
    /**
     * The number of named child variables. The client can use this optional information to present the variables in a
     * paged UI and fetch them in chunks.
     */
    Integer namedVariables;
    /**
     * The number of indexed child variables. The client can use this optional information to present the variables in a
     * paged UI and fetch them in chunks.
     */
    Integer indexedVariables;
}
