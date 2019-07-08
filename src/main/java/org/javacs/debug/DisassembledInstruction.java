package org.javacs.debug;

/** Represents a single disassembled instruction. */
public class DisassembledInstruction {
    /**
     * The address of the instruction. Treated as a hex value if prefixed with '0x', or as a decimal value otherwise.
     */
    String address;
    /** Optional raw bytes representing the instruction and its operands, in an implementation-defined format. */
    String instructionBytes;
    /** Text representing the instruction and its operands, in an implementation-defined format. */
    String instruction;
    /** Name of the symbol that correponds with the location of this instruction, if any. */
    String symbol;
    /**
     * Source location that corresponds to this instruction, if any. Should always be set (if available) on the first
     * instruction returned, but can be omitted afterwards if this instruction maps to the same source file as the
     * previous instruction.
     */
    Source location;
    /** The line within the source location that corresponds to this instruction, if any. */
    Integer line;
    /** The column within the line that corresponds to this instruction, if any. */
    Integer column;
    /** The end line of the range that corresponds to this instruction, if any. */
    Integer endLine;
    /** The end column of the range that corresponds to this instruction, if any. */
    Integer endColumn;
}
