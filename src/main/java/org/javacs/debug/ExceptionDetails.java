package org.javacs.debug;

/** Detailed information about an exception that has occurred. */
public class ExceptionDetails {
    /** Message contained in the exception. */
    String message;
    /** Short type name of the exception object. */
    String typeName;
    /** Fully-qualified type name of the exception object. */
    String fullTypeName;
    /** Optional expression that can be evaluated in the current scope to obtain the exception object. */
    String evaluateName;
    /** Stack trace at the time the exception was thrown. */
    String stackTrace;
    /** Details of the exception contained by this exception, if any. */
    ExceptionDetails[] innerException;
}
