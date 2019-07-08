package org.javacs.debug;

/** Arguments for 'gotoTargets' request. */
public class GotoTargetsArguments {
    /** The source location for which the goto targets are determined. */
    Source source;
    /** The line location for which the goto targets are determined. */
    int line;
    /** An optional column location for which the goto targets are determined. */
    Integer column;
}
