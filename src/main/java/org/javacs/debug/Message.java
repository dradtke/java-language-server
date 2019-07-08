package org.javacs.debug;

import java.util.Map;

/** A structured message object. Used to return errors from requests. */
public class Message {
    /** Unique identifier for the message. */
    int id;
    /**
     * A format string for the message. Embedded variables have the form '{name}'. If variable name starts with an
     * underscore character, the variable does not contain user data (PII) and can be safely used for telemetry
     * purposes.
     */
    String format;
    /** An object used as a dictionary for looking up the variables in the format string. */
    Map<String, String> variables;
    /** If true send to telemetry. */
    Boolean sendTelemetry;
    /** If true show user. */
    Boolean showUser;
    /** An optional url where additional information about this message can be found. */
    String url;
    /** An optional label that is presented to the user as the UI for opening the url. */
    String urlLabel;
}
