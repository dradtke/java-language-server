package org.javacs.warn;

import java.util.function.Consumer;

class Unused {
    void test(int unusedParam) {
        int unusedLocal = 1;
    }

    private int unusedPrivate;

    Consumer<Integer> lambda = unusedLambdaParam -> {
        int unusedLocalInLambda;
    };

    private int unusedMethod() {
        return 0;
    }

    private Unused() { }

    private Unused(int i) { }

    private class UnusedClass { }
}