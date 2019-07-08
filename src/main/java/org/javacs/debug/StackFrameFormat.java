package org.javacs.debug;

/** Provides formatting information for a stack frame. */
public class StackFrameFormat extends ValueFormat {
    /** Displays parameters for the stack frame. */
    Boolean parameters;
    /** Displays the types of parameters for the stack frame. */
    Boolean parameterTypes;
    /** Displays the names of parameters for the stack frame. */
    Boolean parameterNames;
    /** Displays the values of parameters for the stack frame. */
    Boolean parameterValues;
    /** Displays the line number of the stack frame. */
    Boolean line;
    /** Displays the module of the stack frame. */
    Boolean module;
    /** Includes all stack frames, including those the debug adapter might otherwise hide. */
    Boolean includeAll;
}
