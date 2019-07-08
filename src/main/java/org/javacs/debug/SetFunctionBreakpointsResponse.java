package org.javacs.debug;

/**
 * Response to 'setFunctionBreakpoints' request. Returned is information about each breakpoint created by this request.
 */
public class SetFunctionBreakpointsResponse extends Response {
    SetFunctionBreakpointsResponseBody body;
}
