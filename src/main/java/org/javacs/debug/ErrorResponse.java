package org.javacs.debug;

/** On error (whenever 'success' is false), the body can provide more details. */
public class ErrorResponse extends Response {
    ErrorResponseBody Body;
}
