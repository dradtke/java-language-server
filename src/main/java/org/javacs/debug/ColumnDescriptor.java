package org.javacs.debug;

/**
 * A ColumnDescriptor specifies what module attribute to show in a column of the ModulesView, how to format it, and what
 * the column's label should be. It is only used if the underlying UI actually supports this level of customization.
 */
public class ColumnDescriptor {
    /** Name of the attribute rendered in this column. */
    String attributeName;
    /** Header UI label of column. */
    String label;
    /** Format to use for the rendered values in this column. TBD how the format strings looks like. */
    String format;
    /**
     * Datatype of values in this column. Defaults to 'string' if not specified. 'string' | 'number' | 'boolean' |
     * 'unixTimestampUTC'.
     */
    String type;
    /** Width of this column in characters (hint only). */
    Integer width;
}
