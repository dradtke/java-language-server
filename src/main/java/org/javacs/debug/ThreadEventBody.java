package org.javacs.debug;

public class ThreadEventBody {
    /** The reason for the event. Values: 'started', 'exited', etc. */
    public String reason;
    /** The identifier of the thread. */
    public int threadId;
}
