package org.javacs;

import com.sun.source.tree.*;
import com.sun.source.util.*;
import java.util.*;
import javax.lang.model.element.*;

class WarnUnused extends TreePathScanner<Void, Void> {
    private final Trees trees;
    // TODO ignore writes when calculating used
    private final Set<Element> declared = new HashSet<>(), used = new HashSet<>();

    WarnUnused(JavacTask task) {
        this.trees = Trees.instance(task);
    }

    Set<Element> notUsed() {
        declared.removeAll(used);
        return declared;
    }

    private void foundDeclaration() {
        var el = trees.getElement(getCurrentPath());
        declared.add(el);
    }

    private void foundReference() {
        var path = getCurrentPath();
        var file = path.getCompilationUnit();
        var el = trees.getElement(path);
        // Check if reference is within declaration
        var declaration = trees.getTree(el);
        var reference = path.getLeaf();
        var pos = trees.getSourcePositions();
        var startD = pos.getStartPosition(file, declaration);
        var endD = pos.getEndPosition(file, declaration);
        var startRef = pos.getStartPosition(file, reference);
        var endRef = pos.getEndPosition(file, reference);
        if (startD < startRef && endRef < endD) {
            return;
        }
        // Otherwise, note that el has been used
        used.add(el);
    }

    private boolean isPrivate(VariableTree t) {
        return t.getModifiers().getFlags().contains(Modifier.PRIVATE);
    }

    private boolean isPrivate(MethodTree t) {
        return t.getModifiers().getFlags().contains(Modifier.PRIVATE);
    }

    private boolean isPrivate(ClassTree t) {
        return t.getModifiers().getFlags().contains(Modifier.PRIVATE);
    }

    private boolean isLocal(VariableTree t) {
        var parent = getCurrentPath().getParentPath().getLeaf();
        return !(parent instanceof ClassTree)
                && !(parent instanceof MethodTree) // TODO hint for unused parameters
                && !(parent instanceof LambdaExpressionTree);
    }

    private boolean isEmptyConstructor(MethodTree t) {
        return t.getParameters().isEmpty() && t.getReturnType() == null;
    }

    @Override
    public Void visitVariable(VariableTree t, Void __) {
        if (isPrivate(t) || isLocal(t)) {
            foundDeclaration();
        }
        return super.visitVariable(t, null);
    }

    @Override
    public Void visitMethod(MethodTree t, Void __) {
        if (isPrivate(t) && !isEmptyConstructor(t)) {
            foundDeclaration();
        }
        return super.visitMethod(t, null);
    }

    @Override
    public Void visitClass(ClassTree t, Void __) {
        if (isPrivate(t)) {
            foundDeclaration();
        }
        return super.visitClass(t, null);
    }

    @Override
    public Void visitIdentifier(IdentifierTree t, Void __) {
        foundReference();
        return super.visitIdentifier(t, null);
    }

    @Override
    public Void visitMemberSelect(MemberSelectTree t, Void __) {
        foundReference();
        return super.visitMemberSelect(t, null);
    }

    @Override
    public Void visitMemberReference(MemberReferenceTree t, Void __) {
        foundReference();
        return super.visitMemberReference(t, null);
    }

    @Override
    public Void visitNewClass(NewClassTree t, Void __) {
        foundReference();
        return super.visitNewClass(t, null);
    }
}
