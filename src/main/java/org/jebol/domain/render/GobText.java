package org.jebol.domain.render;

import org.jebol.domain.value.AnyBlockValue;
import org.jebol.domain.value.AnyStringValue;
import org.jebol.domain.value.BlockValue;
import org.jebol.domain.value.GobStorage;
import org.jebol.domain.value.GobValue;
import org.jebol.domain.value.Value;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class GobText {

    private static final int A_PLAIN_STRINGS_PLACE = 1;

    private final GobValue gob;
    private final RichText.Line line;
    private final TextLines lines;

    public GobText(GobValue gob, TextMeasure measure) {
        this.gob = gob;
        this.line = theLineOf(gob);
        this.lines = new TextLines(line.runs(), measure, line.layout().roomForEachLine(theGobsOwnBox()));
    }

    private RichText.Line theLineOf(GobValue written) {
        return switch (written.storage().contentKind()) {
            case TEXT -> written.storage().contentIfKind(GobStorage.Content.TEXT) instanceof AnyBlockValue rich
                    ? new RichText(Colour.BLACK).lineIn(rich)
                    : nothingWritten();
            case STRING -> written.storage().contentIfKind(GobStorage.Content.STRING) instanceof AnyStringValue plain
                    ? new RichText.Line(
                            List.of(new TextRun(plain.text(), Colour.BLACK,
                                    PaintInstruction.Writing.THE_ORDINARY_SIZE, false, false)),
                            List.of(A_PLAIN_STRINGS_PLACE), TextLayout.STANDARD, TextCaret.NONE)
                    : nothingWritten();
            default -> nothingWritten();
        };
    }

    private RichText.Line nothingWritten() {
        return new RichText.Line(List.of(), List.of(), TextLayout.STANDARD, TextCaret.NONE);
    }

    public TextLines lines() {
        return lines;
    }

    public TextLayout layout() {
        return line.layout();
    }

    public Placement theGobsOwnBox() {
        int wide = (int) Math.round(gob.storage().size().x());
        int high = (int) Math.round(gob.storage().size().y());
        return new Placement(0, 0, wide, high, ClipRectangle.wholeSurface(Math.max(1, wide), Math.max(1, high)),
                Placement.OPAQUE);
    }

    public int theRunAt(int placeInTheBlock) {
        return line.theRunAt(placeInTheBlock).orElse(line.runs().size());
    }

    public Optional<AnyBlockValue> theBlockAtTheCaret(TextLines.RunAndCharacter caret) {
        if (caret.isNowhere()) {
            return Optional.empty();
        }
        int place = line.placeOfEachRun().get(caret.run());
        List<Value> copied = new ArrayList<>(theRichTextAsWritten());
        if (copied.get(place - 1) instanceof AnyStringValue written) {
            copied.set(place - 1, written.atIndex(caret.character() + 1));
        }
        return Optional.of(BlockValue.block(copied).atIndex(place));
    }

    private List<Value> theRichTextAsWritten() {
        Value content = gob.storage().contentKind() == GobStorage.Content.TEXT
                ? gob.storage().contentIfKind(GobStorage.Content.TEXT)
                : gob.storage().contentIfKind(GobStorage.Content.STRING);
        if (content instanceof AnyBlockValue rich) {
            return rich.storage().snapshot();
        }
        return List.of(content);
    }
}
