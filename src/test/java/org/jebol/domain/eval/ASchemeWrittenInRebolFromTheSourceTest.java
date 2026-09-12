package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ASchemeWrittenInRebolFromTheSourceTest {

    private static final String A_COUNTING_SCHEME = """
            sys/make-scheme [
                title: "A counter"
                name: 'tally
                actor: [
                    open:   func [port][port/data: 0  port]
                    open?:  func [port][integer? port/data]
                    close:  func [port][port/data: none  port]
                    pick:   func [port key][reduce ['picked key port/data]]
                    poke:   func [port key value][port/data: value  port]
                    update: func [port][port/data: port/data + 1  port]
                ]
            ]
            """;

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String answerToScheming(String source) {
        return answerTo(A_COUNTING_SCHEME + source);
    }

    @Test
    @DisplayName("the scheme registers, keeping the actor it was written with")
    void theSchemeRegistersKeepingItsActor() {
        assertThat(answerToScheming("""
                reduce [
                    true? find words-of system/schemes 'tally
                    type? system/schemes/tally/actor
                ]""")).isEqualTo("[#(true) #(object!)]");
    }

    @Test
    @DisplayName("opening one runs the actor's own OPEN")
    void openingOneRunsTheActorsOwnOpen() {
        assertThat(answerToScheming("""
                counter: open [scheme: 'tally]
                reduce [port? counter  counter/data]""")).isEqualTo("[#(true) 0]");
    }

    @Test
    @DisplayName("and a URL is the same doorway spelled differently")
    void aUrlIsTheSameDoorwaySpelledDifferently() {
        assertThat(answerToScheming("""
                counter: open tally://
                reduce [port? counter  counter/data]""")).isEqualTo("[#(true) 0]");
    }

    @Test
    @DisplayName("every action reaches the function of its own name")
    void everyActionReachesTheFunctionOfItsOwnName() {
        assertThat(answerToScheming("""
                counter: open [scheme: 'tally]
                reduce [
                    open? counter
                    (poke counter 1 42  counter/data)
                    pick counter 'akey
                    (update counter  counter/data)
                ]""")).isEqualTo("""
                        [#(true) 42 [picked akey 42] 43]""");
    }

    @Test
    @DisplayName("and CLOSE is the actor's too, so OPEN? answers on what it left")
    void closeIsTheActorsToo() {
        assertThat(answerToScheming("""
                counter: open [scheme: 'tally]
                close counter
                reduce [counter/data  open? counter]""")).isEqualTo("[_ #(false)]");
    }

    @Test
    @DisplayName("an action the actor has no function for is refused by name")
    void anActionTheActorHasNoFunctionForIsRefusedByName() {
        assertThat(answerToScheming("""
                counter: open [scheme: 'tally]
                e: try [read counter]
                reduce [e/id  type? e/arg1  e/arg1]"""))
                .isEqualTo("[no-port-action #(set-word!) read:]");
        assertThat(answerToScheming("""
                counter: open [scheme: 'tally]
                e: try [write counter #{00}] e/id""")).isEqualTo("no-port-action");
        assertThat(answerToScheming("""
                counter: open [scheme: 'tally]
                e: try [select counter 'k] e/id""")).isEqualTo("no-port-action");
    }

    @Test
    @DisplayName("and an actor that is neither a word nor an object is invalid")
    void anActorThatIsNeitherIsInvalid() {
        assertThat(answerTo("""
                sys/make-scheme [title: "Broken" name: 'broken actor: 42]
                e: try [open [scheme: 'broken]] e/id""")).isEqualTo("invalid-actor");
    }

    private static final String A_TELLING_SCHEME = """
            sys/make-scheme [
                title: "A teller"
                name: 'telling
                actor: [
                    open:    func [port][port/data: 0  port]
                    copy:    func [port]["what the actor copied"]
                    length?: func [port][42]
                    query:   func [port field][reduce ['asked field]]
                    read:    func [
                        port
                        /binary /all /lines /string
                        /part length /seek index
                    ][
                        reduce ['read binary all lines string length index]
                    ]
                ]
            ]
            """;

    private static String answerToTelling(String source) {
        return answerTo(A_TELLING_SCHEME
                + "teller: open [scheme: 'telling]\n" + source);
    }

    @Test
    @DisplayName("COPY, LENGTH? and QUERY are the actor's too")
    void copyLengthAndQueryAreTheActorsToo() {
        assertThat(answerToTelling("""
                reduce [copy teller  length? teller  query teller 'size]"""))
                .isEqualTo("""
                        ["what the actor copied" 42 [asked size]]""");
    }

    @Test
    @DisplayName("and the refinements the caller asked for reach it")
    void theRefinementsTheCallerAskedForReachIt() {
        assertThat(answerToTelling("read teller"))
                .isEqualTo("[read _ _ _ _ _ _]");
        assertThat(answerToTelling("read/binary teller"))
                .isEqualTo("[read #(true) _ _ _ _ _]");
        assertThat(answerToTelling("read/all teller"))
                .isEqualTo("[read _ #(true) _ _ _ _]");
        assertThat(answerToTelling("read/lines teller"))
                .isEqualTo("[read _ _ #(true) _ _ _]");
        assertThat(answerToTelling("read/string teller"))
                .isEqualTo("[read _ _ _ #(true) _ _]");
    }

    @Test
    @DisplayName("and what a refinement carries arrives with it")
    void whatARefinementCarriesArrivesWithIt() {
        assertThat(answerToTelling("read/part teller 3"))
                .isEqualTo("[read _ _ _ _ 3 _]");
        assertThat(answerToTelling("read/seek teller 7"))
                .isEqualTo("[read _ _ _ _ _ 7]");
        assertThat(answerToTelling("read/part/seek teller 3 7"))
                .isEqualTo("[read _ _ _ _ 3 7]");
    }

    @Test
    @DisplayName("a built-in actor still needs its service granted")
    void aBuiltInActorStillNeedsItsServiceGranted() {
        assertThat(answerTo("""
                e: try [open %somewhere.txt] e/id""")).isEqualTo("no-service");
        assertThat(answerTo("type? system/schemes/file/actor")).isEqualTo("#(word!)");
    }
}
