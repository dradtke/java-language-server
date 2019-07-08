package org.javacs.debug;

import com.google.gson.JsonObject;

public class OutputEventBody {
    /**
     * The output category. If not specified, 'console' is assumed. Values: 'console', 'stdout', 'stderr', 'telemetry',
     * etc.
     */
    String category;
    /** The output to report. */
    String output;
    /**
     * If an attribute 'variablesReference' exists and its value is > 0, the output contains objects which can be
     * retrieved by passing 'variablesReference' to the 'variables' request.
     */
    Integer variablesReference;
    /** An optional source location where the output was produced. */
    Source source;
    /** An optional source location line where the output was produced. */
    Integer line;
    /** An optional source location column where the output was produced. */
    Integer column;
    /**
     * Optional data to report. For the 'telemetry' category the data will be sent to telemetry, for the other
     * categories the data is shown in JSON format.
     */
    JsonObject data;
}
