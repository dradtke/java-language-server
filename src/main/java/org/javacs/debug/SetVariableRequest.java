package org.javacs.debug;

/**
 * SetVariable request; value of command field is 'setVariable'. Set the variable with the given name in the variable
 * container to a new value.
 */
public class SetVariableRequest extends Request {
    SetVariableArguments arguments;
}
