package org.jebol.domain.read;

import org.jebol.domain.value.AnyWordValue;
import org.jebol.domain.value.Catalogue;
import org.jebol.domain.value.Construction;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.LogicValue;
import org.jebol.domain.value.NoneValue;
import org.jebol.domain.value.Typeset;
import org.jebol.domain.value.TypesetValue;
import org.jebol.domain.value.UnsetValue;
import org.jebol.domain.value.Value;
import org.jebol.domain.value.VectorKind;
import org.jebol.domain.value.VectorSpec;

import java.util.List;
import java.util.Optional;

final class ConstructedValues {

    private final Construction construction;

    ConstructedValues(Construction construction) {
        this.construction = construction;
    }

    private static final class CannotConstruct extends RuntimeException {

        private static final long serialVersionUID = 1L;

        CannotConstruct() {
            super("malconstruct", null, false, false);
        }
    }

    Optional<Value> built(List<Value> contents) {
        try {
            return Optional.of(construct(contents));
        } catch (CannotConstruct refused) {
            return Optional.empty();
        }
    }

    private Value construct(List<Value> contents) {
        if (contents.isEmpty() || !(contents.getFirst() instanceof AnyWordValue leading)) {
            throw new CannotConstruct();
        }
        if (contents.size() == 1) {
            Optional<Value> simple = switch (leading.canonical()) {
                case "true" -> Optional.of(LogicValue.yes());
                case "false" -> Optional.of(LogicValue.no());
                case "none" -> Optional.of(NoneValue.none());
                case "unset" -> Optional.of(UnsetValue.unset());
                default -> Optional.empty();
            };
            if (simple.isPresent()) {
                return simple.get();
            }
        }
        if (namesAVector(leading, contents.size())) {
            return VectorSpec.readConstruction(contents).orElseThrow(CannotConstruct::new);
        }
        Value resolved = datatypeNamed(leading);
        if (contents.size() == 1) {
            return resolved;
        }
        if (!(resolved instanceof Datatype datatype)) {
            throw new CannotConstruct();
        }
        return builtBy(datatype, contents.subList(1, contents.size()));
    }

    private Value builtBy(Datatype datatype, List<Value> contents) {
        try {
            return datatype.constructedFrom(contents, construction);
        } catch (RuntimeException refused) {
            throw new CannotConstruct();
        }
    }

    private boolean namesAVector(AnyWordValue leading, int howManyParts) {
        if ("vector!".equals(leading.canonical())) {
            return howManyParts > 1;
        }
        return VectorKind.named(leading.spelling()).isPresent();
    }

    private Value datatypeNamed(AnyWordValue word) {
        if (!word.spelling().endsWith("!")) {
            throw new CannotConstruct();
        }
        Optional<Datatype> datatype = Catalogue.DATATYPES.named(word.spelling());
        if (datatype.isPresent()) {
            return datatype.get();
        }
        String name = word.spelling().substring(0, word.spelling().length() - 1);
        return Typeset.named(name)
                .map(typeset -> (Value) TypesetValue.of(typeset))
                .orElseThrow(CannotConstruct::new);
    }
}
