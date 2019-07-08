package org.javacs.debug;

/** Arguments for 'initialize' request. */
public class InitializeRequestArguments {
    /** The ID of the (frontend) client using this adapter. */
    String clientID;
    /** The human readable name of the (frontend) client using this adapter. */
    String clientName;
    /** The ID of the debug adapter. */
    String adapterID;
    /** The ISO-639 locale of the (frontend) client using this adapter, e.g. en-US or de-CH. */
    String locale;
    /** If true all line numbers are 1-based (default). */
    Boolean linesStartAt1;
    /** If true all column numbers are 1-based (default). */
    Boolean columnsStartAt1;
    /**
     * Determines in what format paths are specified. The default is 'path', which is the native format. Values: 'path',
     * 'uri', etc.
     */
    String pathFormat;
    /** Client supports the optional type attribute for variables. */
    Boolean supportsVariableType;
    /** Client supports the paging of variables. */
    Boolean supportsVariablePaging;
    /** Client supports the runInTerminal request. */
    Boolean supportsRunInTerminalRequest;
    /** Client supports memory references. */
    Boolean supportsMemoryReferences;
}
