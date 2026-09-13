package org.jebol.domain.render;

import org.jebol.domain.value.BlockValue;
import org.jebol.domain.value.StringValue;
import org.jebol.domain.value.TupleValue;
import org.jebol.domain.value.Value;
import org.jebol.domain.value.WordValue;

import java.util.ArrayList;
import java.util.List;

final class RichText {

    private static final double HOW_WIDE_A_CHARACTER_RUNS =
            0.55;

    private RichText() {
    }

    record Run(String text, Colour colour, double size, boolean bold, boolean italic) {
    }

    static List<Run> runsIn(BlockValue block, Colour penColour) {
        List<Run> runs = new ArrayList<>();
        Colour colour = penColour;
        double size = PaintInstruction.Writing.THE_ORDINARY_SIZE;
        boolean bold = false;
        boolean italic = false;

        for (Value item : block.remaining()) {
            switch (item) {
                case StringValue said -> runs.add(
                        new Run(said.text(), colour, size, bold, italic));
                case TupleValue parts -> colour = Colour.ofTuple(parts);
                case WordValue named -> {
                    boolean turningItOn =
                            named.datatype() != org.jebol.domain.value.Datatype.REFINEMENT;
                    switch (named.canonical()) {
                        case "bold", "b" -> bold = turningItOn;
                        case "italic", "i" -> italic = turningItOn;
                        default -> {
                        }
                    }
                }
                default -> size = sizeOf(item, size);
            }
        }
        return List.copyOf(runs);
    }

    private static double sizeOf(Value item, double standing) {
        return item instanceof org.jebol.domain.value.IntegerValue whole
                ? whole.magnitude()
                : standing;
    }

    static double howWideItRunsOut(Run run) {
        double perCharacter = run.size() * HOW_WIDE_A_CHARACTER_RUNS;
        return run.text().length() * (run.bold() ? perCharacter * 1.05 : perCharacter);
    }
}
