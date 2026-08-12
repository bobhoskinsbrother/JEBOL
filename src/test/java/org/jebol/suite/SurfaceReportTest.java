package org.jebol.suite;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import org.jebol.domain.eval.Natives;
import org.jebol.domain.value.ContextSlot;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.FunctionValue;
import org.jebol.domain.value.NativeValue;
import org.jebol.domain.value.OperatorValue;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.Value;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Every function JEBOL offers, with its arguments and refinements.
 *
 * <p>Printed in the shape Rebol declares its own, so the two can be compared
 * line for line against the declared specs in {@code r3/c-surface.txt}, which
 * {@code scripts/c-surface.py} writes from Rebol's source. JEBOL's natives carry
 * a rebuilt spec rather than a REBOL block, so this reads the registry instead.
 *
 * <p>Why this exists: without it, the only way to learn that a native is
 * missing, or that it refuses an argument Rebol accepts, is to run
 * the suite and read the failure. That finds them one at a time and in no
 * useful order. The two surfaces side by side give the whole list at once,
 * and say which of the three kinds of gap each one is: a function that is
 * not there, a refinement that is not there, or a parameter that takes
 * fewer datatypes than it should.
 *
 * <p>Reports rather than asserts, like the other work-list tools here.
 */
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

    /**
     * The same lines, written where `scripts/c-parity.py` can read them.
     *
     * <p>Printing alone means the surface can only be compared by eye. The
     * audit compares Rebol's own C declarations against this, so this one has
     * to be a file.
     */
    private static void writeForTheAudit(java.util.Collection<String> lines) {
        java.nio.file.Path into = java.nio.file.Path.of("build", "jebol-surface.txt");
        try {
            java.nio.file.Files.createDirectories(into.getParent());
            java.nio.file.Files.write(into, lines);
        } catch (java.io.IOException unwritable) {
            throw new java.io.UncheckedIOException(unwritable);
        }
    }

    /** One function's arguments and refinements, or nothing if it is not one. */
    private static Optional<String> describe(Value value) {
        if (value instanceof OperatorValue operator) {
            return describe(operator.underlying());
        }
        StringBuilder shape = new StringBuilder();
        switch (value) {
            case NativeValue asNative -> {
                appendArguments(shape, asNative.parameters());
                // Declared separately from the parameters, so a refinement
                // that takes no argument would otherwise not appear at all.
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

    /**
     * The datatypes a parameter accepts, spelled as Rebol spells them.
     *
     * <p>Left off when the parameter accepts everything, because that is how
     * Rebol declares an unconstrained argument too, and a list of fifty-eight
     * names would bury the ones that matter.
     */
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
