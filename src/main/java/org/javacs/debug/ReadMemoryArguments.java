package org.javacs.debug;

/** Arguments for 'readMemory' request. */
public class ReadMemoryArguments {
    /** Memory reference to the base location from which data should be read. */
    String memoryReference;
    /** Optional offset (in bytes) to be applied to the reference location before reading data. Can be negative. */
    Integer offset;
    /** Number of bytes to read at the specified location and offset. */
    int count;
}
