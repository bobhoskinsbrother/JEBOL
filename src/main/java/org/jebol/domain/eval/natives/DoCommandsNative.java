package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.Evaluator;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.AnyBlockValue;
import org.jebol.domain.value.AnyPathValue;
import org.jebol.domain.value.BlockValue;
import org.jebol.domain.value.CommandValue;
import org.jebol.domain.value.Context;
import org.jebol.domain.value.DefaultNative;
import org.jebol.domain.value.ErrorValue;
import org.jebol.domain.value.EvaluationFailure;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.ParameterKind;
import org.jebol.domain.value.ParenValue;
import org.jebol.domain.value.Raised;
import org.jebol.domain.value.SetWordValue;
import org.jebol.domain.value.UnsetValue;
import org.jebol.domain.value.Value;
import org.jebol.domain.value.WordValue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class DoCommandsNative extends DefaultNative {

    private static final String WHAT_A_FAILED_COMMAND_RAISES = "command-fail";

    @Override
    public String nativeName() {
        return "do-commands";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(Parameter.of("commands", Set.of(BlockValue.TYPE)));
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) ->
                new CommandsInTurn(((AnyBlockValue) arguments.getFirst()).remaining(), evaluator, context)
                        .runToTheLastAnswer();
    }

    private static final class CommandsInTurn {

        private final List<Value> items;

        private final Evaluator evaluator;

        private final Context context;

        private int at;

        private CommandsInTurn(List<Value> items, Evaluator evaluator, Context context) {
            this.items = items;
            this.evaluator = evaluator;
            this.context = context;
        }

        private Value runToTheLastAnswer() {
            Value last = UnsetValue.unset();
            while (at < items.size()) {
                Optional<SetWordValue> answerGoesTo = items.get(at) instanceof SetWordValue named
                        ? Optional.of(named)
                        : Optional.empty();
                if (answerGoesTo.isPresent()) {
                    at++;
                }
                Value answer = runTheNextCommand();
                answerGoesTo.ifPresent(named -> named.findTheBoundSlot().setValue(answer));
                last = answer;
            }
            return last;
        }

        private Value runTheNextCommand() {
            Value named = items.get(at++);
            Value function = named instanceof WordValue word ? word.findTheBoundSlot().value() : named;
            if (!(function instanceof CommandValue command)) {
                throw Raised.of(EvaluationFailure.EXPECT_VAL, SetWordValue.of("command!"), named);
            }
            List<Value> gathered = new ArrayList<>();
            for (Parameter parameter : command.parameters()) {
                if (parameter.consumesAnArgument() && parameter.owningRefinement().isEmpty()) {
                    gathered.add(readArgumentFor(parameter, named));
                }
            }
            try {
                return command.behaviour().call(gathered, evaluator, context, Set.of());
            } catch (Raised failed) {
                if (failed.error().errorId().equals(WHAT_A_FAILED_COMMAND_RAISES)) {
                    return UnsetValue.unset();
                }
                throw failed;
            }
        }

        private Value readArgumentFor(Parameter parameter, Value named) {
            if (at >= items.size()) {
                throw Raised.of(EvaluationFailure.NO_ARG, named, parameter.asWrittenInTheSpec());
            }
            Value given = evaluateTheArgument(items.get(at++), parameter);
            if (!parameter.accepts(given.datatype())) {
                throw new Raised(ErrorValue.of(
                        EvaluationFailure.EXPECT_ARG.category(),
                        EvaluationFailure.EXPECT_ARG.errorId(),
                        "a command does not allow " + given.datatype().literalSpelling()
                                + " for its " + parameter.name() + " argument",
                        named, parameter.asWrittenInTheSpec(), given.datatype()));
            }
            return given;
        }

        private Value evaluateTheArgument(Value written, Parameter parameter) {
            boolean fetched = parameter.kind() == ParameterKind.NORMAL;
            return switch (written) {
                case ParenValue paren -> evaluator.evaluateOrRaise(paren, context);
                case WordValue word when fetched -> word.findTheBoundSlot().value();
                case AnyPathValue path when fetched ->
                        evaluator.evaluateOrRaise(BlockValue.of(List.of(path)), context);
                default -> written;
            };
        }
    }
}
