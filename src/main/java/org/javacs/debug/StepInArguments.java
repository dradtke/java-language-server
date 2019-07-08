package org.javacs.debug;

/** Arguments for 'stepIn' request. */
public class StepInArguments {
    /** Execute 'stepIn' for this thread. */
    int threadId;
    /** Optional id of the target to step into. */
    Integer targetId;
}
