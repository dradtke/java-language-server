package org.javacs.debug;

/** Arguments for 'setVariable' request. */
public class SetVariableArguments {
    /** The reference of the variable container. */
    int variablesReference;
    /** The name of the variable in the container. */
    String name;
    /** The value of the variable. */
    String value;
    /** Specifies details on how to format the response value. */
    ValueFormat format;
}
