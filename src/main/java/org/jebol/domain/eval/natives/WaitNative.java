package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.Comparison;
import org.jebol.domain.eval.Evaluator;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.host.ScreenEvent;
import org.jebol.domain.value.AnyBlockValue;
import org.jebol.domain.value.BlockValue;
import org.jebol.domain.value.Typeset;
import org.jebol.domain.value.AnyDecimalValue;
import org.jebol.domain.value.EventCatalogue;
import org.jebol.domain.value.EventValue;
import org.jebol.domain.value.GobValue;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.LogicValue;
import org.jebol.domain.value.NoneValue;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.PortValue;
import org.jebol.domain.value.TimeValue;
import org.jebol.domain.value.Typeset;
import org.jebol.domain.value.Value;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class WaitNative extends PortWakingNative {

    private static final long SCREEN_POLL_MILLISECONDS = 10;

    private static final long SLEEP_SLICE_MILLISECONDS = 50;

    private static final long NANOSECONDS_IN_A_MILLISECOND = 1_000_000L;

    private static final double MILLISECONDS_IN_A_SECOND = 1000;

    private static final int AN_UNCATALOGUED_EVENT = 0;

    @Override
    public String nativeName() {
        return "wait";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(Parameter.required("value",
                Typeset.NUMBER.membersAnd(TimeValue.TYPE, PortValue.TYPE, BlockValue.TYPE, NoneValue.TYPE)));
    }

    @Override
    public Set<String> refinementsDeclaredApart() {
        return Set.of("all", "only");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            Value asked = arguments.getFirst();
            if (asked instanceof PortValue port && port.schemeName().equals("event")) {
                return waitedOnTheScreen(port, evaluator);
            }
            List<Value> waitedOn = asked instanceof AnyBlockValue block
                    ? evaluator.evaluateEachOrRaise(block, context)
                    : List.of(asked);
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
                || !queue.fieldValue("awake").datatype().belongsTo(Typeset.ANY_FUNCTION)) {
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
            for (ScreenEvent reported : evaluator.screen().takeQueuedEvents()) {
                if (wakes(port, guiEventFor(reported), evaluator)) {
                    return NoneValue.none();
                }
            }
            if (!theScreenStillHasSomethingToSay(evaluator)) {
                return NoneValue.none();
            }
            sleepInterruptibly(SCREEN_POLL_MILLISECONDS, evaluator);
        }
        return NoneValue.none();
    }

    private boolean theScreenStillHasSomethingToSay(Evaluator evaluator) {
        Value root = evaluator.systemContext().valueAt("system", "view", "screen-gob");
        return root instanceof GobValue gob && gob.storage().length() > 0;
    }

    private EventValue guiEventFor(ScreenEvent reported) {
        return EventValue.fresh()
                .withType(EventCatalogue.typeIndexOf(reported.kind().spelling())
                        .orElse(AN_UNCATALOGUED_EVENT))
                .withAttached(EventValue.Model.GUI,
                        reported.window() == null ? NoneValue.none() : reported.window());
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
