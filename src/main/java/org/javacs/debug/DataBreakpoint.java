package org.javacs.debug;

/** Properties of a data breakpoint passed to the setDataBreakpoints request. */
public class DataBreakpoint {
    /** An id representing the data. This id is returned from the dataBreakpointInfo request. */
    String dataId;
    /** The access type of the data. 'read' | 'write' | 'readWrite'. */
    String accessType;
    /** An optional expression for conditional breakpoints. */
    String condition;
    /**
     * An optional expression that controls how many hits of the breakpoint are ignored. The backend is expected to
     * interpret the expression as needed.
     */
    String hitCondition;
}
