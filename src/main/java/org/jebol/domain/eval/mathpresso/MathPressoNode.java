package org.jebol.domain.eval.mathpresso;

import java.util.ArrayList;
import java.util.List;

abstract sealed class MathPressoNode {

    private MathPressoNode parent;
    private int position;
    final List<MathPressoNode> children;

    MathPressoNode(int slots) {
        children = new ArrayList<>(slots);
        for (int slot = 0; slot < slots; slot++) {
            children.add(null);
        }
    }

    MathPressoNode parent() {
        return parent;
    }

    boolean hasParent() {
        return parent != null;
    }

    int position() {
        return position;
    }

    void setPosition(int position) {
        this.position = position;
    }

    MathPressoNode childAt(int index) {
        return children.get(index);
    }

    void setChildAt(int index, MathPressoNode node) {
        children.set(index, node);
        if (node != null) {
            node.parent = this;
        }
    }

    MathPressoNode unlinkChildAt(int index) {
        MathPressoNode child = children.get(index);
        children.set(index, null);
        if (child != null) {
            child.parent = null;
        }
        return child;
    }

    void replaceNode(MathPressoNode reference, MathPressoNode replacement) {
        for (int index = 0; index < children.size(); index++) {
            if (children.get(index) == reference) {
                children.set(index, replacement);
                reference.parent = null;
                if (replacement != null) {
                    replacement.parent = this;
                }
                return;
            }
        }
    }

    boolean isImmediate() {
        return this instanceof Immediate;
    }

    static final class Block extends MathPressoNode {

        private final boolean program;

        Block(boolean program) {
            super(0);
            this.program = program;
        }

        boolean isProgram() {
            return program;
        }

        int size() {
            return children.size();
        }

        void append(MathPressoNode node) {
            children.add(node);
            node.parent = this;
        }

        void removeAt(int index) {
            children.remove(index).parent = null;
        }
    }

    static final class VariableDeclaration extends MathPressoNode {

        private final MathPressoSymbol symbol;

        VariableDeclaration(MathPressoSymbol symbol) {
            super(1);
            this.symbol = symbol;
        }

        MathPressoSymbol symbol() {
            return symbol;
        }

        MathPressoNode child() {
            return childAt(0);
        }
    }

    static final class Variable extends MathPressoNode {

        private final MathPressoSymbol symbol;
        private boolean assignedTo;

        Variable(MathPressoSymbol symbol) {
            super(0);
            this.symbol = symbol;
        }

        MathPressoSymbol symbol() {
            return symbol;
        }

        boolean isAssignedTo() {
            return assignedTo;
        }

        void markAssignedTo() {
            assignedTo = true;
        }
    }

    static final class Immediate extends MathPressoNode {

        private long bits;

        Immediate(long bits) {
            super(0);
            this.bits = bits;
        }

        long bits() {
            return bits;
        }

        void setBits(long bits) {
            this.bits = bits;
        }
    }

    static final class Unary extends MathPressoNode {

        private final MathPressoOperator operator;

        Unary(MathPressoOperator operator) {
            super(1);
            this.operator = operator;
        }

        MathPressoOperator operator() {
            return operator;
        }

        MathPressoNode child() {
            return childAt(0);
        }
    }

    static final class Binary extends MathPressoNode {

        private final MathPressoOperator operator;

        Binary(MathPressoOperator operator) {
            super(2);
            this.operator = operator;
        }

        MathPressoOperator operator() {
            return operator;
        }

        MathPressoNode left() {
            return childAt(0);
        }

        MathPressoNode right() {
            return childAt(1);
        }
    }
}
