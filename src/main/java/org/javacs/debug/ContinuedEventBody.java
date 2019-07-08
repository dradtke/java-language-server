package org.javacs.debug;

public class ContinuedEventBody {
    /** The thread which was continued. */
    public int threadId;
    /** If 'allThreadsContinued' is true, a debug adapter can announce that all threads have continued. */
    public Boolean allThreadsContinued;
}
