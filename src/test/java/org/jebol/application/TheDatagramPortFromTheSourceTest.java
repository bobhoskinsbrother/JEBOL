package org.jebol.application;

import org.jebol.adapter.host.JavaSockets;
import org.jebol.domain.host.HostService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class TheDatagramPortFromTheSourceTest {

    private static final AtomicInteger NEXT = new AtomicInteger(41_500);

    private static int aFreePortNumber() {
        return NEXT.incrementAndGet();
    }

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.writingTo(
                message -> { },
                Bounds.standard().granting(HostService.NETWORK));
        interpreter.useNetwork(new JavaSockets());
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String withoutTheGrant(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Nested
    @DisplayName("binding, which is what opening one does")
    class Binding {

        @Test
        @DisplayName("answers a port")
        void answersAPort() {
            assertThat(answerTo("port? open udp://:" + aFreePortNumber()))
                    .isEqualTo("#(true)");
        }

        @Test
        @DisplayName("it is open once bound and shut once closed")
        void openThenClosed() {
            assertThat(answerTo("""
                    p: open udp://:%d
                    was: open? p
                    close p
                    reduce [was open? p]""".formatted(aFreePortNumber())))
                    .isEqualTo("[#(true) #(false)]");
        }

        @Test
        @DisplayName("a number another socket already holds is refused")
        void aNumberAlreadyHeldIsRefused() {
            int held = aFreePortNumber();
            assertThat(answerTo("""
                    first-one: open udp://:%d
                    failure: try [open udp://:%d]
                    close first-one
                    failure/id""".formatted(held, held))).isEqualTo("no-connect");
        }
    }

    @Nested
    @DisplayName("sending and receiving, over the loopback")
    class SendingAndReceiving {

        @Test
        @DisplayName("a datagram written at one end arrives at the other")
        void aDatagramReachesThePeer() {
            int listening = aFreePortNumber();
            assertThat(answerTo("""
                    here: open udp://:%d
                    there: open udp://127.0.0.1:%d
                    write there "hello udp"
                    read here
                    got: copy here/data
                    close here close there
                    to string! got""".formatted(listening, listening)))
                    .isEqualTo("\"hello udp\"");
        }

        @Test
        @DisplayName("reading answers the port, not the bytes, as the C does")
        void readingAnswersThePort() {
            int listening = aFreePortNumber();
            assertThat(answerTo("""
                    here: open udp://:%d
                    there: open udp://127.0.0.1:%d
                    write there "x"
                    answered: type? read here
                    close here close there
                    answered""".formatted(listening, listening))).isEqualTo("#(port!)");
        }

        @Test
        @DisplayName("what arrived is a binary in the port's data")
        void theDatagramLandsInTheData() {
            int listening = aFreePortNumber();
            assertThat(answerTo("""
                    here: open udp://:%d
                    there: open udp://127.0.0.1:%d
                    write there "AB"
                    read here
                    got: copy here/data
                    close here close there
                    reduce [type? got got]""".formatted(listening, listening)))
                    .isEqualTo("[#(binary!) #{4142}]");
        }

        @Test
        @DisplayName("two datagrams append cleanly, with nothing wedged between")
        void twoDatagramsBothLand() {
            int listening = aFreePortNumber();
            assertThat(answerTo("""
                    here: open udp://:%d
                    there: open udp://127.0.0.1:%d
                    write there "one"
                    read here
                    write there "two"
                    read here
                    got: copy here/data
                    close here close there
                    to string! got""".formatted(listening, listening)))
                    .isEqualTo("\"onetwo\"");
        }

        @Test
        @DisplayName("an empty datagram is a datagram")
        void anEmptyDatagram() {
            int listening = aFreePortNumber();
            assertThat(answerTo("""
                    here: open udp://:%d
                    there: open udp://127.0.0.1:%d
                    write there ""
                    read here
                    got: length? here
                    close here close there
                    got""".formatted(listening, listening))).isEqualTo("0");
        }
    }

    @Nested
    @DisplayName("writing")
    class Writing {

        @Test
        @DisplayName("answers the port so writes chain")
        void writingAnswersThePort() {
            assertThat(answerTo("""
                    p: open udp://127.0.0.1:%d
                    answered: port? write p "x"
                    close p
                    answered""".formatted(aFreePortNumber()))).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("and leaves none in the data, because the write is done")
        void writingLeavesNoneInTheData() {
            assertThat(answerTo("""
                    p: open udp://127.0.0.1:%d
                    write p "x"
                    left: type? p/data
                    close p
                    left""".formatted(aFreePortNumber()))).isEqualTo("#(none!)");
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"5", "0", "-1", "1.5", "[a b]", "none", "true",
                "quote word", "10:30"})
        void anythingButTextAndBytesIsRefused(String written) {
            assertThat(answerTo("""
                    p: open udp://127.0.0.1:%d
                    failure: try [write p %s]
                    close p
                    failure/id""".formatted(aFreePortNumber(), written)))
                    .isEqualTo("invalid-port-arg");
        }
    }

    @Nested
    @DisplayName("what the port reports about itself")
    class WhatItReports {

        @Test
        @DisplayName("nothing buffered is a length of zero")
        void nothingBufferedIsZero() {
            assertThat(answerTo("""
                    p: open udp://:%d
                    measured: length? p
                    close p
                    measured""".formatted(aFreePortNumber()))).isEqualTo("0");
        }

        @Test
        @DisplayName("and after a read it is how many bytes arrived")
        void afterAReadItIsTheByteCount() {
            int listening = aFreePortNumber();
            assertThat(answerTo("""
                    here: open udp://:%d
                    there: open udp://127.0.0.1:%d
                    write there "abcde"
                    read here
                    measured: length? here
                    close here close there
                    measured""".formatted(listening, listening))).isEqualTo("5");
        }
    }

    @Nested
    @DisplayName("the network grant, which binding needs as much as connecting")
    class TheGrant {

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"open udp://:41999", "read udp://:41999",
                "write udp://:41999 \"x\""})
        void withoutTheGrantItRefuses(String written) {
            assertThat(withoutTheGrant("failure: try [" + written + "] failure/id"))
                    .isEqualTo("no-service");
        }
    }
}
