package org.javacs.debug;

/**
 * SetExceptionBreakpoints request; value of command field is 'setExceptionBreakpoints'. The request configures the
 * debuggers response to thrown exceptions. If an exception is configured to break, a 'stopped' event is fired (with
 * reason 'exception').
 */
public class SetExceptionBreakpointsRequest extends Request {
    SetExceptionBreakpointsArguments arguments;
}
