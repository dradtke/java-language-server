package org.javacs.debug;

public class ModuleEventBody {
    /** The reason for the event. 'new' | 'changed' | 'removed'. */
    String reason;
    /** The new, changed, or removed module. In case of 'removed' only the module id is used. */
    Module module;
}
