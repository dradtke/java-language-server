package org.javacs.debug;

/** Arguments for 'modules' request. */
public class ModulesArguments {
    /** The index of the first module to return; if omitted modules start at 0. */
    Integer startModule;
    /** The number of modules to return. If moduleCount is not specified or 0, all modules are returned. */
    Integer moduleCount;
}
