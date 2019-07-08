package org.javacs.debug;

/** Arguments for 'goto' request. */
public class GotoArguments {
    /** Set the goto target for this thread. */
    public int threadId;
    /** The location where the debuggee will continue to run. */
    public int targetId;
}
