package org.javacs.debug;

/** Response to 'initialize' request. */
public class InitializeResponse extends Response {
    /** The capabilities of this debug adapter. */
    public Capabilities body;
}
