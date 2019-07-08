package org.javacs.debug;

/** Arguments for 'setDataBreakpoints' request. */
public class SetDataBreakpointsArguments {
    /**
     * The contents of this array replaces all existing data breakpoints. An empty array clears all data breakpoints.
     */
    public DataBreakpoint[] breakpoints;
}
