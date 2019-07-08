package org.javacs.debug;

/**
 * Scopes request; value of command field is 'scopes'. The request returns the variable scopes for a given stackframe
 * ID.
 */
public class ScopesRequest extends Request {
    public ScopesArguments arguments;
}
