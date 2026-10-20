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

final class SingleCommand implements Command {

    private final Extension extension;

    private final List<Value> arguments;

    private final Set<String> refinements;

    private final Evaluator evaluator;

    private final Context context;

    private final AtomicInteger handlesMade;

    private Optional<RuntimeException> escaped = Optional.empty();

    SingleCommand(Extension extension, List<Value> arguments, Set<String> refinements,
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
    public Value evaluate(AnyBlockValue block) {
        try {
            return evaluator.evaluateOrRaise(block, context);
        } catch (RuntimeException scriptException) {
            escaped = Optional.of(scriptException);
            throw scriptException;
        }
    }

    boolean escapesFromTheScript(RuntimeException failed) {
        return escaped.filter(failed::equals).isPresent();
    }

    @Override
    public HandleValue makeHandle(String type, Object held) {
        if (findDeclaredType(type).isEmpty()) {
            throw new IllegalArgumentException(extension.name() + " declares no handle type called " + type);
        }
        return HandleValue.wrapAContext(type, handlesMade.incrementAndGet(), JavaObjectValue.of(held));
    }

    @Override
    public <T> T unwrap(Value handle, Class<T> type) {
        if (handle instanceof HandleValue ours
                && findDeclaredType(ours.typeName()).isPresent()
                && ours.payload() instanceof JavaObjectValue carried
                && carried.held().filter(type::isInstance).isPresent()) {
            return type.cast(carried.held().get());
        }
        throw Raised.of(EvaluationFailure.INVALID_HANDLE, handle);
    }

    private Optional<HandleType> findDeclaredType(String kind) {
        return extension.handleTypes().stream().filter(declared -> declared.name().equals(kind)).findFirst();
    }

    @Override
    public Raised failure(ErrorCategory category, String id, Value... about) {
        return new Raised(switch (about.length) {
            case 0 -> ErrorValue.of(category, id, id);
            case 1 -> ErrorValue.of(category, id, id, about[0]);
            case 2 -> ErrorValue.of(category, id, id, about[0], about[1]);
            default -> ErrorValue.of(category, id, id, about[0], about[1], about[2]);
        });
    }

    @Override
    public Postbox postbox() {
        return evaluator.postbox();
    }
}
