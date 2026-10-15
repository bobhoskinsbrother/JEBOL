package org.jebol.domain.render;

import org.jebol.application.Bounds;
import org.jebol.application.Interpreter;
import org.jebol.domain.host.HostService;
import org.jebol.domain.value.GobValue;
import org.jebol.domain.value.ObjectValue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ARichTextCaretIsDrawnFromTheSourceTest {

    private final Interpreter interpreter = Interpreter.withBounds(
            Bounds.standard().granting(HostService.WINDOWS));

    private PaintInstruction.Writing theLineWrittenFor(String caretFields) {
        String source = """
                written: copy {abcd}
                labelled: make gob! [size: 200x40]
                labelled/text: reduce [
                    'font make object! [shadow: 2x2]
                    'caret make object! [caret: none start: none end: none]
                    written
                ]
                the-caret: select labelled/text 'caret
                do bind [%s] the-caret
                labelled""".formatted(caretFields);
        interpreter.defineFreshWordsIn(source);
        GobValue gob = (GobValue) interpreter.run(source).value();
        ObjectValue dialect = (ObjectValue) interpreter.run("system/dialects/draw").value();
        List<PaintInstruction.Writing> written = PaintList.of(gob, dialect).instructions().stream()
                .filter(PaintInstruction.Writing.class::isInstance)
                .map(PaintInstruction.Writing.class::cast)
                .toList();
        assertThat(written).hasSize(1);
        return written.getFirst();
    }

    @Test
    @DisplayName("a caret set in the rich text is carried to the renderer, before the character it names")
    void aSetCaretIsCarried() {
        TextCaret caret = theLineWrittenFor("""
                caret: reduce [at labelled/text 5 at written 3] start: none end: none""").caret();

        assertThat(caret.isShown()).isTrue();
        assertThat(caret.run()).isZero();
        assertThat(caret.character()).isEqualTo(2);
        assertThat(caret.marksASelection()).isFalse();
    }

    @Test
    @DisplayName("a caret whose place is unset is not drawn, as the 2010 GUI leaves it while a field has no focus")
    void anUnsetCaretIsNotDrawn() {
        assertThat(theLineWrittenFor("caret: [0 0] start: [0 0] end: [0 0]").caret().isShown()).isFalse();
    }

    @Test
    @DisplayName("a start and an end that differ mark the text between them")
    void aSelectionIsCarried() {
        TextCaret caret = theLineWrittenFor("""
                caret: reduce [at labelled/text 5 at written 4]
                start: reduce [at labelled/text 5 at written 2]
                end: reduce [at labelled/text 5 at written 4]""").caret();

        assertThat(caret.marksASelection()).isTrue();
        assertThat(caret.selectionFrom()).isEqualTo(new TextLines.RunAndCharacter(0, 1));
        assertThat(caret.selectionTo()).isEqualTo(new TextLines.RunAndCharacter(0, 3));
    }

    @Test
    @DisplayName("a start and an end at the same place mark nothing")
    void anEmptySelectionMarksNothing() {
        assertThat(theLineWrittenFor("""
                caret: reduce [at labelled/text 5 at written 2]
                start: reduce [at labelled/text 5 at written 2]
                end: reduce [at labelled/text 5 at written 2]""").caret().marksASelection()).isFalse();
    }

    @Test
    @DisplayName("the caret object is not a font, so the font's shadow before it still stands")
    void theCaretIsNotAFont() {
        assertThat(theLineWrittenFor("caret: [0 0] start: [0 0] end: [0 0]").layout().shadowAcross())
                .isEqualTo(2);
    }

    @Test
    @DisplayName("a gob with no caret in its text draws none")
    void noCaretNoneDrawn() {
        String source = """
                make gob! [size: 100x20 text: {plain}]""";
        interpreter.defineFreshWordsIn(source);
        GobValue gob = (GobValue) interpreter.run(source).value();
        PaintInstruction.Writing written = (PaintInstruction.Writing) PaintList.of(gob).instructions().getFirst();

        assertThat(written.caret().isShown()).isFalse();
    }
}
