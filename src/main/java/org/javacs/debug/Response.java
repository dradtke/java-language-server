package org.javacs.debug;

/** Response for a request. */
public class Response extends ProtocolMessage {
    // type: 'response';
    /** Sequence number of the corresponding request. */
    int request_seq;
    /** Outcome of the request. */
    boolean success;
    /** The command requested. */
    String command;
    /** Contains error message if success == false. */
    String message;
}
