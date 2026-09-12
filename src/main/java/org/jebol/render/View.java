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
import java.util.Optional;

/**
 * A layout that stays on the server and answers events.
 *
 * <p>An event arrives naming which face was touched, the block runs, and the
 * view is rendered again. Nothing but markup crosses to the browser: no script
 * is ever sent, and a handle names a block that was already here rather than
 * carrying one.
 */
public final class View {

    private final Interpreter interpreter;
    private final Map<String, BlockValue> actions = new LinkedHashMap<>();

    private BlockValue description = BlockValue.block();
    private List<Face> faces = List.of();

    private View(Interpreter interpreter) {
        this.interpreter = interpreter;
    }

    /** Runs the source once and holds the layout it produced. */
    public static View of(Interpreter interpreter, String source) {
        View view = new View(interpreter);
        interpreter.defineFreshWordsIn(source);
        ScriptOutcome outcome = runOnceBecauseItSetsUpTheStateAsWellAsDescribingIt(
                interpreter, source);

        if (outcome.conclusion() == Conclusion.PRODUCED_A_VALUE
                && outcome.value() instanceof BlockValue block) {
            view.description = block.datatype() == Datatype.BLOCK
                    ? block
                    : block.as(Datatype.BLOCK);
        }
        view.rebuild();
        return view;
    }

    private static ScriptOutcome runOnceBecauseItSetsUpTheStateAsWellAsDescribingIt(
            Interpreter interpreter, String source) {

        return interpreter.run(source);
    }

    /** The page as it stands, built afresh so it cannot drift from the state. */
    public String markup() {
        rebuild();
        return Html.render(faces, List.copyOf(actions.keySet()));
    }

    /** The handles a browser may raise, in the order the faces appear. */
    public List<String> actionHandles() {
        return List.copyOf(actions.keySet());
    }

    /** Runs the block belonging to a handle. */
    public ScriptOutcome raise(String handle) {
        BlockValue action = actions.get(handle);
        if (action == null) {
            return aBrowserOutOfStepWithThisView(handle);
        }
        return interpreter.run(Molder.moldOnly(action));
    }

    private static ScriptOutcome aBrowserOutOfStepWithThisView(String handle) {
        return new ScriptOutcome(
                Conclusion.RAISED,
                ErrorValue.of(ErrorCategory.SCRIPT, "no-such-action",
                        "nothing on this view is called \"" + handle + "\""),
                Duration.ZERO);
    }

    /** Evaluates something in the view's interpreter, for asking about state. */
    public ScriptOutcome evaluate(String expression) {
        return interpreter.run(expression);
    }

    private void rebuild() {
        faces = Layout.facesIn(description, this::valueOf);
        rememberActions();
    }

    private Optional<Value> valueOf(String canonical) {
        Context context = interpreter.userContext();
        if (!context.knows(canonical)) {
            return Optional.empty();
        }
        Value held = context.slotFor(canonical).value();
        return held.datatype() == Datatype.UNSET
                ? Optional.empty()
                : Optional.of(held);
    }

    private void rememberActions() {
        List<String> stillThere = new ArrayList<>();
        int at = 0;
        for (Face face : faces) {
            if (face.action().isPresent()) {
                stillThere.add(handleFor(at));
                actions.put(handleFor(at), (BlockValue) face.action().orElseThrow());
            }
            at++;
        }
        actions.keySet().retainAll(stillThere);
    }

    private static String handleFor(int position) {
        return "face-" + position;
    }
}
