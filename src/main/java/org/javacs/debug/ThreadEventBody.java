package org.javacs.debug;

public class ThreadEventBody {
    /** The reason for the event. Values: 'started', 'exited', etc. */
    String reason;
    /** The identifier of the thread. */
    int threadId;
}
