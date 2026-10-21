package org.jebol.domain.eval.actions;

import org.jebol.domain.eval.Encodings;
import org.jebol.domain.eval.RandomDrawing;
import org.jebol.domain.eval.RebolRandom;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.host.DigestPort;
import org.jebol.domain.value.ActionValue;
import org.jebol.domain.value.DefaultNative;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.UnsetValue;
import org.jebol.domain.value.Value;

import java.util.List;
import java.util.Set;

public class RandomAction extends DefaultNative implements ActionValue {

    private final RebolRandom randomness;
    private final RandomDrawing secureRandomness;
    private final Encodings encodings;

    public RandomAction(Encodings encodings, DigestPort digests) {
        this.encodings = encodings;
        this.randomness = new RebolRandom(digests);
        this.secureRandomness = randomness.secured();
    }

    @Override
    public String nativeName() {
        return "random";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return acceptsWhateverComesAlong("value");
    }

    @Override
    public Set<String> refinementsDeclaredApart() {
        return Set.of("seed", "secure", "only");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            Value given = arguments.getFirst();
            if (refinements.contains("seed")) {
                return seedWith(given);
            }
            RandomDrawing drawing = refinements.contains("secure") ? secureRandomness : randomness;
            return refinements.contains("only")
                    ? given.pickAtRandom(drawing)
                    : given.randomise(drawing);
        };
    }

    private Value seedWith(Value chosen) {
        randomness.seed(chosen.asRandomSeed(encodings::computeTheChecksumSeed));
        return UnsetValue.unset();
    }
}
