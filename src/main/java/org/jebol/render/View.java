package org.jebol.render;

import org.jebol.application.Conclusion;
import org.jebol.application.Interpreter;
import org.jebol.application.ScriptOutcome;
import org.jebol.domain.render.Face;
import org.jebol.domain.render.Html;
import org.jebol.domain.render.Layout;
import org.jebol.domain.value.*;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A layout that stays on the server and answers events.
 *
 * <p>REBOL's event model was built for a local window, where the block that
 * runs when a button is pressed sits in the same process as the button. A
 * browser is not in the same process, so the shape here is the one Phoenix
 * LiveView and Hotwire use: the view lives here, an event arrives naming
 * which face was touched, the block runs, and the view is rendered again.
 *
 * <p>Rendered again rather than patched. The markup is a pure function of the
 * values, so building it afresh cannot drift from the state it describes,
 * whereas a patch that went wrong would leave the two disagreeing with
 * nothing to notice.
 *
 * <p>Nothing but markup crosses to the browser. No script is ever sent, and
 * a handle names a block that was already here rather than carrying one.
 */
public final class View {

    private final Interpreter interpreter;
    private final Map<String, BlockValue> actions = new LinkedHashMap<>();

    private BlockValue description = BlockValue.block();
    private List<Face> faces = List.of();

    private View(Interpreter interpreter) {
        this.interpreter = interpreter;
    }

    /**
     * Runs the source once and holds the layout it produced.
     *
     * <p>Once, not on every render. The source sets up the state as well as
     * describing the page, so running it again would undo whatever an action
     * had changed -- which it did, until a test noticed.
     */
    public static View of(Interpreter interpreter, String source) {
        View view = new View(interpreter);
        interpreter.defineFreshWordsIn(source);
        ScriptOutcome outcome = interpreter.run(source);

        if (outcome.conclusion() == Conclusion.PRODUCED_A_VALUE
                && outcome.value() instanceof BlockValue block) {
            view.description = block.datatype() == Datatype.BLOCK
                    ? block
                    : block.as(Datatype.BLOCK);
        }
        view.rebuild();
        return view;
    }

    /**
     * The page as it stands.
     *
     * <p>Built from the description and the values as they are now, so it
     * cannot drift from the state it describes. A patch that went wrong would
     * leave the two disagreeing with nothing to notice; building it again
     * cannot.
     */
    public String markup() {
        rebuild();
        return Html.render(faces, List.copyOf(actions.keySet()));
    }

    /** The handles a browser may raise, in the order the faces appear. */
    public List<String> actionHandles() {
        return List.copyOf(actions.keySet());
    }

    /**
     * Runs the block belonging to a handle.
     *
     * <p>A handle nobody registered is refused rather than guessed at: an
     * event naming something that is not there is a browser out of step with
     * the server, and running the nearest block would be worse than nothing.
     */
    public ScriptOutcome raise(String handle) {
        BlockValue action = actions.get(handle);
        if (action == null) {
            return new ScriptOutcome(
                    Conclusion.RAISED,
                    ErrorValue.of(ErrorCategory.SCRIPT, "no-such-action",
                            "nothing on this view is called \"" + handle + "\""),
                    Duration.ZERO);
        }
        return interpreter.run(Molder.moldOnly(action));
    }

    /** Evaluates something in the view's interpreter, for asking about state. */
    public ScriptOutcome evaluate(String expression) {
        return interpreter.run(expression);
    }

    /** Reads the faces again, looking every word up as it stands now. */
    private void rebuild() {
        faces = Layout.facesIn(description, this::valueOf);
        rememberActions();
    }

    /** What a word in the layout names, if the interpreter knows it. */
    private java.util.Optional<org.jebol.domain.value.Value> valueOf(String canonical) {
        Context context = interpreter.userContext();
        if (!context.knows(canonical)) {
            return java.util.Optional.empty();
        }
        org.jebol.domain.value.Value held = context.slotFor(canonical).value();
        return held.datatype() == Datatype.UNSET
                ? java.util.Optional.empty()
                : java.util.Optional.of(held);
    }

    /** Gives every actionable face a stable handle for the browser to name. */
    private void rememberActions() {
        List<String> ordered = new ArrayList<>();
        int at = 0;
        for (Face face : faces) {
            if (face.action().isPresent()) {
                ordered.add("face-" + at);
                actions.put("face-" + at, (BlockValue) face.action().orElseThrow());
            }
            at++;
        }
        actions.keySet().retainAll(ordered);
    }
}
