package org.javacs.debug;

/**
 * Evaluate request; value of command field is 'evaluate'. Evaluates the given expression in the context of the top most
 * stack frame. The expression has access to any variables and arguments that are in scope.
 */
public class EvaluateRequest extends Request {
    EvaluateArguments arguments;
}
