package org.jebol.domain.eval;

import org.jebol.domain.value.*;

import java.util.ArrayDeque;
import java.util.Deque;

final class Trace {

    static final int EVERYTHING = 100_000;

    private static final int DEEPEST_INDENT = 10;

    private static final int MOLD_LIMIT = 50;

    private final Deque<String> keptLines = new ArrayDeque<>();

    private int level;
    private boolean callsOnly;
    private boolean keepingRatherThanPrinting;
    private int depthWhenTraceBegan;

    private static final int KEPT_LINES = 100;

    private OutputPort output;

    void writeTo(OutputPort port) {
        this.output = port;
    }

    boolean isOn() {
        return level > 0;
    }

    void level(int wanted, boolean functionsOnly) {
        this.level = Math.max(0, wanted);
        this.callsOnly = level > 0 && functionsOnly;
        this.depthWhenTraceBegan = theZeroTheIndentationCountsFrom();
        if (level == 0) {
            keepingRatherThanPrinting = false;
        }
    }

    private int theZeroTheIndentationCountsFrom() {
        return depthNow;
    }

    void keepRatherThanPrint(boolean keeping) {
        this.keepingRatherThanPrinting = keeping;
        if (!keeping) {
            keptLines.clear();
        }
    }

    void showTheLastAndStopTracing(int lines) {
        level = 0;
        callsOnly = false;
        if (output == null) {
            return;
        }
        int skip = Math.max(0, keptLines.size() - lines);
        int at = 0;
        for (String line : keptLines) {
            if (at++ >= skip) {
                output.write(line + System.lineSeparator());
            }
        }
    }

    void line(int position, Value value, Context context) {
        if (callsOnly || value.datatype().isAnyFunction()) {
            return;
        }
        int depth = indentFor(AT_THIS_DEPTH);
        if (depth < 0) {
            return;
        }
        StringBuilder written = new StringBuilder(" ".repeat(3 * depth));
        written.append(String.format("%-2d", position))
                .append(": ")
                .append(molded(value));
        if (value instanceof WordValue named
                && (named.datatype() == Datatype.WORD
                        || named.datatype() == Datatype.GET_WORD)) {
            written.append(whatTheWordHolds(named, context));
        }
        emit(written.toString());
    }

    void call(String name, Value callee, java.util.List<Value> arguments) {
        int depth = indentFor(AT_THIS_DEPTH);
        if (depth < 0) {
            return;
        }
        StringBuilder written = new StringBuilder(" ".repeat(3 * depth));
        written.append("--> ").append(name);
        if (callsOnly) {
            for (Value argument : arguments) {
                written.append(' ').append(molded(argument));
            }
        }
        emit(written.toString());
    }

    void answered(String name, Value produced) {
        int depth = indentFor(ONE_FURTHER_OUT);
        if (depth < 0) {
            return;
        }
        emit(" ".repeat(3 * depth) + "<-- " + name + " == " + molded(produced));
    }

    private static final int AT_THIS_DEPTH = 0;

    private static final int ONE_FURTHER_OUT = 1;

    private static final int PAST_THE_LEVEL = -1;

    private int indentFor(int plus) {
        int depth = depthNow - depthWhenTraceBegan + plus;
        if (depth < 0 || depth >= level) {
            return PAST_THE_LEVEL;
        }
        return Math.min(depth, DEEPEST_INDENT);
    }

    private int depthNow;

    void nowAtDepth(int depth) {
        this.depthNow = depth;
    }

    private String whatTheWordHolds(WordValue named, Context context) {
        if (!named.isBound() && !context.knows(named.canonical())) {
            return "";
        }
        Context holder = named.isBound() ? named.binding() : context;
        if (!holder.knows(named.canonical())) {
            return "";
        }
        Value held = holder.slotFor(named.canonical()).value();
        if (held.datatype().isAnyFunction()) {
            return " : " + held.datatype().literalSpelling() + " " + molded(held);
        }
        return " : " + molded(held);
    }

    private String molded(Value value) {
        String written = Molder.mold(value);
        return written.length() <= MOLD_LIMIT
                ? written
                : written.substring(0, MOLD_LIMIT);
    }

    private void emit(String line) {
        if (keepingRatherThanPrinting) {
            keptLines.addLast(line);
            while (keptLines.size() > KEPT_LINES) {
                keptLines.removeFirst();
            }
            return;
        }
        if (output != null) {
            output.write(line + System.lineSeparator());
        }
    }
}
