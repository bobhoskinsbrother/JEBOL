package org.jebol.application;

import org.jebol.adapter.host.FileSystemPort;
import org.jebol.domain.host.HostService;
import org.jebol.domain.host.OutputPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class WhatEachBuiltInSchemeServesFromTheSourceTest {

    private static final String SAYS_WHAT_HAPPENED = """
            idOf: func [code][
                set/any 'answer try code
                either error? :answer [answer/id] ['no-error]
            ]
            refusalOf: func [code][
                set/any 'answer try code
                either error? :answer [reduce [answer/id answer/arg1]] [reduce ['no-error]]
            ]
            """;

    private static Interpreter anInterpreterReading(Path directory) throws IOException {
        Files.writeString(directory.resolve("held.txt"), "carried");
        return Interpreter.createOn(
                AnInterpreter.standardPorts().withFiles(FileSystemPort.rootFileSystem(directory)),
                OutputPort.discard(),
                Bounds.standard().grant(HostService.FILES));
    }

    private static String answerTo(Interpreter interpreter, String source) {
        String whole = SAYS_WHAT_HAPPENED + source;
        interpreter.defineNewWordsIn(whole);
        return interpreter.display(interpreter.run(whole));
    }

    private static String answerTo(String source) {
        return answerTo(AnInterpreter.create(), source);
    }

    @Nested
    @DisplayName("a file port, whose actor is p-file.c")
    class AFilePort {

        @ParameterizedTest(name = "{0} on a file port is no-port-action")
        @ValueSource(strings = {
                "pick p 1", "poke p 1 2", "put p 1 2", "select p 1", "insert p 1",
                "change p 1", "remove p", "take p", "find p 1", "flush p", "update p"})
        void refusesWhatTheFileActorDoesNotServe(String call, @TempDir Path directory)
                throws IOException {
            assertThat(answerTo(anInterpreterReading(directory),
                    "p: open %held.txt refusalOf [" + call + "]"))
                    .isEqualTo("[no-port-action " + call.split(" ")[0] + ":]");
        }

        @ParameterizedTest(name = "{0} on a file port answers")
        @ValueSource(strings = {
                "read p", "copy p", "length? p", "head p", "tail p", "head? p",
                "tail? p", "past? p", "next p", "back p", "skip p 1", "at p 1",
                "atz p 1", "index? p", "indexz? p", "clear p", "query p none",
                "open? p", "close p"})
        void servesWhatTheFileActorServes(String call, @TempDir Path directory)
                throws IOException {
            assertThat(answerTo(anInterpreterReading(directory),
                    "p: open %held.txt idOf [" + call + "]")).isEqualTo("no-error");
        }

        @Test
        @DisplayName("COPY answers the bytes from where the port stands, not the port")
        void copiesTheBytesBehindAFilePort(@TempDir Path directory) throws IOException {
            assertThat(answerTo(anInterpreterReading(directory), """
                    p: open %held.txt
                    whole: copy p
                    head p skip p 3
                    rest: copy p
                    head p
                    part: copy/part p 3
                    reduce [whole rest part]""")).isEqualTo("[#{63617272696564} #{72696564} #{636172}]");
        }

        @Test
        @DisplayName("and COPY leaves the port where READ would, past what it answered")
        void movesThePortOnAsReadDoes(@TempDir Path directory) throws IOException {
            assertThat(answerTo(anInterpreterReading(directory), """
                    p: open %held.txt
                    copy p
                    after-whole: index? p
                    head p
                    copy/part p 3
                    reduce [after-whole index? p]""")).isEqualTo("[8 4]");
        }

        @Test
        @DisplayName("HEAD?, TAIL? and PAST? read the position, where they refused a port before")
        void answersThePositionPredicates(@TempDir Path directory) throws IOException {
            assertThat(answerTo(anInterpreterReading(directory), """
                    p: open %held.txt
                    fresh: reduce [head? p tail? p past? p]
                    skip p 3
                    moved: reduce [head? p tail? p past? p]
                    tail p
                    ended: reduce [head? p tail? p past? p]
                    reduce [fresh moved ended]"""))
                    .isEqualTo("[[#(true) #(false) #(false)] "
                            + "[#(false) #(false) #(false)] "
                            + "[#(false) #(true) #(false)]]");
        }
    }

    @Nested
    @DisplayName("a directory port, whose actor serves ten verbs and no more")
    class ADirectoryPort {

        @ParameterizedTest(name = "{0} on a directory port is no-port-action")
        @ValueSource(strings = {
                "write p {x}", "append p {x}", "modify p 'x 1", "update p", "copy p",
                "pick p 1", "poke p 1 2", "put p 1 2", "select p 1", "insert p 1",
                "change p 1", "remove p", "take p", "find p 1", "flush p", "head p",
                "tail p", "head? p", "past? p", "next p", "back p", "skip p 1",
                "at p 1", "atz p 1", "index? p", "indexz? p"})
        void refusesWhatTheDirectoryActorDoesNotServe(String call, @TempDir Path directory)
                throws IOException {
            assertThat(answerTo(anInterpreterReading(directory),
                    "p: open %./ refusalOf [" + call + "]"))
                    .isEqualTo("[no-port-action " + call.split(" ")[0] + ":]");
        }

        @ParameterizedTest(name = "{0} on a directory port answers")
        @ValueSource(strings = {
                "read p", "open? p", "close p", "query p none", "length? p", "tail? p"})
        void servesWhatTheDirectoryActorServes(String call, @TempDir Path directory)
                throws IOException {
            assertThat(answerTo(anInterpreterReading(directory),
                    "p: open %./ idOf [" + call + "]")).isEqualTo("no-error");
        }
    }

    @Nested
    @DisplayName("a checksum port, which serves six verbs")
    class AChecksumPort {

        @ParameterizedTest(name = "{0} on a checksum port is no-port-action")
        @ValueSource(strings = {
                "append p {x}", "query p none", "modify p 'x 1", "copy p", "length? p",
                "pick p 1", "select p 1", "insert p 1", "remove p", "clear p", "take p",
                "find p 1", "flush p", "head p", "tail? p", "next p", "index? p"})
        void refusesWhatTheChecksumActorDoesNotServe(String call) {
            assertThat(answerTo("p: open checksum://md5 refusalOf [" + call + "]"))
                    .isEqualTo("[no-port-action " + call.split(" ")[0] + ":]");
        }

        @ParameterizedTest(name = "{0} on a checksum port answers")
        @ValueSource(strings = {"read p", "write p #{00}", "update p", "open? p", "close p"})
        void servesWhatTheChecksumActorServes(String call) {
            assertThat(answerTo("p: open checksum://md5 idOf [" + call + "]"))
                    .isEqualTo("no-error");
        }
    }

    @Nested
    @DisplayName("a closed transport port, which refuses everything but opening")
    class AClosedTransportPort {

        @ParameterizedTest(name = "{0} on a closed tcp port is not-open")
        @ValueSource(strings = {
                "read p", "write p {x}", "query p none", "length? p", "pick p 1",
                "insert p 1", "copy p", "find p 1", "head p", "take p", "delete p"})
        void refusesEverythingWhileClosed(String call) {
            assertThat(answerTo("p: make port! tcp://localhost:1 refusalOf [" + call + "]"))
                    .isEqualTo("[not-open tcp://localhost:1]");
        }

        @ParameterizedTest(name = "{0} is served while the port is closed")
        @ValueSource(strings = {"close p", "open? p", "update p"})
        void servesWhatNeedsNoConnection(String call) {
            assertThat(answerTo("p: make port! tcp://localhost:1 idOf [" + call + "]"))
                    .isEqualTo("no-error");
        }

        @Test
        @DisplayName("a closed udp port answers the same way, sharing the transport's actor")
        void refusesOnAClosedDatagramPortToo() {
            assertThat(answerTo("""
                    p: make port! udp://localhost:1
                    reduce [idOf [read p] idOf [insert p 1] idOf [open? p]]"""))
                    .isEqualTo("[not-open not-open no-error]");
        }
    }

    @Nested
    @DisplayName("an event queue, whose actor is p-event.c")
    class AnEventQueue {

        @Test
        @DisplayName("FIND falls through to no-port-action, as its case in the C does")
        void refusesFindOnAnEventQueue() {
            assertThat(answerTo("p: make port! event:// refusalOf [find p 1]"))
                    .isEqualTo("[no-port-action find:]");
        }

        @Test
        @DisplayName("POKE is served, and refuses anything that is not an event")
        void refusesPokingAnythingButAnEvent() {
            assertThat(answerTo("""
                    p: make port! event://
                    reduce [refusalOf [poke p 1 2] refusalOf [insert p 2]]"""))
                    .isEqualTo("[[invalid-arg 2] [invalid-arg 2]]");
        }

        @ParameterizedTest(name = "{0} on an event queue is no-port-action")
        @ValueSource(strings = {
                "read p", "write p {x}", "open? p", "query p none", "modify p 'x 1",
                "copy p", "put p 1 2", "select p 1", "change p 1", "remove p",
                "take p", "flush p", "head p", "tail? p", "next p", "index? p"})
        void refusesWhatTheEventActorDoesNotServe(String call) {
            assertThat(answerTo("p: make port! event:// refusalOf [" + call + "]"))
                    .isEqualTo("[no-port-action " + call.split(" ")[0] + ":]");
        }

        @Test
        @DisplayName("the system and callback queues answer as the event queue does")
        void answersTheSameOnTheOtherTwoQueues() {
            assertThat(answerTo("""
                    queued: make port! system://
                    minded: make port! callback://
                    reduce [
                        refusalOf [read queued]
                        refusalOf [read minded]
                        refusalOf [find queued 1]
                        idOf [length? queued]
                    ]""")).isEqualTo("[[no-port-action read:] "
                    + "[no-port-action read:] [no-port-action find:] no-error]");
        }
    }
}
