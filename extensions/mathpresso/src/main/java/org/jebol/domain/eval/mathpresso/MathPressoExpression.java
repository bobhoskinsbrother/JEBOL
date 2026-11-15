package org.jebol.domain.eval.mathpresso;

import org.jebol.domain.eval.mathpresso.MathPressoNode.Binary;
import org.jebol.domain.eval.mathpresso.MathPressoNode.Block;
import org.jebol.domain.eval.mathpresso.MathPressoNode.Immediate;
import org.jebol.domain.eval.mathpresso.MathPressoNode.Unary;
import org.jebol.domain.eval.mathpresso.MathPressoNode.Variable;
import org.jebol.domain.eval.mathpresso.MathPressoNode.VariableDeclaration;
import org.jebol.domain.eval.mathpresso.SseDoubles.Predicate;

import java.util.List;

import static org.jebol.domain.eval.mathpresso.SseDoubles.HALF;
import static org.jebol.domain.eval.mathpresso.SseDoubles.MAGNITUDE;
import static org.jebol.domain.eval.mathpresso.SseDoubles.NEGATIVE_INFINITY;
import static org.jebol.domain.eval.mathpresso.SseDoubles.ONE;
import static org.jebol.domain.eval.mathpresso.SseDoubles.QUIET_NAN;
import static org.jebol.domain.eval.mathpresso.SseDoubles.SIGN;
import static org.jebol.domain.eval.mathpresso.SseDoubles.ZERO;
import static org.jebol.domain.eval.mathpresso.SseDoubles.add;
import static org.jebol.domain.eval.mathpresso.SseDoubles.compare;
import static org.jebol.domain.eval.mathpresso.SseDoubles.divide;
import static org.jebol.domain.eval.mathpresso.SseDoubles.flag;
import static org.jebol.domain.eval.mathpresso.SseDoubles.isNaN;
import static org.jebol.domain.eval.mathpresso.SseDoubles.multiply;
import static org.jebol.domain.eval.mathpresso.SseDoubles.subtract;

public final class MathPressoExpression {

    private record Operand(long bits, boolean readOnly) {

        Operand protect() {
            return new Operand(bits, true);
        }

        Operand unprotect() {
            return new Operand(bits, false);
        }
    }

    private final Block program;
    private final int slots;
    private final List<MathPressoSymbol> alteredGlobals;
    private final int variableCount;

    MathPressoExpression(Block program, int slots, List<MathPressoSymbol> rootSymbols, int variableCount) {
        this.program = program;
        this.slots = slots;
        this.variableCount = variableCount;
        markAltered(program);
        this.alteredGlobals = rootSymbols.stream()
                .filter(symbol -> symbol.isGlobal() && symbol.isAltered())
                .toList();
    }

    private static void markAltered(MathPressoNode node) {
        if (node instanceof Binary binary && binary.operator().isAssignment()
                && binary.left() instanceof Variable target) {
            target.symbol().markAltered();
        }
        for (MathPressoNode child : node.children) {
            if (child != null) {
                markAltered(child);
            }
        }
    }

    public int variableCount() {
        return variableCount;
    }

    public long evaluate(long[] variables) {
        Operand[] slotted = new Operand[slots];
        Operand result = evaluateBlock(program, slotted, variables);
        for (MathPressoSymbol symbol : alteredGlobals) {
            variables[symbol.offset()] = slotted[symbol.slot()].bits();
        }
        return result == null ? QUIET_NAN : result.bits();
    }

    private Operand evaluateNode(MathPressoNode node, Operand[] slotted, long[] variables) {
        return switch (node) {
            case Block block -> evaluateBlock(block, slotted, variables);
            case VariableDeclaration declaration -> evaluateDeclaration(declaration, slotted, variables);
            case Variable variable -> readVariable(variable.symbol(), slotted, variables);
            case Immediate immediate -> new Operand(immediate.bits(), false);
            case Unary unary -> evaluateUnary(unary, slotted, variables);
            case Binary binary -> evaluateBinary(binary, slotted, variables);
        };
    }

    private Operand evaluateBlock(Block block, Operand[] slotted, long[] variables) {
        Operand result = null;
        for (int index = 0; index < block.size(); index++) {
            result = evaluateNode(block.childAt(index), slotted, variables);
        }
        return result;
    }

    private Operand evaluateDeclaration(VariableDeclaration declaration, Operand[] slotted, long[] variables) {
        Operand result = declaration.child() == null
                ? null
                : evaluateNode(declaration.child(), slotted, variables).protect();
        slotted[declaration.symbol().slot()] = result;
        return result;
    }

    private Operand readVariable(MathPressoSymbol symbol, Operand[] slotted, long[] variables) {
        Operand held = slotted[symbol.slot()];
        if (held != null) {
            return held;
        }
        if (symbol.isGlobal()) {
            Operand inMemory = new Operand(variables[symbol.offset()], true);
            slotted[symbol.slot()] = inMemory;
            return symbol.writeCount() > 0 ? inMemory.unprotect() : inMemory;
        }
        Operand notANumber = new Operand(QUIET_NAN, false);
        slotted[symbol.slot()] = notANumber;
        return notANumber;
    }

    private Operand evaluateUnary(Unary unary, Operand[] slotted, long[] variables) {
        Operand operand = evaluateNode(unary.child(), slotted, variables);
        if (unary.operator() == MathPressoOperator.NONE) {
            return operand;
        }
        long x = operand.bits();
        long answer = switch (unary.operator()) {
            case NEG -> subtract(ZERO, x);
            case NOT -> compare(x, ZERO, Predicate.EQUAL);
            case IS_NAN -> flag(isNaN(x));
            case IS_INF -> compare(x | SIGN, NEGATIVE_INFINITY, Predicate.EQUAL);
            case IS_FINITE -> compare(x | SIGN, ZERO, Predicate.LESS_OR_EQUAL);
            case SIGN_BIT -> flag(x < 0);
            case ROUND -> Sse2Rounding.halfUp(x);
            case ROUND_EVEN -> Sse2Rounding.nearestEven(x);
            case TRUNC -> Sse2Rounding.truncate(x);
            case FLOOR -> Sse2Rounding.floor(x);
            case CEIL -> Sse2Rounding.ceil(x);
            case ABS -> SseDoubles.maximum(subtract(ZERO, x), x);
            case SQRT -> SseDoubles.squareRoot(x);
            case FRAC -> subtract(x, Sse2Rounding.floor(x));
            case RECIP -> divide(ONE, x);
            default -> LibmCalls.call(unary.operator(), x);
        };
        return new Operand(answer, false);
    }

    private Operand evaluateBinary(Binary binary, Operand[] slotted, long[] variables) {
        MathPressoOperator operator = binary.operator();
        if (operator.isAssignment()) {
            MathPressoSymbol target = ((Variable) binary.left()).symbol();
            Operand assigned = evaluateNode(binary.right(), slotted, variables).protect();
            slotted[target.slot()] = assigned;
            return assigned;
        }
        Operand left;
        Operand right;
        if (binary.left() instanceof Variable first && binary.right() instanceof Variable second
                && first.symbol() == second.symbol()) {
            left = evaluateNode(first, slotted, variables).unprotect();
            if (operator == MathPressoOperator.MOD || operator == MathPressoOperator.COPY_SIGN) {
                return new Operand(evaluateInOneRegister(operator, left.bits()), false);
            }
            right = left;
        } else {
            left = evaluateNode(binary.left(), slotted, variables);
            right = evaluateNode(binary.right(), slotted, variables);
            if (operator.isCommutativeForSwap() && left.readOnly() && !right.readOnly()) {
                Operand swapped = left;
                left = right;
                right = swapped;
            }
        }
        long l = left.bits();
        long r = right.bits();
        long answer = switch (operator) {
            case EQ -> compare(l, r, Predicate.EQUAL);
            case NE -> compare(l, r, Predicate.NOT_EQUAL);
            case GT -> compare(l, r, Predicate.NOT_LESS_OR_EQUAL);
            case GE -> compare(l, r, Predicate.NOT_LESS);
            case LT -> compare(l, r, Predicate.LESS);
            case LE -> compare(l, r, Predicate.LESS_OR_EQUAL);
            case ADD -> add(l, r);
            case SUB -> subtract(l, r);
            case MUL -> multiply(l, r);
            case DIV -> divide(l, r);
            case MIN -> SseDoubles.minimum(l, r);
            case MAX -> SseDoubles.maximum(l, r);
            case AVG -> multiply(add(l, r), HALF);
            case MOD -> subtract(l, multiply(Sse2Rounding.truncate(divide(l, r)), r));
            case COPY_SIGN -> (l & MAGNITUDE) | (r & SIGN);
            default -> LibmCalls.call(operator, l, r);
        };
        return new Operand(answer, false);
    }

    private static long evaluateInOneRegister(MathPressoOperator operator, long x) {
        if (operator == MathPressoOperator.COPY_SIGN) {
            return ZERO;
        }
        long truncated = Sse2Rounding.truncate(divide(x, x));
        return subtract(x, multiply(truncated, truncated));
    }
}
