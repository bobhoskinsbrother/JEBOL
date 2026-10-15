package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CopyingAnErrorFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Test
    @DisplayName("COPY of an error answers a second error, not the one it was given")
    void copyAnswersASecondError() {
        assertThat(answerTo("""
                raised: try [1 / 0]
                taken: copy raised
                reduce [same? taken raised  error? taken  taken/id  taken/code]"""))
                .isEqualTo("[#(false) #(true) zero-divide 400]");
    }

    @Test
    @DisplayName("and writing a field of the copy leaves the original alone")
    void writingAFieldOfTheCopyLeavesTheOriginalAlone() {
        assertThat(answerTo("""
                raised: try [1 / 0]
                taken: copy raised
                taken/id: 'renamed
                reduce [taken/id raised/id]""")).isEqualTo("[renamed zero-divide]");
    }

    @Test
    @DisplayName("the fields themselves are shared until /DEEP says otherwise")
    void theFieldsAreSharedUntilDeepSaysOtherwise() {
        assertThat(answerTo("""
                raised: make error! [type: 'User id: 'message arg1: [1 2]]
                shallow: copy raised
                deeply: copy/deep raised
                reduce [
                    same? raised/arg1 shallow/arg1
                    same? raised/arg1 deeply/arg1
                ]""")).isEqualTo("[#(true) #(false)]");
    }

    @Test
    @DisplayName("/PART names nothing on a thing with no order, so it is refused")
    void partNamesNothingOnAThingWithNoOrder() {
        assertThat(answerTo("""
                collect [
                    foreach subject reduce [make object! [a: 1]  try [1 / 0]] [
                        keep either error? e: try [copy/part subject 2] [
                            e/id
                        ] ['accepted]
                    ]
                ]""")).isEqualTo("[bad-refines bad-refines]");
    }
}
