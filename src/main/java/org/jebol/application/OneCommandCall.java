package org.jebol.application;

import org.jebol.domain.eval.Evaluator;
import org.jebol.domain.eval.Postbox;
import org.jebol.domain.value.AnyBlockValue;
import org.jebol.domain.value.Context;
import org.jebol.domain.value.ErrorCategory;
import org.jebol.domain.value.ErrorValue;
import org.jebol.domain.value.EvaluationFailure;
import org.jebol.domain.value.HandleValue;
import org.jebol.domain.value.JavaObjectValue;
import org.jebol.domain.value.Raised;
import org.jebol.domain.value.Value;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

final class OneCommandCall implements CommandCall {

    private final Extension extension;

    private final List<Value> arguments;

    private final Set<String> refinements;

    private final Evaluator evaluator;

    private final Context context;

    private final AtomicInteger handlesMade;

    private Optional<RuntimeException> escaped = Optional.empty();

    OneCommandCall(Extension extension, List<Value> arguments, Set<String> refinements,
            Evaluator evaluator, Context context, AtomicInteger handlesMade) {
        this.extension = extension;
        this.arguments = List.copyOf(arguments);
        this.refinements = Set.copyOf(refinements);
        this.evaluator = evaluator;
        this.context = context;
        this.handlesMade = handlesMade;
    }

    @Override
    public List<Value> arguments() {
        return arguments;
    }

    @Override
    public Set<String> refinements() {
        return refinements;
    }

    @Override
    public Value evaluated(AnyBlockValue block) {
        try {
            return evaluator.evaluateOrRaise(block, context);
        } catch (RuntimeException fromTheScript) {
            escaped = Optional.of(fromTheScript);
            throw fromTheScript;
        }
    }

    boolean escapedFromTheScript(RuntimeException failed) {
        return escaped.filter(failed::equals).isPresent();
    }

    @Override
    public HandleValue handleOf(String kind, Object held) {
        if (theKindNamed(kind).isEmpty()) {
            throw new IllegalArgumentException(extension.name() + " declares no handle kind called " + kind);
        }
        return HandleValue.context(kind, handlesMade.incrementAndGet(), JavaObjectValue.of(held));
    }

    @Override
    public <T> T heldBy(Value handle, Class<T> type) {
        if (handle instanceof HandleValue ours
                && theKindNamed(ours.typeName()).isPresent()
                && ours.payload() instanceof JavaObjectValue carried
                && carried.held().filter(type::isInstance).isPresent()) {
            return type.cast(carried.held().get());
        }
        throw Raised.of(EvaluationFailure.INVALID_HANDLE, handle);
    }

    private Optional<HandleKind> theKindNamed(String kind) {
        return extension.handleKinds().stream().filter(declared -> declared.name().equals(kind)).findFirst();
    }

    @Override
    public Raised failure(ErrorCategory category, String id, Value... about) {
        return new Raised(switch (about.length) {
            case 0 -> ErrorValue.of(category, id, id);
            case 1 -> ErrorValue.about(category, id, id, about[0]);
            case 2 -> ErrorValue.about(category, id, id, about[0], about[1]);
            default -> ErrorValue.about(category, id, id, about[0], about[1], about[2]);
        });
    }

    @Override
    public Postbox postbox() {
        return evaluator.postbox();
    }
}
