package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.BinaryValue;
import org.jebol.domain.value.CharacterValue;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.StringValue;
import org.jebol.domain.value.Trimming;
import org.jebol.domain.value.Typeset;
import org.jebol.domain.value.Value;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class TrimAction extends DefaultNative {

    private static final int AN_OCTET = 0xFF;

    @Override
    public String name() {
        return "trim";
    }

    @Override
    public List<Parameter> parameters() {
        return List.of(
                Parameter.required("series", Typeset.SERIES.membersAnd(
                        Datatype.OBJECT, Datatype.ERROR, Datatype.MODULE)),
                Parameter.belongingTo("with", "characters", Set.of()));
    }

    @Override
    public Set<String> refinements() {
        return Set.of("head", "tail", "auto", "lines", "all", "with");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> arguments.getFirst().trimmed(
                new Trimming(refinements, argumentOf("with", 0, arguments, refinements)
                        .map(this::unwantedCodePoints)
                        .orElseGet(Set::of)));
    }

    private Set<Integer> unwantedCodePoints(Value characters) {
        return switch (characters) {
            case CharacterValue character -> Set.of(character.codepoint());
            case IntegerValue whole -> Set.of((int) whole.magnitude());
            case StringValue text -> text.text().codePoints().boxed().collect(Collectors.toSet());
            case BinaryValue bytes -> octetsOf(bytes);
            default -> Set.of();
        };
    }

    private Set<Integer> octetsOf(BinaryValue bytes) {
        Set<Integer> octets = new HashSet<>();
        for (byte octet : bytes.octetsFromHere()) {
            octets.add(octet & AN_OCTET);
        }
        return octets;
    }
}
