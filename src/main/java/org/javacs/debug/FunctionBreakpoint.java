package org.javacs.debug;

/** Properties of a breakpoint passed to the setFunctionBreakpoints request. */
public class FunctionBreakpoint {
    /** The name of the function. */
    String name;
    /** An optional expression for conditional breakpoints. */
    String condition;
    /**
     * An optional expression that controls how many hits of the breakpoint are ignored. The backend is expected to
     * interpret the expression as needed.
     */
    String hitCondition;
}
