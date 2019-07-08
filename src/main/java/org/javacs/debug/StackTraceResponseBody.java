package org.javacs.debug;

public class StackTraceResponseBody {
    /**
     * The frames of the stackframe. If the array has length zero, there are no stackframes available. This means that
     * there is no location information available.
     */
    StackFrame[] stackFrames;
    /** The total number of frames available. */
    Integer totalFrames;
}
