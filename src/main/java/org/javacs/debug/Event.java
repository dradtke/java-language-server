package org.javacs.debug;

import com.google.gson.JsonObject;

/** A debug adapter initiated event. */
public class Event extends ProtocolMessage {
    // type: 'event';
    /** Type of event. */
    public String event;
    /** Event-specific information. */
    public JsonObject body;
}
