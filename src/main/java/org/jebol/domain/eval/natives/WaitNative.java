package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.Comparison;
import org.jebol.domain.eval.Evaluator;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.eval.EventPath;
import org.jebol.domain.host.ScreenEvent;
import org.jebol.domain.host.ScreenEventDetail;
import org.jebol.domain.value.AnyBlockValue;
import org.jebol.domain.value.AnyFunctionValue;
import org.jebol.domain.value.BlockValue;
import org.jebol.domain.value.AnyDecimalValue;
import org.jebol.domain.value.CharacterValue;
import org.jebol.domain.value.EventCatalogue;
import org.jebol.domain.value.EventValue;
import org.jebol.domain.value.GobValue;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.LitWordValue;
import org.jebol.domain.value.LogicValue;
import org.jebol.domain.value.NoneValue;
import org.jebol.domain.value.PairValue;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.PortValue;
import org.jebol.domain.value.SetWordValue;
import org.jebol.domain.value.TimeValue;
import org.jebol.domain.value.TypesetValue;
import org.jebol.domain.value.Value;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.UnaryOperator;

public class WaitNative extends PortWakingNative {

    private static final long SCREEN_POLL_MILLISECONDS = 10;

    private static final long SLEEP_SLICE_MILLISECONDS = 50;

    private static final long NANOSECONDS_IN_A_MILLISECOND = 1_000_000L;

    private static final double MILLISECONDS_IN_A_SECOND = 1000;

    @Override
    public String nativeName() {
        return "wait";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(Parameter.required("value",
                TypesetValue.NUMBER.membersAnd(TimeValue.TYPE, PortValue.TYPE, BlockValue.TYPE, NoneValue.TYPE)));
    }

    @Override
    public Set<String> refinementsDeclaredApart() {
        return Set.of("all", "only");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            Value asked = arguments.getFirst();
            if (isAnEventPort(asked)) {
                return waitedOnTheScreen((PortValue) asked, evaluator);
            }
            List<Value> waitedOn = asked instanceof AnyBlockValue block
                    ? evaluator.evaluateEachOrRaise(block, context)
                    : List.of(asked);
            Optional<PortValue> eventPort = theEventPortAmong(waitedOn);
            if (eventPort.isPresent()) {
                return howLongToWaitAmong(waitedOn)
                        .map(milliseconds -> waitedOnTheScreenFor(eventPort.get(), milliseconds, evaluator))
                        .orElseGet(() -> waitedOnTheScreen(eventPort.get(), evaluator));
            }
            if (whicheverPortWoke(waitedOn, evaluator) instanceof PortValue woken) {
                return woken;
            }
            howLongToWaitAmong(waitedOn).ifPresent(milliseconds ->
                    sleepInterruptibly(Math.max(0, milliseconds), evaluator));
            return NoneValue.none();
        };
    }

    private Optional<Long> howLongToWaitAmong(List<Value> waitedOn) {
        return waitedOn.stream()
                .filter(each -> each instanceof IntegerValue
                        || each instanceof AnyDecimalValue
                        || each instanceof TimeValue)
                .findFirst()
                .map(this::millisecondsIn);
    }

    private long millisecondsIn(Value duration) {
        return duration instanceof TimeValue(long nanoseconds)
                ? nanoseconds / NANOSECONDS_IN_A_MILLISECOND
                : (long) (MILLISECONDS_IN_A_SECOND * Comparison.asDouble(duration));
    }

    private Value whicheverPortWoke(List<Value> waitedOn, Evaluator evaluator) {
        if (!(evaluator.hostPort("system") instanceof PortValue queue)
                || !(queue.fieldValue("awake") instanceof AnyFunctionValue)) {
            return NoneValue.none();
        }
        AnyBlockValue ports = BlockValue.block(new ArrayList<>(waitedOn));
        while (true) {
            Value said = evaluator.applyFunction(queue.fieldValue("awake"), List.of(queue, ports));
            if (said instanceof LogicValue(boolean truth) && truth) {
                return theFirstWokenAmongEmptyingTheWakeList(waitedOn, queue);
            }
            if (!(said instanceof LogicValue)) {
                theWakeListOf(queue).ifPresent(this::emptied);
                return NoneValue.none();
            }
        }
    }

    private Value theFirstWokenAmongEmptyingTheWakeList(List<Value> waitedOn, PortValue queue) {
        List<Value> woken = theWakeListOf(queue).map(AnyBlockValue::remaining).orElse(List.of());
        Value answer = waitedOn.stream()
                .filter(one -> one instanceof PortValue && woken.contains(one))
                .findFirst()
                .orElse(NoneValue.none());
        theWakeListOf(queue).ifPresent(this::emptied);
        return answer;
    }

    private Optional<AnyBlockValue> theWakeListOf(PortValue queue) {
        return queue.fieldValue("data") instanceof AnyBlockValue list
                ? Optional.of(list)
                : Optional.empty();
    }

    private void emptied(AnyBlockValue list) {
        while (list.storage().length() > 0) {
            list.storage().removeAt(1);
        }
    }

    private Value waitedOnTheScreen(PortValue port, Evaluator evaluator) {
        while (theScreenStillHasSomethingToSay(evaluator)) {
            if (aQueuedEventWakes(port, evaluator)) {
                return NoneValue.none();
            }
            if (!theScreenStillHasSomethingToSay(evaluator)) {
                return NoneValue.none();
            }
            sleepInterruptibly(SCREEN_POLL_MILLISECONDS, evaluator);
        }
        return NoneValue.none();
    }

    private boolean aQueuedEventWakes(PortValue port, Evaluator evaluator) {
        Optional<ScreenEvent> next = evaluator.screen().takeTheNextEvent();
        while (next.isPresent()) {
            if (wakes(port, guiEventFor(next.get()), evaluator)) {
                return true;
            }
            next = evaluator.screen().takeTheNextEvent();
        }
        return false;
    }

    private boolean isAnEventPort(Value asked) {
        return asked instanceof PortValue port && port.schemeName().equals("event");
    }

    private Optional<PortValue> theEventPortAmong(List<Value> waitedOn) {
        return waitedOn.stream()
                .filter(this::isAnEventPort)
                .map(PortValue.class::cast)
                .findFirst();
    }

    private Value waitedOnTheScreenFor(PortValue port, long milliseconds, Evaluator evaluator) {
        long deadline = System.nanoTime() + milliseconds * NANOSECONDS_IN_A_MILLISECOND;
        while (true) {
            if (aQueuedEventWakes(port, evaluator)) {
                return port;
            }
            long remainingNanoseconds = deadline - System.nanoTime();
            if (remainingNanoseconds <= 0 || evaluator.reasonToStop().isPresent()) {
                return NoneValue.none();
            }
            sleepInterruptibly(Math.min(SCREEN_POLL_MILLISECONDS,
                    Math.ceilDiv(remainingNanoseconds, NANOSECONDS_IN_A_MILLISECOND)), evaluator);
        }
    }

    private boolean theScreenStillHasSomethingToSay(Evaluator evaluator) {
        Value root = evaluator.systemContext().valueAt("system", "view", "screen-gob");
        return root instanceof GobValue gob && gob.storage().length() > 0;
    }

    private EventValue guiEventFor(ScreenEvent reported) {
        List<Value> spec = new ArrayList<>(List.of(
                SetWordValue.of("type"), LitWordValue.of(reported.kind().spelling())));
        spec.addAll(reported.window() == null
                ? List.of(SetWordValue.of("port"), NoneValue.none())
                : List.of(SetWordValue.of("window"), reported.window()));
        spec.addAll(whatTheDetailWrites(reported.detail()));
        return EventPath.made(BlockValue.block(spec), UnaryOperator.identity()) instanceof EventValue made
                ? made
                : EventValue.fresh();
    }

    private List<Value> whatTheDetailWrites(ScreenEventDetail detail) {
        return switch (detail) {
            case ScreenEventDetail.At(int across, int down) ->
                    List.of(SetWordValue.of("offset"), PairValue.of(across, down));
            case ScreenEventDetail.Typed(int codepoint) ->
                    List.of(SetWordValue.of("key"), CharacterValue.of(codepoint));
            case ScreenEventDetail.NamedKey(String name) -> EventCatalogue.keyIndexOf(name)
                    .<List<Value>>map(position -> List.of(
                            SetWordValue.of("code"), IntegerValue.of(position + 1)))
                    .orElse(List.of());
            case ScreenEventDetail.NothingMore nothing -> List.of();
        };
    }

    private void sleepInterruptibly(long milliseconds, Evaluator evaluator) {
        long remaining = milliseconds;
        while (remaining > 0) {
            if (evaluator.reasonToStop().isPresent()) {
                return;
            }
            try {
                Thread.sleep(Math.min(SLEEP_SLICE_MILLISECONDS, remaining));
            } catch (InterruptedException stopped) {
                Thread.currentThread().interrupt();
                return;
            }
            remaining -= SLEEP_SLICE_MILLISECONDS;
        }
    }
}
