package org.javacs.debug;

/** A Stackframe contains the source location. */
public class StackFrame {
    /**
     * An identifier for the stack frame. It must be unique across all threads. This id can be used to retrieve the
     * scopes of the frame with the 'scopesRequest' or to restart the execution of a stackframe.
     */
    int id;
    /** The name of the stack frame, typically a method name. */
    String name;
    /** The optional source of the frame. */
    Source source;
    /** The line within the file of the frame. If source is null or doesn't exist, line is 0 and must be ignored. */
    int line;
    /** The column within the line. If source is null or doesn't exist, column is 0 and must be ignored. */
    int column;
    /** An optional end line of the range covered by the stack frame. */
    Integer endLine;
    /** An optional end column of the range covered by the stack frame. */
    Integer endColumn;
    /** Optional memory reference for the current instruction pointer in this frame. */
    String instructionPointerReference;
    /** The module associated with this frame, if any. */
    String moduleId;
    /**
     * An optional hint for how to present this frame in the UI. A value of 'label' can be used to indicate that the
     * frame is an artificial frame that is used as a visual label or separator. A value of 'subtle' can be used to
     * change the appearance of a frame in a 'subtle' way. 'normal' | 'label' | 'subtle'.
     */
    String presentationHint;
}
