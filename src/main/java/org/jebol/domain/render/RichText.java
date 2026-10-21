package org.jebol.domain.render;

import org.jebol.domain.value.AnyBlockValue;
import org.jebol.domain.value.AnyDecimalValue;
import org.jebol.domain.value.AnyStringValue;
import org.jebol.domain.value.AnyWordValue;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.LogicValue;
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
    private double size = PaintInstruction.Writing.ORDINARY_SIZE;
    private boolean bold;
    private boolean italic;
    private TextLayout layout = TextLayout.STANDARD;
    private NextObjectKind nextObject = NextObjectKind.FONT;
    private Optional<ObjectValue> caretObject = Optional.empty();

    private enum NextObjectKind { FONT, PARA, CARET }

    RichText(Colour penColour) {
        this.colour = penColour;
    }

    record Line(List<TextRun> runs, List<Integer> placeOfEachRun, TextLayout layout, TextCaret caret) {

        Optional<Integer> findRunAt(int placeInBlock) {
            int run = placeOfEachRun.indexOf(placeInBlock);
            return run < 0 ? Optional.empty() : Optional.of(run);
        }
    }

    Line lineIn(AnyBlockValue block) {
        List<TextRun> runs = new ArrayList<>();
        List<Integer> places = new ArrayList<>();
        List<Value> items = block.remaining();
        for (int offset = 0; offset < items.size(); offset++) {
            switch (items.get(offset)) {
                case AnyStringValue said -> {
                    runs.add(new TextRun(said.text(), colour, size, bold, italic));
                    places.add(block.index() + offset);
                }
                case TupleValue parts -> colour = Colour.ofTuple(parts);
                case AnyWordValue command -> obeyWord(command);
                case ObjectValue fields -> takeObject(fields);
                case IntegerValue(long magnitude) -> size = magnitude;
                default -> {
                }
            }
        }
        Line withoutCaret = new Line(List.copyOf(runs), List.copyOf(places), layout, TextCaret.NONE);
        return new Line(withoutCaret.runs(), withoutCaret.placeOfEachRun(), layout,
                caretObject.map(fields -> readCaretIn(fields, withoutCaret)).orElse(TextCaret.NONE));
    }

    private void obeyWord(AnyWordValue command) {
        boolean turningItOn = !(command instanceof RefinementValue);
        nextObject = NextObjectKind.FONT;
        switch (command.canonical()) {
            case "bold", "b" -> bold = turningItOn;
            case "italic", "i" -> italic = turningItOn;
            case "para" -> nextObject = NextObjectKind.PARA;
            case "caret" -> nextObject = NextObjectKind.CARET;
            default -> {
            }
        }
    }

    private void takeObject(ObjectValue fields) {
        switch (nextObject) {
            case PARA -> takeParaIn(fields);
            case CARET -> caretObject = Optional.of(fields);
            case FONT -> takeFontIn(fields);
        }
        nextObject = NextObjectKind.FONT;
    }

    private TextCaret readCaretIn(ObjectValue fields, Line line) {
        return new TextCaret(
                findPlaceNamedBy(readField(fields, "caret"), line),
                findPlaceNamedBy(readField(fields, "start"), line),
                findPlaceNamedBy(readField(fields, "end"), line));
    }

    private TextLines.RunAndCharacter findPlaceNamedBy(Optional<Value> field, Line line) {
        if (field.isEmpty() || !(field.get() instanceof AnyBlockValue pair) || pair.remaining().size() < 2) {
            return TextLines.RunAndCharacter.NOWHERE;
        }
        Optional<Integer> placeInBlock = switch (pair.remaining().get(0)) {
            case AnyBlockValue atString -> Optional.of(atString.index());
            case IntegerValue(long place) when place > 0 -> Optional.of((int) place);
            default -> Optional.empty();
        };
        Optional<Integer> character = switch (pair.remaining().get(1)) {
            case AnyStringValue atCaret -> Optional.of(atCaret.index() - 1);
            case IntegerValue(long place) when place > 0 -> Optional.of((int) place - 1);
            default -> Optional.empty();
        };
        Optional<Integer> run = placeInBlock.flatMap(line::findRunAt);
        return run.isPresent() && character.isPresent()
                ? new TextLines.RunAndCharacter(run.get(), character.get())
                : TextLines.RunAndCharacter.NOWHERE;
    }

    private void takeFontIn(ObjectValue fields) {
        readField(fields, "size").flatMap(this::readSizeIn).ifPresent(asked -> size = asked);
        readField(fields, "color").filter(TupleValue.class::isInstance)
                .map(TupleValue.class::cast)
                .ifPresent(parts -> colour = Colour.ofTuple(parts));
        readField(fields, "style").ifPresent(this::takeStyle);
        PairValue shadow = readField(fields, "shadow").flatMap(this::readShadowsOffsetIn)
                .orElse(PairValue.of(0, 0));
        layout = layout.withShadow(roundToWholePixels(shadow.x()), roundToWholePixels(shadow.y()));
    }

    private Optional<PairValue> readShadowsOffsetIn(Value shadow) {
        return switch (shadow) {
            case PairValue offset -> Optional.of(offset);
            case AnyBlockValue several when !several.remaining().isEmpty()
                    && several.remaining().getFirst() instanceof PairValue offset -> Optional.of(offset);
            default -> Optional.empty();
        };
    }

    private void takeParaIn(ObjectValue fields) {
        readField(fields, "origin").filter(PairValue.class::isInstance).map(PairValue.class::cast)
                .ifPresent(origin -> layout = layout.withOrigin(roundToWholePixels(origin.x()), roundToWholePixels(origin.y())));
        readField(fields, "margin").filter(PairValue.class::isInstance).map(PairValue.class::cast)
                .ifPresent(margin -> layout = layout.withMargin(roundToWholePixels(margin.x()), roundToWholePixels(margin.y())));
        TextAlignment across = readField(fields, "align").flatMap(this::readWordsSpelling)
                .flatMap(TextAlignment::findAlignmentSpelt).orElse(TextAlignment.LEFT);
        TextVerticalAlignment down = readField(fields, "valign").flatMap(this::readWordsSpelling)
                .flatMap(TextVerticalAlignment::findAlignmentSpelt).orElse(TextVerticalAlignment.TOP);
        layout = layout.align(across, down);
        readField(fields, "wrap?").filter(LogicValue.class::isInstance).map(LogicValue.class::cast)
                .ifPresent(wraps -> layout = layout.wrap(wraps.truth()));
    }

    private Optional<String> readWordsSpelling(Value word) {
        return word instanceof AnyWordValue said ? Optional.of(said.canonical()) : Optional.empty();
    }

    private int roundToWholePixels(double measurement) {
        return (int) measurement;
    }

    private Optional<Double> readSizeIn(Value given) {
        return switch (given) {
            case IntegerValue(long magnitude) -> Optional.of((double) magnitude);
            case AnyDecimalValue number -> Optional.of(number.quantity());
            default -> Optional.empty();
        };
    }

    private void takeStyle(Value style) {
        List<Value> named = style instanceof AnyBlockValue several ? several.remaining() : List.of(style);
        bold = named.stream().anyMatch(word -> spells(word, "bold"));
        italic = named.stream().anyMatch(word -> spells(word, "italic"));
    }

    private boolean spells(Value word, String spelling) {
        return word instanceof AnyWordValue said && said.canonical().equals(spelling);
    }

    private Optional<Value> readField(ObjectValue fields, String name) {
        return fields.context().holds(name)
                ? Optional.of(fields.context().ownSlotFor(name).value())
                : Optional.empty();
    }
}
