package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DerivingOneFunctionFromAnotherFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String failureOf(String source) {
        return answerTo("e: try [" + source + """
                ]
                either error? e [reduce [e/type e/id]] [reduce ['ok]]""");
    }

    @Test
    @DisplayName("a built-in derived with a new specification answers that specification")
    void abuiltInDerivedWithANewSpecificationAnswersIt() {
        assertThat(answerTo("""
                asked: [{Mine} series [series! none!]]
                mine: make :tail? reduce [asked]
                equal? asked spec-of :mine""")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("and the original is left with the specification it always had")
    void theOriginalIsLeftAlone() {
        assertThat(answerTo("""
                before: mold spec-of :tail?
                mine: make :tail? [[{Mine} series [series! none!]]]
                before = mold spec-of :tail?""")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("the derived one is a different value from the original")
    void thederivedOneIsADifferentValue() {
        assertThat(answerTo("""
                same? :tail? make :tail? [[{Mine} series [series!]]]"""))
                .isEqualTo("#(false)");
    }

    @Test
    @DisplayName("it keeps the behaviour when no new body is given")
    void itKeepsTheBehaviourWithoutANewBody() {
        assertThat(answerTo("""
                mine: make :tail? [[{Mine} series [series! none!]]]
                reduce [mine tail [1 2] mine [1 2]]""")).isEqualTo("[#(true) #(false)]");
    }

    @Test
    @DisplayName("the derived types are checked, not merely documented")
    void thederivedTypesAreChecked() {
        assertThat(failureOf("""
                narrow: make :tail? [[{Mine} series [block!]]]
                narrow "text\""""))
                .as("a string passes the original's types and not the derived ones")
                .isEqualTo("[Script expect-arg]");
        assertThat(failureOf("""
                tail? "text\""""))
                .as("the original takes a string quite happily")
                .isEqualTo("[ok]");
    }

    @Test
    @DisplayName("a written function derives the same way")
    void awrittenFunctionDerivesTheSameWay() {
        assertThat(answerTo("""
                doubled: func [a] [a * 2]
                asked: [{Mine} a [integer!]]
                mine: make :doubled reduce [asked]
                reduce [equal? asked spec-of :mine  mold body-of :mine  mine 5]"""))
                .isEqualTo("""
                        [#(true) "[a * 2]" 10]""");
    }

    @Test
    @DisplayName("and a new body replaces what it does")
    void anewBodyReplacesWhatItDoes() {
        assertThat(answerTo("""
                doubled: func [a] [a * 2]
                tripled: make :doubled [[{Mine} a] [a * 3]]
                tripled 5""")).isEqualTo("15");
    }

    @Test
    @DisplayName("a star where the specification goes keeps the original's")
    void astarKeepsTheOriginalSpecification() {
        assertThat(answerTo("""
                doubled: func [a] [a * 2]
                mine: make :doubled [* [a * 4]]
                reduce [mold spec-of :mine  mine 5]""")).isEqualTo("""
                        ["[a]" 20]""");
    }

    @Test
    @DisplayName("EMPTY? is the library deriving TAIL?, and reads as its own")
    void emptyIsTheLibraryDerivingTail() {
        assertThat(answerTo("mold spec-of :empty?"))
                .as("mezz-series.reb widens TAIL? with object! and none!")
                .contains("object!")
                .contains("none!");
        assertThat(answerTo("""
                reduce [empty? none  empty? []  empty? [1]]"""))
                .isEqualTo("[#(true) #(true) #(false)]");
    }
}
