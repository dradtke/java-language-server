package org.javacs.debug;

public class LoadedSourceEventBody {
    /** The reason for the event. 'new' | 'changed' | 'removed'. */
    String reason;
    /** The new, changed, or removed source. */
    Source source;
}
