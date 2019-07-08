package org.javacs.debug;

/** The checksum of an item calculated by the specified algorithm. */
public class Checksum {
    /** The algorithm used to calculate this checksum. 'MD5' | 'SHA1' | 'SHA256' | 'timestamp'. */
    String algorithm;
    /** Value of the checksum. */
    String checksum;
}
