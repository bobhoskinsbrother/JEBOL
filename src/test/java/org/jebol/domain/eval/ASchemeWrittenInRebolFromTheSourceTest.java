package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A scheme whose actor is written in REBOL, which JEBOL could not open at all.
 *
 * <p>{@code Do_Port_Action} in {@code c-port.c}. Every port action goes to the
 * port's actor, and an actor is one of two things: a word naming something
 * built in, or an object of functions written in REBOL. JEBOL served only the
 * first, and refused every scheme whose name was not on a list of eight -- so
 * {@code sys/make-scheme} accepted a scheme, put it in {@code system/schemes}
 * with its actor intact, and then no port could ever be opened on it.
 *
 * <p>The distinction that matters is which of the two needs the host's
 * permission. A built-in actor is the way out of the interpreter and has to
 * have its service granted first. An actor written in REBOL is not a way out
 * of anything: whatever it reaches for, it reaches for by calling ordinary
 * words, and each of those asks the host for itself. So it opens without
 * asking for anything, and this test class needs no host at all.
 *
 * <p>Every expectation was read off a real 3.22.5 first.
 */
class ASchemeWrittenInRebolFromTheSourceTest {

    /**
     * A scheme that counts, with six of the ten actions and deliberately
     * without READ or WRITE, so what happens for an action the actor has no
     * function for can be asked.
     */
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

    /**
     * Each verb reaching the function of its own name, with the arguments it
     * was given -- {@code Redo_Func} hands the actor's function the same stack
     * the action was called on, so PICK's key and POKE's value arrive as they
     * were written.
     */
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

    /**
     * Refused by name, so a caller learns which verb this port does not do
     * rather than that something went wrong somewhere inside it. The name
     * arrives as a set-word, which is how the action table spells it.
     */
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

    /**
     * An actor that is neither a word nor an object is a scheme built wrongly
     * rather than a port used wrongly, and says so at the door: the refusal is
     * on OPEN, before any verb has been sent.
     */
    @Test
    @DisplayName("and an actor that is neither a word nor an object is invalid")
    void anActorThatIsNeitherIsInvalid() {
        assertThat(answerTo("""
                sys/make-scheme [title: "Broken" name: 'broken actor: 42]
                e: try [open [scheme: 'broken]] e/id""")).isEqualTo("invalid-actor");
    }

    /**
     * A scheme that answers the actions the counting one deliberately lacks,
     * and says what refinements it was called with.
     */
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

    /**
     * COPY, LENGTH? and QUERY are actions like any other, and {@code T_Port}
     * sends the lot to {@code Do_Port_Action}. Only MAKE, TO and REFLECT are
     * named as exceptions, and none of those acts on a port that is already
     * built.
     *
     * <p>Not a detail of the dispatch. Rebol's own HTTP ends a request with
     * {@code body: copy port}, meaning the response body, and a COPY that
     * duplicated the port object instead answered a port where the caller
     * wanted the page -- so {@code read http://example.com} gave back the port
     * it had just read through.
     */
    @Test
    @DisplayName("COPY, LENGTH? and QUERY are the actor's too")
    void copyLengthAndQueryAreTheActorsToo() {
        assertThat(answerToTelling("""
                reduce [copy teller  length? teller  query teller 'size]"""))
                .isEqualTo("""
                        ["what the actor copied" 42 [asked size]]""");
    }

    /**
     * The refinements go with the action. An actor's function declares its
     * own, and what the caller asked for is part of what the action was given.
     *
     * <p>Dropping them is quiet and total: HTTP's READ answers a decoded
     * string for {@code read}, the raw bytes for {@code read/binary} and a
     * three-part block for {@code read/all}, all out of one function reading
     * one response.
     */
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

    /** And the value a refinement carries arrives with it, not just the flag. */
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

    /**
     * The built-in half is unchanged: a scheme whose actor is a word still
     * needs the service that word names, and an interpreter given no
     * filesystem still refuses a file port.
     */
    @Test
    @DisplayName("a built-in actor still needs its service granted")
    void aBuiltInActorStillNeedsItsServiceGranted() {
        assertThat(answerTo("""
                e: try [open %somewhere.txt] e/id""")).isEqualTo("no-service");
        assertThat(answerTo("type? system/schemes/file/actor")).isEqualTo("#(word!)");
    }
}
