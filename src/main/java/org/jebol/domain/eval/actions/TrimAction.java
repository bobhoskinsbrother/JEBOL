package org.jebol.domain.eval.actions;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.*;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class TrimAction extends DefaultNative implements ActionValue {

    private static final int AN_OCTET = 0xFF;

    @Override
    public String nativeName() {
        return "trim";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(
                Parameter.required("series", TypesetValue.SERIES.membersAnd(
                        ObjectValue.TYPE, ErrorValue.TYPE, ModuleValue.TYPE)),
                Parameter.belongingTo("with", "characters", Set.of()));
    }

    @Override
    public Set<String> refinementsDeclaredApart() {
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
            case AnyStringValue text -> text.text().codePoints().boxed().collect(Collectors.toSet());
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
