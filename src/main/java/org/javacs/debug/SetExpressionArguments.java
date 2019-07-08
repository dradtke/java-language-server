package org.javacs.debug;

/** Arguments for 'setExpression' request. */
public class SetExpressionArguments {
    /** The l-value expression to assign to. */
    String expression;
    /** The value expression to assign to the l-value expression. */
    String value;
    /**
     * Evaluate the expressions in the scope of this stack frame. If not specified, the expressions are evaluated in the
     * global scope.
     */
    Integer frameId;
    /** Specifies how the resulting value should be formatted. */
    ValueFormat format;
}
