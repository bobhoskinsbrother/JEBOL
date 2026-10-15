package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FormingAnErrorFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Test
    @DisplayName("the words come from the catalogue and not from the implementation")
    void theWordsComeFromTheCatalogue() {
        assertThat(answerTo("""
                {^/** Math error: attempt to divide by zero^/}
                    = form make error! [type: 'Math id: 'zero-divide]"""))
                .isEqualTo("#(true)");
    }

    @Test
    @DisplayName("a field the entry names is molded, and the prose around it is formed")
    void aFieldTheEntryNamesIsMoldedAndTheProseFormed() {
        assertThat(answerTo("""
                {^/** Script error: foo does not allow #(integer!) for its bar argument^/}
                    = form make error! [
                        type: 'Script id: 'expect-arg
                        arg1: 'foo arg2: 'bar arg3: #(integer!)
                    ]""")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("so a string argument keeps its quotes, as a molded string does")
    void aStringArgumentKeepsItsQuotes() {
        assertThat(answerTo("""
                {^/** User error: "plain text"^/} = form make error! {plain text}"""))
                .isEqualTo("#(true)");
    }

    @Test
    @DisplayName("and changing an argument changes the message")
    void changingAnArgumentChangesTheMessage() {
        assertThat(answerTo("""
                raised: make error! [type: 'Script id: 'no-value arg1: 'x]
                before: form raised
                raised/arg1: 'y
                reduce [
                    before = {^/** Script error: x has no value^/}
                    (form raised) = {^/** Script error: y has no value^/}
                ]""")).isEqualTo("[#(true) #(true)]");
    }

    @Test
    @DisplayName("a WHERE line and a NEAR line follow when the error carries them")
    void aWhereLineAndANearLineFollowWhenTheErrorCarriesThem() {
        assertThat(answerTo("""
                parse (form try [1 / 0]) [
                    {^/** Math error: attempt to divide by zero^/}
                    {** Where: / try} thru #"^/"
                    {** Near: / 0^/}
                ]""")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("and the chain records the natives a failure came up through")
    void theChainRecordsTheNativesAFailureCameUpThrough() {
        assertThat(answerTo("""
                raised: try [1 / 0]
                raised/where""")).isEqualTo("[/ try]");
        assertThat(answerTo("""
                inner: does [1 / 0]
                outer: does [inner]
                raised: try [outer]
                raised/where""")).isEqualTo("[/ inner outer try]");
        assertThat(answerTo("""
                raised: try [do [1 / 0]]
                raised/where""")).isEqualTo("[/ do try]");
    }
}
