package org.jebol.domain.render;

import org.jebol.domain.eval.Delect;
import org.jebol.domain.value.AnyBlockValue;
import org.jebol.domain.value.AnyWordValue;
import org.jebol.domain.value.BlockValue;
import org.jebol.domain.value.Context;
import org.jebol.domain.value.EvaluationFailure;
import org.jebol.domain.value.GobStorage;
import org.jebol.domain.value.GobValue;
import org.jebol.domain.value.NoneValue;
import org.jebol.domain.value.ObjectValue;
import org.jebol.domain.value.Raised;
import org.jebol.domain.value.Value;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

public final class EffectRefusal {

    private static final String EFFECT = "effect";
    private static final String PUSH = "push";

    private final ObjectValue dialect;
    private final Set<Object> alreadyRead = Collections.newSetFromMap(new IdentityHashMap<>());

    private EffectRefusal(ObjectValue dialect) {
        this.dialect = dialect;
    }

    public static void refuseAnyUnder(GobValue gob, Value dialect) {
        if (dialect instanceof ObjectValue declared) {
            new EffectRefusal(declared).readGob(gob.storage());
        }
    }

    public static void refuseAnyUnder(List<GobValue> gobs, Value dialect) {
        if (dialect instanceof ObjectValue declared) {
            EffectRefusal reading = new EffectRefusal(declared);
            gobs.forEach(gob -> reading.readGob(gob.storage()));
        }
    }

    private void readGob(GobStorage gob) {
        if (!alreadyRead.add(gob)) {
            return;
        }
        if (gob.contentKind() == GobStorage.Content.DRAW
                && gob.readContentIfKind(GobStorage.Content.DRAW) instanceof AnyBlockValue block) {
            readCommands(block);
        }
        for (Value child : gob.pane()) {
            if (child instanceof GobValue held) {
                readGob(held.storage());
            }
        }
    }

    private void readCommands(AnyBlockValue block) {
        if (!alreadyRead.add(block.storage())) {
            return;
        }
        AnyBlockValue left = block;
        AnyBlockValue answer = BlockValue.of();
        while (readNextCommand(left, answer) instanceof AnyBlockValue standing) {
            List<Value> read = answer.remaining();
            if (read.isEmpty()) {
                return;
            }
            if (read.getFirst() instanceof AnyWordValue command) {
                obey(command.canonical(), read.subList(1, read.size()));
            }
            if (standing.index() <= left.index()) {
                return;
            }
            left = standing;
            answer = BlockValue.of();
        }
    }

    private Value readNextCommand(AnyBlockValue left, AnyBlockValue answer) {
        try {
            return Delect.read(dialect, left, answer, false, null, Context.unbound());
        } catch (Raised malformedSoPaintingSkipsItAsBefore) {
            return NoneValue.none();
        }
    }

    private void obey(String command, List<Value> arguments) {
        if (EFFECT.equals(command)) {
            throw Raised.of(EvaluationFailure.FEATURE_NA);
        }
        if (PUSH.equals(command) && !arguments.isEmpty()
                && arguments.getFirst() instanceof AnyBlockValue inside) {
            readCommands(inside);
        }
    }
}
