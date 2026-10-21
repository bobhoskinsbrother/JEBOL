package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.Comparison;
import org.jebol.domain.eval.Evaluator;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.eval.Stopped;
import org.jebol.domain.eval.EventPath;
import org.jebol.domain.host.FilePort;
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

    private static final long PAUSE_SLICE_MILLISECONDS = 50;

    private static final long NANOSECONDS_IN_MILLISECOND = 1_000_000L;

    private static final double MILLISECONDS_IN_SECOND = 1000;

    @Override
    public String nativeName() {
        return "wait";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(Parameter.of("value",
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
                return waitOnScreen((PortValue) asked, evaluator);
            }
            List<Value> waitedOn = asked instanceof AnyBlockValue block
                    ? evaluator.evaluateEachOrRaise(block, context)
                    : List.of(asked);
            Optional<PortValue> eventPort = findTheEventPortAmong(waitedOn);
            if (eventPort.isPresent()) {
                return findHowLongToWaitAmong(waitedOn)
                        .map(milliseconds -> waitOnScreenFor(eventPort.get(), milliseconds, evaluator))
                        .orElseGet(() -> waitOnScreen(eventPort.get(), evaluator));
            }
            return waitForFirstToWakeWithin(waitedOn, findHowLongToWaitAmong(waitedOn), evaluator);
        };
    }

    private Value waitForFirstToWakeWithin(List<Value> waitedOn, Optional<Long> milliseconds, Evaluator evaluator) {
        long left = milliseconds.map(asked -> Math.max(0, asked)).orElse(0L);
        while (true) {
            evaluator.deliverWhatWasPosted();
            if (findWhicheverPortWoke(waitedOn, evaluator) instanceof PortValue woken) {
                return woken;
            }
            long slice = Math.min(PAUSE_SLICE_MILLISECONDS, left);
            if (left <= 0 || !sleepWithoutBeingInterrupted(slice, evaluator)) {
                return NoneValue.none();
            }
            left -= slice;
        }
    }

    private Optional<Long> findHowLongToWaitAmong(List<Value> waitedOn) {
        return waitedOn.stream()
                .filter(each -> each instanceof IntegerValue
                        || each instanceof AnyDecimalValue
                        || each instanceof TimeValue)
                .findFirst()
                .map(this::countMillisecondsIn);
    }

    private long countMillisecondsIn(Value duration) {
        return duration instanceof TimeValue(long nanoseconds)
                ? nanoseconds / NANOSECONDS_IN_MILLISECOND
                : (long) (MILLISECONDS_IN_SECOND * Comparison.asDouble(duration));
    }

    private Value findWhicheverPortWoke(List<Value> waitedOn, Evaluator evaluator) {
        if (!(evaluator.findHostPort("system") instanceof PortValue queue)
                || !(queue.readFieldValue("awake") instanceof AnyFunctionValue)) {
            return NoneValue.none();
        }
        AnyBlockValue ports = BlockValue.of(new ArrayList<>(waitedOn));
        while (true) {
            Value said = evaluator.applyFunction(queue.readFieldValue("awake"), List.of(queue, ports));
            if (said instanceof LogicValue(boolean truth) && truth) {
                return takeFirstWokenAndEmptyWakeList(waitedOn, queue);
            }
            if (!(said instanceof LogicValue)) {
                findTheWakeListOf(queue).ifPresent(this::empty);
                return NoneValue.none();
            }
        }
    }

    private Value takeFirstWokenAndEmptyWakeList(List<Value> waitedOn, PortValue queue) {
        List<Value> woken = findTheWakeListOf(queue).map(AnyBlockValue::remaining).orElse(List.of());
        Value answer = waitedOn.stream()
                .filter(one -> one instanceof PortValue && woken.contains(one))
                .findFirst()
                .orElse(NoneValue.none());
        findTheWakeListOf(queue).ifPresent(this::empty);
        return answer;
    }

    private Optional<AnyBlockValue> findTheWakeListOf(PortValue queue) {
        return queue.readFieldValue("data") instanceof AnyBlockValue list
                ? Optional.of(list)
                : Optional.empty();
    }

    private void empty(AnyBlockValue list) {
        while (list.storage().length() > 0) {
            list.storage().removeAt(1);
        }
    }

    private Value waitOnScreen(PortValue port, Evaluator evaluator) {
        while (doesScreenStillHaveSomethingToSay(evaluator)) {
            if (doesQueuedEventWake(port, evaluator)) {
                return NoneValue.none();
            }
            if (!doesScreenStillHaveSomethingToSay(evaluator)
                    || !sleepWithoutBeingInterrupted(SCREEN_POLL_MILLISECONDS, evaluator)) {
                return NoneValue.none();
            }
        }
        return NoneValue.none();
    }

    private boolean doesQueuedEventWake(PortValue port, Evaluator evaluator) {
        Optional<ScreenEvent> next = evaluator.ports().screen().acceptNextEvent();
        while (next.isPresent()) {
            if (wakes(port, makeGuiEventFor(next.get()), evaluator)) {
                return true;
            }
            next = evaluator.ports().screen().acceptNextEvent();
        }
        return false;
    }

    private boolean isAnEventPort(Value asked) {
        return asked instanceof PortValue port && port.schemeName().equals("event");
    }

    private Optional<PortValue> findTheEventPortAmong(List<Value> waitedOn) {
        return waitedOn.stream()
                .filter(this::isAnEventPort)
                .map(PortValue.class::cast)
                .findFirst();
    }

    private Value waitOnScreenFor(PortValue port, long milliseconds, Evaluator evaluator) {
        long deadline = System.nanoTime() + milliseconds * NANOSECONDS_IN_MILLISECOND;
        while (true) {
            if (doesQueuedEventWake(port, evaluator)) {
                return port;
            }
            long remainingNanoseconds = deadline - System.nanoTime();
            if (remainingNanoseconds <= 0
                    || !sleepWithoutBeingInterrupted(Math.min(SCREEN_POLL_MILLISECONDS,
                            Math.ceilDiv(remainingNanoseconds, NANOSECONDS_IN_MILLISECOND)), evaluator)) {
                return NoneValue.none();
            }
        }
    }

    private boolean doesScreenStillHaveSomethingToSay(Evaluator evaluator) {
        Value root = evaluator.systemContext().readValueAt("system", "view", "screen-gob");
        return root instanceof GobValue gob && gob.storage().length() > 0;
    }

    private EventValue makeGuiEventFor(ScreenEvent reported) {
        List<Value> spec = new ArrayList<>(List.of(
                SetWordValue.of("type"), LitWordValue.of(reported.type().spelling())));
        spec.addAll(reported.window() == null
                ? List.of(SetWordValue.of("port"), NoneValue.none())
                : List.of(SetWordValue.of("window"), reported.window()));
        spec.addAll(writeTheDetail(reported.detail()));
        return EventPath.make(BlockValue.of(spec), UnaryOperator.identity()) instanceof EventValue made
                ? made
                : EventValue.fresh();
    }

    private List<Value> writeTheDetail(ScreenEventDetail detail) {
        return switch (detail) {
            case ScreenEventDetail.At(int across, int down) ->
                    List.of(SetWordValue.of("offset"), PairValue.of(across, down));
            case ScreenEventDetail.Typed(int codepoint) ->
                    List.of(SetWordValue.of("key"), CharacterValue.of(codepoint));
            case ScreenEventDetail.NamedKey(String name) -> EventCatalogue.findTheKeyIndexOf(name)
                    .<List<Value>>map(position -> List.of(
                            SetWordValue.of("code"), IntegerValue.of(position + 1)))
                    .orElse(List.of());
            case ScreenEventDetail.Scrolled(_, _, int amount) ->
                    List.of(SetWordValue.of("offset"), PairValue.of(0, amount));
            case ScreenEventDetail.NothingMore nothing -> List.of();
        };
    }

    private boolean sleepWithoutBeingInterrupted(long milliseconds, Evaluator evaluator) {
        long remaining = milliseconds;
        while (remaining > 0) {
            stopIfRunHasBeenStopped(evaluator);
            if (!letHostPassTime(evaluator, Math.min(PAUSE_SLICE_MILLISECONDS, remaining))) {
                return false;
            }
            remaining -= PAUSE_SLICE_MILLISECONDS;
        }
        stopIfRunHasBeenStopped(evaluator);
        return true;
    }

    private boolean letHostPassTime(Evaluator evaluator, long milliseconds) {
        try {
            return evaluator.ports().pause().pauseFor(milliseconds);
        } catch (FilePort.Denied refused) {
            throw refused.raise();
        }
    }

    private void stopIfRunHasBeenStopped(Evaluator evaluator) {
        evaluator.reasonToStop().ifPresent(reason -> {
            throw new Stopped(reason);
        });
    }
}
