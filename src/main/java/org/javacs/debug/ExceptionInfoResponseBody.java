package org.javacs.debug;

public class ExceptionInfoResponseBody {
    /** ID of the exception that was thrown. */
    String exceptionId;
    /** Descriptive text for the exception provided by the debug adapter. */
    String description;
    /**
     * Mode that caused the exception notification to be raised. never: never breaks, always: always breaks, unhandled:
     * breaks when excpetion unhandled, userUnhandled: breaks if the exception is not handled by user code.
     */
    String breakMode;
    /** Detailed information about the exception. */
    ExceptionDetails details;
}
