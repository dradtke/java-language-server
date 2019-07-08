package org.javacs.debug;

/** Properties of a breakpoint or logpoint passed to the setBreakpoints request. */
public class SourceBreakpoint {
    /** The source line of the breakpoint or logpoint. */
    int line;
    /** An optional source column of the breakpoint. */
    Integer column;
    /** An optional expression for conditional breakpoints. */
    String condition;
    /**
     * An optional expression that controls how many hits of the breakpoint are ignored. The backend is expected to
     * interpret the expression as needed.
     */
    String hitCondition;
    /**
     * If this attribute exists and is non-empty, the backend must not 'break' (stop) but log the message instead.
     * Expressions within {} are interpolated.
     */
    String logMessage;
}
