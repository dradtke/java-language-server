package org.javacs.debug;

/** A debug adapter initiated event. */
public class Event extends ProtocolMessage {
    // type: 'event';
    /** Type of event. */
    public String event;
}
