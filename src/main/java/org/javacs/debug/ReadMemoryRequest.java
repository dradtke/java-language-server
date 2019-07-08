package org.javacs.debug;

/** ReadMemory request; value of command field is 'readMemory'. Reads bytes from memory at the provided location. */
public class ReadMemoryRequest extends Request {
    ReadMemoryArguments arguments;
}
