package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MakingATaskFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static final String THE_EMPTY_HEADER = """
            make task! [
                title: _
                header: _
                parent: _
                path: _
                args: _
            ]""";

    @Test
    @DisplayName("a task is a task, and is not an object")
    void aTaskIsATaskAndNotAnObject() {
        assertThat(answerTo("""
                made: make task! []
                reduce [type? made  task? made  object? made]"""))
                .isEqualTo("[#(task!) #(true) #(false)]");
    }

    @Test
    @DisplayName("its header is the same five fields whatever the block said")
    void itsHeaderIsTheSameFiveFields() {
        assertThat(answerTo("make task! []")).isEqualTo(THE_EMPTY_HEADER);
        assertThat(answerTo("make task! [1 / 0]")).isEqualTo(THE_EMPTY_HEADER);
        assertThat(answerTo("""
                made: make task! [title: "not a spec"]
                made/title""")).isEqualTo("_");
        assertThat(answerTo("words-of make task! []"))
                .isEqualTo("[title header parent path args]");
    }

    @Test
    @DisplayName("a first block is a spec, and its set-words fill the header")
    void aFirstBlockIsASpec() {
        assertThat(answerTo("""
                made: make task! [[title: "spec"] [1 + 1]]
                made/title""")).isEqualTo("""
                        "spec\"""");
        assertThat(answerTo("""
                made: make task! [[] []]
                made/title""")).isEqualTo("_");
    }

    @Test
    @DisplayName("a spec with no body after it, or anything that is not a block, is refused")
    void aSpecWithNoBodyOrSomethingElseIsRefused() {
        assertThat(answerTo("""
                collect [
                    foreach given reduce [
                        [[title: "spec"]]  10  "x"  make task! []
                    ] [
                        keep either error? e: try [make task! given] [e/id] ['made]
                    ]
                    keep either error? e: try [to task! []] [e/id] ['made]
                ]""")).isEqualTo("""
                        [bad-make-arg bad-make-arg bad-make-arg bad-make-arg \
                        bad-make-arg]""");
    }

    @Test
    @DisplayName("a field can be written, and one it has not got cannot be read")
    void aFieldCanBeWrittenAndOneItHasNotGotCannotBeRead() {
        assertThat(answerTo("""
                made: make task! []
                made/title: "set"
                reduce [made/title  either error? e: try [made/nosuchfield] [e/id] ['read]]"""))
                .isEqualTo("""
                        ["set" invalid-path]""");
    }

    @Test
    @DisplayName("it molds as its header whether it is molded or formed")
    void itMoldsAsItsHeaderWhetherMoldedOrFormed() {
        assertThat(answerTo("""
                made: make task! []
                (mold made) = form made""")).isEqualTo("#(true)");
        assertThat(answerTo("""
                made: make task! []
                (mold made) = append copy "" made""")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("and DO answers the task rather than running its body here")
    void doAnswersTheTaskRatherThanRunningItsBody() {
        assertThat(answerTo("""
                reduce [
                    task? do make task! []
                    task? do make task! [1 / 0]
                    string? mold make task! []
                    string? append copy "" make task! []
                ]""")).isEqualTo("[#(true) #(true) #(true) #(true)]");
    }
}
