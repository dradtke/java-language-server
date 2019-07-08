package org.javacs.debug;

public /** Base class of requests, responses, and events. */
public class ProtocolMessage {
    /** Sequence number. */
    int seq;
    /** Message type. Values: 'request', 'response', 'event', etc. */
    String type;
}
