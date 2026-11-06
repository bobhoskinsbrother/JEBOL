package org.jebol.domain.eval.mathpresso;

import org.jebol.domain.eval.mathpresso.MathPressoNode.Binary;
import org.jebol.domain.eval.mathpresso.MathPressoNode.Block;
import org.jebol.domain.eval.mathpresso.MathPressoNode.Immediate;
import org.jebol.domain.eval.mathpresso.MathPressoNode.Unary;
import org.jebol.domain.eval.mathpresso.MathPressoNode.Variable;
import org.jebol.domain.eval.mathpresso.MathPressoNode.VariableDeclaration;

final class MathPressoOptimizer {

    private final MathPressoReporter reporter;

    MathPressoOptimizer(MathPressoReporter reporter) {
        this.reporter = reporter;
    }

    void optimize(MathPressoNode node) {
        switch (node) {
            case Block block -> optimizeBlock(block);
            case VariableDeclaration declaration -> optimizeDeclaration(declaration);
            case Variable variable -> optimizeVariable(variable);
            case Immediate ignored -> {
            }
            case Unary unary -> optimizeUnary(unary);
            case Binary binary -> optimizeBinary(binary);
        }
    }

    private void optimizeBlock(Block block) {
        boolean alterable = !block.isProgram();
        int index = 0;
        while (index < block.size()) {
            optimize(block.childAt(index));
            if (alterable && block.childAt(index).isImmediate()) {
                block.removeAt(index);
                continue;
            }
            index++;
        }
    }

    private void optimizeDeclaration(VariableDeclaration declaration) {
        if (declaration.child() != null) {
            optimize(declaration.child());
            if (declaration.child() instanceof Immediate value) {
                declaration.symbol().setValueBits(value.bits());
            }
        }
    }

    private void optimizeVariable(Variable variable) {
        MathPressoSymbol symbol = variable.symbol();
        if (symbol.isAssigned() && !variable.isAssignedTo()) {
            variable.parent().replaceNode(variable, new Immediate(symbol.valueBits()));
        }
    }

    private void optimizeUnary(Unary unary) {
        optimize(unary.child());
        MathPressoNode child = unary.child();
        if (child instanceof Immediate value) {
            long folded = CompiledFolding.foldUnary(unary.operator(), value.bits())
                    .orElseThrow(() -> reporter.refuse(
                            "Invalid unary operation '" + unary.operator().spelling() + "'."));
            value.setBits(folded);
            unary.unlinkChildAt(0);
            unary.parent().replaceNode(unary, value);
        } else if (child instanceof Unary inner && inner.operator() == unary.operator()
                && unary.operator() == MathPressoOperator.NEG) {
            unary.parent().replaceNode(unary, inner.unlinkChildAt(0));
        }
    }

    private void optimizeBinary(Binary binary) {
        MathPressoOperator operator = binary.operator();
        if (operator.isAssignment() && binary.left() instanceof Variable target) {
            target.markAssignedTo();
        }
        optimize(binary.left());
        optimize(binary.right());
        MathPressoNode left = binary.left();
        MathPressoNode right = binary.right();
        if (left instanceof Immediate leftValue && right instanceof Immediate rightValue) {
            long folded = CompiledFolding.foldBinary(operator, leftValue.bits(), rightValue.bits())
                    .orElseThrow(() -> reporter.refuse(
                            "Invalid binary operation '" + operator.spelling() + "'."));
            leftValue.setBits(folded);
            binary.unlinkChildAt(0);
            binary.parent().replaceNode(binary, leftValue);
        } else if (left instanceof Immediate leftValue) {
            if (operator.isNeutralOnLeft(SseDoubles.asDouble(leftValue.bits()))) {
                binary.unlinkChildAt(1);
                binary.parent().replaceNode(binary, right);
            }
        } else if (right instanceof Immediate rightValue) {
            if (operator.isAssignment() && left instanceof Variable target) {
                target.symbol().setValueBits(rightValue.bits());
                target.symbol().markAssigned();
            } else if (operator.isNeutralOnRight(SseDoubles.asDouble(rightValue.bits()))) {
                binary.unlinkChildAt(0);
                binary.parent().replaceNode(binary, left);
            }
        }
    }
}
