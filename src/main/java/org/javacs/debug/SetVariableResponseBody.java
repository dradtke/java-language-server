package org.javacs.debug;

public class SetVariableResponseBody {
    /** The new value of the variable. */
    String value;
    /** The type of the new value. Typically shown in the UI when hovering over the value. */
    String type;
    /**
     * If variablesReference is > 0, the new value is structured and its children can be retrieved by passing
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
