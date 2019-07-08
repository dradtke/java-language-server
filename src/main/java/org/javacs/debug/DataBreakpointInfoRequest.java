package org.javacs.debug;

/**
 * DataBreakpointInfo request; value of command field is 'dataBreakpointInfo'. Obtains information on a possible data
 * breakpoint that could be set on an expression or variable.
 */
public class DataBreakpointInfoRequest extends Request {
    DataBreakpointInfoArguments arguments;
}
