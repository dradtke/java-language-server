package org.javacs.debug;

import com.google.gson.JsonObject;

/** A client or debug adapter initiated request. */
public class Request extends ProtocolMessage {
    // type: 'request';
    /** The command to execute. */
    String command;
    /** Object containing arguments for the command. */
    JsonObject arguments;
}
