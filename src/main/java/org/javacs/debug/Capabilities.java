package org.javacs.debug;

/** Information about the capabilities of a debug adapter. */
public class Capabilities {
    /** The debug adapter supports the 'configurationDone' request. */
    Boolean supportsConfigurationDoneRequest;
    /** The debug adapter supports function breakpoints. */
    Boolean supportsFunctionBreakpoints;
    /** The debug adapter supports conditional breakpoints. */
    Boolean supportsConditionalBreakpoints;
    /** The debug adapter supports breakpoints that break execution after a specified number of hits. */
    Boolean supportsHitConditionalBreakpoints;
    /** The debug adapter supports a (side effect free) evaluate request for data hovers. */
    Boolean supportsEvaluateForHovers;
    /** Available filters or options for the setExceptionBreakpoints request. */
    ExceptionBreakpointsFilter[] exceptionBreakpointFilters;
    /** The debug adapter supports stepping back via the 'stepBack' and 'reverseContinue' requests. */
    Boolean supportsStepBack;
    /** The debug adapter supports setting a variable to a value. */
    Boolean supportsSetVariable;
    /** The debug adapter supports restarting a frame. */
    Boolean supportsRestartFrame;
    /** The debug adapter supports the 'gotoTargets' request. */
    Boolean supportsGotoTargetsRequest;
    /** The debug adapter supports the 'stepInTargets' request. */
    Boolean supportsStepInTargetsRequest;
    /** The debug adapter supports the 'completions' request. */
    Boolean supportsCompletionsRequest;
    /** The debug adapter supports the 'modules' request. */
    Boolean supportsModulesRequest;
    /** The set of additional module information exposed by the debug adapter. */
    ColumnDescriptor[] additionalModuleColumns;
    /** Checksum algorithms supported by the debug adapter. 'MD5' | 'SHA1' | 'SHA256' | 'timestamp'. */
    String[] supportedChecksumAlgorithms;
    /**
     * The debug adapter supports the 'restart' request. In this case a client should not implement 'restart' by
     * terminating and relaunching the adapter but by calling the RestartRequest.
     */
    Boolean supportsRestartRequest;
    /** The debug adapter supports 'exceptionOptions' on the setExceptionBreakpoints request. */
    Boolean supportsExceptionOptions;
    /**
     * The debug adapter supports a 'format' attribute on the stackTraceRequest, variablesRequest, and evaluateRequest.
     */
    Boolean supportsValueFormattingOptions;
    /** The debug adapter supports the 'exceptionInfo' request. */
    Boolean supportsExceptionInfoRequest;
    /** The debug adapter supports the 'terminateDebuggee' attribute on the 'disconnect' request. */
    Boolean supportTerminateDebuggee;
    /**
     * The debug adapter supports the delayed loading of parts of the stack, which requires that both the 'startFrame'
     * and 'levels' arguments and the 'totalFrames' result of the 'StackTrace' request are supported.
     */
    Boolean supportsDelayedStackTraceLoading;
    /** The debug adapter supports the 'loadedSources' request. */
    Boolean supportsLoadedSourcesRequest;
    /** The debug adapter supports logpoints by interpreting the 'logMessage' attribute of the SourceBreakpoint. */
    Boolean supportsLogPoints;
    /** The debug adapter supports the 'terminateThreads' request. */
    Boolean supportsTerminateThreadsRequest;
    /** The debug adapter supports the 'setExpression' request. */
    Boolean supportsSetExpression;
    /** The debug adapter supports the 'terminate' request. */
    Boolean supportsTerminateRequest;
    /** The debug adapter supports data breakpoints. */
    Boolean supportsDataBreakpoints;
    /** The debug adapter supports the 'readMemory' request. */
    Boolean supportsReadMemoryRequest;
    /** The debug adapter supports the 'disassemble' request. */
    Boolean supportsDisassembleRequest;
}
