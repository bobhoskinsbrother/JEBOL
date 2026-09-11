package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The one queue everything that happens goes on, which is itself a port.
 *
 * <p>{@code system/ports/system}. Its STATE is the events that have happened
 * and not yet been dealt with, and its DATA is the ports that have woken since
 * the wait began. {@code Event_Actor} in {@code p-event.c} serves it, and what
 * it serves are the block actions, pointed at STATE: INSERT and APPEND put an
 * event on and answer the port, LENGTH? counts, CLEAR empties, PICK reads.
 *
 * <p>One queue rather than one per connection, and that is the part worth
 * knowing. An event's port is not always the port somebody is waiting on:
 * {@code read https://} waits on the TLS port and the events come from the TCP
 * port underneath it. The TLS protocol reads those and, when it has a whole
 * record, puts an event of its own on the queue naming the port its caller is
 * actually waiting for -- {@code insert system/ports/system make event! [type:
 * 'close port: parent]} is one of them, written in {@code prot-tls.reb}. With a
 * list per connection there is nowhere for a protocol to put that.
 *
 * <p>Every expectation was run against a real 3.22.5 first.
 */
class TheSystemPortFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        String whole = "sport: system/ports/system\n" + source;
        interpreter.defineFreshWordsIn(whole);
        return interpreter.display(interpreter.run(whole));
    }

    @Test
    @DisplayName("it is a port, and its queue and wake list are blocks")
    void itIsAPortWithTwoBlocks() {
        assertThat(answerTo("""
                reduce [port? sport  block? sport/state  block? sport/data
                        length? sport/state]"""))
                .isEqualTo("[#(true) #(true) #(true) 0]");
    }

    @Test
    @DisplayName("INSERT puts an event on and answers the port")
    void insertPutsAnEventOnAndAnswersThePort() {
        assertThat(answerTo("""
                reduce [
                    same? sport insert sport make event! [type: 'read]
                    length? sport
                    mold/flat sport/state
                ]""")).isEqualTo("""
                        [#(true) 1 "[make event! [type: 'read]]"]""");
    }

    @Test
    @DisplayName("and APPEND does too, at the other end")
    void appendDoesTooAtTheOtherEnd() {
        assertThat(answerTo("""
                append sport make event! [type: 'read]
                append sport make event! [type: 'close]
                mold/flat sport/state""")).isEqualTo("""
                        {[make event! [type: 'read] make event! [type: 'close]]}""");
        assertThat(answerTo("""
                insert sport make event! [type: 'read]
                insert sport make event! [type: 'close]
                mold/flat sport/state""")).isEqualTo("""
                        {[make event! [type: 'close] make event! [type: 'read]]}""");
    }

    @Test
    @DisplayName("PICK reads one, and past the end reads none")
    void pickReadsOneAndPastTheEndReadsNone() {
        assertThat(answerTo("""
                append sport make event! [type: 'read]
                append sport make event! [type: 'close]
                reduce [mold/flat pick sport 2  pick sport 9]"""))
                .isEqualTo("""
                        ["make event! [type: 'close]" _]""");
    }

    @Test
    @DisplayName("CLEAR empties the queue and answers the port")
    void clearEmptiesTheQueueAndAnswersThePort() {
        assertThat(answerTo("""
                append sport make event! [type: 'read]
                reduce [port? clear sport  length? sport]"""))
                .isEqualTo("[#(true) 0]");
    }

    /** Only events go on it, so a protocol cannot leave anything else there. */
    @Test
    @DisplayName("and anything that is not an event is refused")
    void anythingThatIsNotAnEventIsRefused() {
        assertThat(answerTo("""
                e: try [insert sport 42] reduce [e/id e/arg1]"""))
                .isEqualTo("[invalid-arg 42]");
        assertThat(answerTo("""
                e: try [append sport "not an event"] e/id"""))
                .isEqualTo("invalid-arg");
    }

    /**
     * REMOVE is not one of the actions {@code Event_Actor} names, so it falls
     * to the default and is refused by name. The queue is emptied with CLEAR
     * or drained by waiting, and nothing takes one event off it by hand.
     */
    @Test
    @DisplayName("REMOVE is not one of its actions")
    void removeIsNotOneOfItsActions() {
        assertThat(answerTo("e: try [remove sport] e/id"))
                .isEqualTo("no-port-action");
    }
}
