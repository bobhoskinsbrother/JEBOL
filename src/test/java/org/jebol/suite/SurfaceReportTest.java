package org.jebol.suite;

import org.jebol.domain.eval.Natives;
import org.jebol.domain.value.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;

class SurfaceReportTest {

    @Test
    @DisplayName("every function JEBOL has, with its arguments and refinements")
    void theSurfaceIsPrinted() {
        TreeMap<String, String> byName = new TreeMap<>();
        for (ContextSlot slot : Natives.standard().asContext().slots()) {
            describe(slot.value()).ifPresent(
                    shape -> byName.put(slot.canonical(), slot.canonical() + " |" + shape));
        }

        System.out.printf("%nSURFACE %d functions:%n", byName.size());
        byName.values().forEach(line -> System.out.println("SURFACE " + line));
        writeForTheAudit(byName.values());

        assertThat(byName).as("an empty surface means nothing was registered").isNotEmpty();
    }

    private static void writeForTheAudit(java.util.Collection<String> lines) {
        java.nio.file.Path into = java.nio.file.Path.of("build", "jebol-surface.txt");
        try {
            java.nio.file.Files.createDirectories(into.getParent());
            java.nio.file.Files.write(into, withTheDatatypeTable(lines));
        } catch (java.io.IOException unwritable) {
            throw new java.io.UncheckedIOException(unwritable);
        }
    }

    private static List<String> withTheDatatypeTable(java.util.Collection<String> lines) {
        List<String> everything = new java.util.ArrayList<>();
        for (Datatype datatype : Datatype.values()) {
            everything.add("DATATYPE " + datatype.literalSpelling());
        }
        everything.addAll(lines);
        return everything;
    }

    private static Optional<String> describe(Value value) {
        if (value instanceof OperatorValue operator) {
            return describe(operator.underlying());
        }
        StringBuilder shape = new StringBuilder();
        switch (value) {
            case NativeValue asNative -> {
                appendArguments(shape, asNative.parameters());
                asNative.declaredRefinements().stream().sorted()
                        .forEach(refinement -> shape.append(" /").append(refinement));
            }
            case FunctionValue written -> appendArguments(shape, written.parameters());
            default -> {
                return Optional.empty();
            }
        }
        return Optional.of(shape.toString());
    }

    private static void appendArguments(StringBuilder shape, List<Parameter> parameters) {
        for (Parameter parameter : parameters) {
            if (!parameter.consumesAnArgument()) {
                shape.append(" /").append(parameter.name());
                continue;
            }
            shape.append(' ').append(parameter.name());
            appendAcceptedTypes(shape, parameter);
        }
    }

    private static void appendAcceptedTypes(StringBuilder shape, Parameter parameter) {
        Set<Datatype> accepted = parameter.acceptedTypes();
        if (accepted.isEmpty() || accepted.size() == Datatype.values().length) {
            return;
        }
        shape.append('<')
                .append(String.join(" ", accepted.stream()
                        .map(Datatype::literalSpelling)
                        .sorted()
                        .toList()))
                .append('>');
    }
}
