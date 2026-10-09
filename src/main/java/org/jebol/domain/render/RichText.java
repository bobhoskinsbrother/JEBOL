package org.jebol.domain.render;

import org.jebol.domain.value.AnyBlockValue;
import org.jebol.domain.value.AnyDecimalValue;
import org.jebol.domain.value.AnyStringValue;
import org.jebol.domain.value.AnyWordValue;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.ObjectValue;
import org.jebol.domain.value.PairValue;
import org.jebol.domain.value.RefinementValue;
import org.jebol.domain.value.TupleValue;
import org.jebol.domain.value.Value;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

final class RichText {

    private Colour colour;
    private double size = PaintInstruction.Writing.THE_ORDINARY_SIZE;
    private boolean bold;
    private boolean italic;
    private TextLayout layout = TextLayout.STANDARD;
    private boolean theNextObjectIsAPara;

    RichText(Colour penColour) {
        this.colour = penColour;
    }

    record Line(List<TextRun> runs, TextLayout layout) {
    }

    Line lineIn(AnyBlockValue block) {
        List<TextRun> runs = new ArrayList<>();
        for (Value item : block.remaining()) {
            switch (item) {
                case AnyStringValue said when !said.text().isEmpty() ->
                        runs.add(new TextRun(said.text(), colour, size, bold, italic));
                case TupleValue parts -> colour = Colour.ofTuple(parts);
                case AnyWordValue command -> obeyTheWord(command);
                case ObjectValue fields -> takeTheObject(fields);
                case IntegerValue(long magnitude) -> size = magnitude;
                default -> {
                }
            }
        }
        return new Line(List.copyOf(runs), layout);
    }

    private void obeyTheWord(AnyWordValue command) {
        boolean turningItOn = !(command instanceof RefinementValue);
        theNextObjectIsAPara = false;
        switch (command.canonical()) {
            case "bold", "b" -> bold = turningItOn;
            case "italic", "i" -> italic = turningItOn;
            case "para" -> theNextObjectIsAPara = true;
            default -> {
            }
        }
    }

    private void takeTheObject(ObjectValue fields) {
        if (theNextObjectIsAPara) {
            takeTheParaIn(fields);
        } else {
            takeTheFontIn(fields);
        }
        theNextObjectIsAPara = false;
    }

    private void takeTheFontIn(ObjectValue fields) {
        theField(fields, "size").flatMap(this::aSizeIn).ifPresent(asked -> size = asked);
        theField(fields, "color").filter(TupleValue.class::isInstance)
                .map(TupleValue.class::cast)
                .ifPresent(parts -> colour = Colour.ofTuple(parts));
        theField(fields, "style").ifPresent(this::takeTheStyle);
        PairValue shadow = theField(fields, "shadow").flatMap(this::theShadowsOffsetIn)
                .orElse(PairValue.of(0, 0));
        layout = layout.withShadow(wholePixels(shadow.x()), wholePixels(shadow.y()));
    }

    private Optional<PairValue> theShadowsOffsetIn(Value shadow) {
        return switch (shadow) {
            case PairValue offset -> Optional.of(offset);
            case AnyBlockValue several when !several.remaining().isEmpty()
                    && several.remaining().getFirst() instanceof PairValue offset -> Optional.of(offset);
            default -> Optional.empty();
        };
    }

    private void takeTheParaIn(ObjectValue fields) {
        theField(fields, "origin").filter(PairValue.class::isInstance).map(PairValue.class::cast)
                .ifPresent(origin -> layout = layout.withOrigin(wholePixels(origin.x()), wholePixels(origin.y())));
        theField(fields, "margin").filter(PairValue.class::isInstance).map(PairValue.class::cast)
                .ifPresent(margin -> layout = layout.withMargin(wholePixels(margin.x()), wholePixels(margin.y())));
        TextAlignment across = theField(fields, "align").flatMap(this::theWordsSpelling)
                .flatMap(TextAlignment::spelt).orElse(TextAlignment.LEFT);
        TextVerticalAlignment down = theField(fields, "valign").flatMap(this::theWordsSpelling)
                .flatMap(TextVerticalAlignment::spelt).orElse(TextVerticalAlignment.TOP);
        layout = layout.aligned(across, down);
    }

    private Optional<String> theWordsSpelling(Value word) {
        return word instanceof AnyWordValue said ? Optional.of(said.canonical()) : Optional.empty();
    }

    private int wholePixels(double measurement) {
        return (int) measurement;
    }

    private Optional<Double> aSizeIn(Value given) {
        return switch (given) {
            case IntegerValue(long magnitude) -> Optional.of((double) magnitude);
            case AnyDecimalValue number -> Optional.of(number.quantity());
            default -> Optional.empty();
        };
    }

    private void takeTheStyle(Value style) {
        List<Value> named = style instanceof AnyBlockValue several ? several.remaining() : List.of(style);
        bold = named.stream().anyMatch(word -> spells(word, "bold"));
        italic = named.stream().anyMatch(word -> spells(word, "italic"));
    }

    private boolean spells(Value word, String spelling) {
        return word instanceof AnyWordValue said && said.canonical().equals(spelling);
    }

    private Optional<Value> theField(ObjectValue fields, String name) {
        return fields.context().holds(name)
                ? Optional.of(fields.context().ownSlotFor(name).value())
                : Optional.empty();
    }
}
