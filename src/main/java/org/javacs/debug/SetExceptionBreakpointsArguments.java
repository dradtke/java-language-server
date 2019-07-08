package org.javacs.debug;

/** Arguments for 'setExceptionBreakpoints' request. */
public class SetExceptionBreakpointsArguments {
    /** IDs of checked exception options. The set of IDs is returned via the 'exceptionBreakpointFilters' capability. */
    String[] filters;
    /** Configuration options for selected exceptions. */
    ExceptionOptions[] exceptionOptions;
}
