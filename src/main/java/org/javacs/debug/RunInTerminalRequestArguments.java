package org.javacs.debug;

import java.util.Map;

/** Arguments for 'runInTerminal' request. */
public class RunInTerminalRequestArguments {
    /** What kind of terminal to launch. 'integrated' | 'external'. */
    String kind;
    /** Optional title of the terminal. */
    String title;
    /** Working directory of the command. */
    String cwd;
    /** List of arguments. The first argument is the command to run. */
    String[] args;
    /** Environment key-value pairs that are added to or removed from the default environment. */
    Map<String, String> env;
}
