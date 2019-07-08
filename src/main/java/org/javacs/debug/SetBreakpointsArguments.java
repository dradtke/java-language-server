package org.javacs.debug;

/** Arguments for 'setBreakpoints' request. */
public class SetBreakpointsArguments {
    /** The source location of the breakpoints; either 'source.path' or 'source.reference' must be specified. */
    Source source;
    /** The code locations of the breakpoints. */
    SourceBreakpoint[] breakpoints;
    /**
     * A value of true indicates that the underlying source has been modified which results in new breakpoint locations.
     */
    Boolean sourceModified;
}
