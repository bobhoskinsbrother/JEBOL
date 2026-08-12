package org.jebol.domain.eval;

import java.util.ArrayDeque;
import java.util.Deque;
import org.jebol.domain.value.Context;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.Molder;
import org.jebol.domain.value.Value;
import org.jebol.domain.value.WordValue;

/**
 * Evaluation tracing, read out of {@code c-do.c}.
 *
 * <p>TRACE turns the evaluator's walk into output. Three hooks do it, and the C
 * puts each one exactly where the thing it reports happens:
 * {@code Trace_Line} before a value is evaluated, {@code Trace_Func} as a call
 * is made, {@code Trace_Return} as one answers. So what a trace shows is what
 * the evaluator is about to do rather than what it did.
 *
 * <p>The output format is Rebol's own, from the {@code trace} block of
 * {@code boot/strings.reb}:
 *
 * <pre>
 * "%-02d: %50r"    the position and the value
 * " : %50r"        what a word holds
 * " : %s %50m"     what a word holds, when it is a function
 * " : %s"          anything else a word holds
 * "--> %s"         a call being made
 * "&lt;-- %s =="       a call answering
 * </pre>
 *
 * <p>Two things about the depth are not guessable. The level is a *limit* and
 * not a switch: `trace 3` shows three levels of nesting and nothing deeper, and
 * `trace on` is the level 100000 rather than a flag of its own. And the
 * indentation stops growing at ten -- {@code if (depth > 10) depth = 10;} --
 * while the cutoff keeps counting, so deep output stays readable without
 * pretending to be shallow.
 *
 * <p>/BACK keeps the lines instead of printing them, so a script can run and
 * then be asked what happened. {@code trace/back 5} prints the last five and
 * turns tracing off: {@code Trace_Flags = 0; Display_Backtrace(Int32(arg));}.
 */
final class Trace {

    /** `Trace_Level = IS_TRUE(arg) ? 100000 : 0;` for a logic. */
    static final int EVERYTHING = 100_000;

    /** `if (depth > 10) depth = 10;` -- the indentation stops, the count does not. */
    private static final int DEEPEST_INDENT = 10;

    private static final int MOLD_LIMIT = 50;

    private final Deque<String> keptLines = new ArrayDeque<>();

    private int level;
    private boolean callsOnly;
    private boolean keepingRatherThanPrinting;
    private int depthWhenTraceBegan;

    /** How many lines /BACK keeps. Rebol's buffer is a ring of this size. */
    private static final int KEPT_LINES = 100;

    private OutputPort output;

    void writeTo(OutputPort port) {
        this.output = port;
    }

    boolean isOn() {
        return level > 0;
    }

    /**
     * Sets the level, as the C does from a logic or a number.
     *
     * <p>{@code Trace_Depth = Eval_Depth() - 1;} is taken at the same moment:
     * the depth TRACE was called at becomes the zero the indentation counts
     * from, minus one for TRACE's own frame. Without that every line would be
     * indented by however deep the caller happened to be.
     */
    void level(int wanted, boolean functionsOnly) {
        this.level = Math.max(0, wanted);
        this.callsOnly = level > 0 && functionsOnly;
        // The block TRACE was called in becomes depth zero. The C takes
        // `Eval_Depth() - 1` for the same reason -- one less for TRACE's own
        // frame -- and the walk is already telling this how deep it is.
        this.depthWhenTraceBegan = depthNow;
        if (level == 0) {
            keepingRatherThanPrinting = false;
        }
    }

    /** `Enable_Backtrace(IS_TRUE(arg))` -- keep the lines rather than print them. */
    void keepRatherThanPrint(boolean keeping) {
        this.keepingRatherThanPrinting = keeping;
        if (!keeping) {
            keptLines.clear();
        }
    }

    /**
     * `Display_Backtrace(lines)` -- the last N kept lines, and tracing stops.
     *
     * <p>The C sets `Trace_Flags = 0` before displaying, so asking for the
     * backtrace is also how tracing is turned off. A caller that wanted both
     * has to ask for the level again afterwards.
     */
    void showTheLast(int lines) {
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

    /**
     * One value, about to be evaluated.
     *
     * <p>`if (GET_FLAG(Trace_Flags, 1)) return; // function` and
     * `if (ANY_FUNC(value)) return;` -- so /FUNCTION silences this hook
     * entirely, and a function value is never reported here because the call
     * hook reports it instead.
     */
    void line(int position, Value value, Context context) {
        if (callsOnly || value.datatype().isAnyFunction()) {
            return;
        }
        int depth = indentFor(0);
        if (depth < 0) {
            return;
        }
        StringBuilder written = new StringBuilder(" ".repeat(3 * depth));
        // "%-02d" in the C is a two-wide left-aligned number. Java's Formatter
        // refuses '-' and '0' together, so the padding is done by hand rather
        // than by changing what the output looks like.
        written.append(String.format("%-2d", position))
                .append(": ")
                .append(molded(value));
        // A word is followed by what it holds, because the word alone says
        // nothing about what is about to happen.
        if (value instanceof WordValue named
                && (named.datatype() == Datatype.WORD
                        || named.datatype() == Datatype.GET_WORD)) {
            written.append(whatTheWordHolds(named, context));
        }
        emit(written.toString());
    }

    /** `Trace_Func`: a call being made, by the name it was made through. */
    void call(String name, Value callee, java.util.List<Value> arguments) {
        int depth = indentFor(0);
        if (depth < 0) {
            return;
        }
        StringBuilder written = new StringBuilder(" ".repeat(3 * depth));
        written.append("--> ").append(name);
        // `if (GET_FLAG(Trace_Flags, 1)) Debug_Values(...)` -- /FUNCTION is the
        // shorter output and yet it is the one that shows the arguments, which
        // is the opposite of what the name suggests.
        if (callsOnly) {
            for (Value argument : arguments) {
                written.append(' ').append(molded(argument));
            }
        }
        emit(written.toString());
    }

    /** `Trace_Return`: a call answering. The depth is one further out. */
    void answered(String name, Value produced) {
        int depth = indentFor(1);
        if (depth < 0) {
            return;
        }
        emit(" ".repeat(3 * depth) + "<-- " + name + " == " + molded(produced));
    }

    /**
     * The indentation for this depth, or -1 when it is past the level.
     *
     * <p>{@code Init_Depth}: `depth = Eval_Depth() - Trace_Depth + plus; if
     * (depth < 0 || depth >= Trace_Level) return -1; if (depth > 10) depth =
     * 10;`. The nesting comes from the evaluator, so this asks it.
     */
    private int indentFor(int plus) {
        int depth = depthNow - depthWhenTraceBegan + plus;
        if (depth < 0 || depth >= level) {
            return -1;
        }
        return Math.min(depth, DEEPEST_INDENT);
    }

    /** How deep the evaluator is, told to this rather than asked for. */
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
        // Three formats, and which one is used depends on what the word holds:
        // a plain value molds, a function names its type and its arguments, and
        // anything else names only its type.
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
