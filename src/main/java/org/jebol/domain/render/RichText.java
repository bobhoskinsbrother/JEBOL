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
    private WhatTheNextObjectIs theNextObject = WhatTheNextObjectIs.A_FONT;
    private Optional<ObjectValue> theCaretObject = Optional.empty();

    private enum WhatTheNextObjectIs { A_FONT, A_PARA, A_CARET }

    RichText(Colour penColour) {
        this.colour = penColour;
    }

    record Line(List<TextRun> runs, List<Integer> placeOfEachRun, TextLayout layout, TextCaret caret) {

        Optional<Integer> theRunAt(int placeInTheBlock) {
            int run = placeOfEachRun.indexOf(placeInTheBlock);
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
                case AnyWordValue command -> obeyTheWord(command);
                case ObjectValue fields -> takeTheObject(fields);
                case IntegerValue(long magnitude) -> size = magnitude;
                default -> {
                }
            }
        }
        Line withoutACaret = new Line(List.copyOf(runs), List.copyOf(places), layout, TextCaret.NONE);
        return new Line(withoutACaret.runs(), withoutACaret.placeOfEachRun(), layout,
                theCaretObject.map(fields -> theCaretIn(fields, withoutACaret)).orElse(TextCaret.NONE));
    }

    private void obeyTheWord(AnyWordValue command) {
        boolean turningItOn = !(command instanceof RefinementValue);
        theNextObject = WhatTheNextObjectIs.A_FONT;
        switch (command.canonical()) {
            case "bold", "b" -> bold = turningItOn;
            case "italic", "i" -> italic = turningItOn;
            case "para" -> theNextObject = WhatTheNextObjectIs.A_PARA;
            case "caret" -> theNextObject = WhatTheNextObjectIs.A_CARET;
            default -> {
            }
        }
    }

    private void takeTheObject(ObjectValue fields) {
        switch (theNextObject) {
            case A_PARA -> takeTheParaIn(fields);
            case A_CARET -> theCaretObject = Optional.of(fields);
            case A_FONT -> takeTheFontIn(fields);
        }
        theNextObject = WhatTheNextObjectIs.A_FONT;
    }

    private TextCaret theCaretIn(ObjectValue fields, Line line) {
        return new TextCaret(
                thePlaceNamedBy(theField(fields, "caret"), line),
                thePlaceNamedBy(theField(fields, "start"), line),
                thePlaceNamedBy(theField(fields, "end"), line));
    }

    private TextLines.RunAndCharacter thePlaceNamedBy(Optional<Value> field, Line line) {
        if (field.isEmpty() || !(field.get() instanceof AnyBlockValue pair) || pair.remaining().size() < 2) {
            return TextLines.RunAndCharacter.NOWHERE;
        }
        Optional<Integer> placeInTheBlock = switch (pair.remaining().get(0)) {
            case AnyBlockValue atTheString -> Optional.of(atTheString.index());
            case IntegerValue(long place) when place > 0 -> Optional.of((int) place);
            default -> Optional.empty();
        };
        Optional<Integer> character = switch (pair.remaining().get(1)) {
            case AnyStringValue atTheCaret -> Optional.of(atTheCaret.index() - 1);
            case IntegerValue(long place) when place > 0 -> Optional.of((int) place - 1);
            default -> Optional.empty();
        };
        Optional<Integer> run = placeInTheBlock.flatMap(line::theRunAt);
        return run.isPresent() && character.isPresent()
                ? new TextLines.RunAndCharacter(run.get(), character.get())
                : TextLines.RunAndCharacter.NOWHERE;
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
