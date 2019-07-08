package org.javacs.debug;

public class ContinuedEventBody {
    /** The thread which was continued. */
    int threadId;
    /** If 'allThreadsContinued' is true, a debug adapter can announce that all threads have continued. */
    Boolean allThreadsContinued;
}
