package org.javacs.debug;

/**
 * Source request; value of command field is 'source'. The request retrieves the source code for a given source
 * reference.
 */
public class SourceRequest extends Request {
    SourceArguments arguments;
}
