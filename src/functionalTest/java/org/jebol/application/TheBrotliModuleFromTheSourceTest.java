package org.jebol.application;

import org.jebol.adapter.host.FileSystemPort;
import org.jebol.domain.eval.compression.brotli.BrotliBlockSwitchStreams;
import org.jebol.domain.host.HostService;
import org.jebol.domain.value.BinaryValue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TheBrotliModuleFromTheSourceTest {

    private static final String REFERENCE = "/brotli/module/";

    @TempDir
    Path directory;

    private final StringBuilder printed = new StringBuilder();

    private Interpreter makeInterpreter() {
        Bounds bounds = Bounds.standard().withoutTimeLimit();
        for (HostService service : HostService.values()) {
            if (service != HostService.ENVIRONMENT) {
                bounds = bounds.grant(service);
            }
        }
        return Interpreter.createOn(
                AnInterpreter.standardPorts().withFiles(FileSystemPort.rootFileSystem(directory)),
                printed::append,
                bounds);
    }

    private static String readReference(String name) {
        try (InputStream in = TheBrotliModuleFromTheSourceTest.class.getResourceAsStream(REFERENCE + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }

    private List<String> runReference(String script) throws IOException {
        Files.writeString(directory.resolve(script), readReference(script));
        Interpreter interpreter = makeInterpreter();
        String source = "system/options/quiet: true system/options/args: [] do %" + script;
        interpreter.defineNewWordsIn(source);
        interpreter.run(source);
        return printed.toString().replace("\r\n", "\n").lines().toList();
    }

    private String answerTo(String source) {
        Interpreter interpreter = makeInterpreter();
        String whole = "b: import 'brotli\n" + source;
        interpreter.defineNewWordsIn(whole);
        return interpreter.display(interpreter.run(whole));
    }

    @Test
    @DisplayName("the module and its one-shot commands answer as module-answers.txt records the C answering")
    void answersAsTheC() throws IOException {
        List<String> expected = readReference("module-answers.txt").replace("\r\n", "\n").lines().toList();

        assertThat(runReference("module-answers.r3")).containsExactlyElementsOf(expected);
    }

    private List<String> runScenario(int index) throws IOException {
        Files.writeString(directory.resolve("module-streams.r3"), readReference("module-streams.r3"));
        printed.setLength(0);
        Interpreter interpreter = makeInterpreter();
        String source = "system/options/quiet: true system/options/args: [" + index + "] do %module-streams.r3";
        interpreter.defineNewWordsIn(source);
        interpreter.run(source);
        return printed.toString().replace("\r\n", "\n").lines().toList();
    }

    @Test
    @DisplayName("every streaming scenario answers as module-streams.txt records the C answering")
    void streamsAsTheC() throws IOException {
        List<String> expected = readReference("module-streams.txt").replace("\r\n", "\n").lines().toList();
        List<String> answered = new ArrayList<>();
        int scenarios = Integer.parseInt(runScenario(0).getFirst().trim());
        for (int index = 1; index <= scenarios; index++) {
            answered.addAll(runScenario(index));
        }

        assertThat(answered).containsExactlyElementsOf(expected);
    }

    private static final String CRASHED = "crashed";

    private List<String> runEveryStreamCase() throws IOException {
        String script = readReference("module-stream-case.r3");
        String everyCase = script.substring(0, script.indexOf("if index = 0")) + """
                repeat index length? cases [
                    code: pick cases index
                    prin [mold/flat code "=> "]
                    set/any 'r try code
                    print answer-of :r
                ]
                """;
        Files.writeString(directory.resolve("every-case.r3"), everyCase);
        printed.setLength(0);
        Interpreter interpreter = makeInterpreter();
        String source = "system/options/quiet: true system/options/args: [0] do %every-case.r3";
        interpreter.defineNewWordsIn(source);
        interpreter.run(source);
        return printed.toString().replace("\r\n", "\n").lines().toList();
    }

    @Test
    @DisplayName("every stream case answers as module-stream-case.txt records, and where the C crashed, finishing with none is finishing with #{}")
    void streamCasesAsTheC() throws IOException {
        List<String> expected = readReference("module-stream-case.txt").replace("\r\n", "\n").lines().toList();
        List<String> answered = runEveryStreamCase();

        assertThat(answered).hasSameSizeAs(expected);
        for (int at = 0; at < expected.size(); at++) {
            String line = expected.get(at);
            if (!line.endsWith(CRASHED)) {
                assertThat(answered.get(at)).isEqualTo(line);
            } else if (line.startsWith("[in-chunks")) {
                String sibling = expected.get(at - 1);
                assertThat(answered.get(at)).isEqualTo(sibling.replace("#{}] =>", "none] =>"));
            } else {
                assertThat(answered.get(at)).startsWith(line.replace(CRASHED, "#{"));
            }
        }
    }

    @Nested
    @DisplayName("the module")
    class TheModule {

        @Test
        @DisplayName("waits in system/modules until import, and import makes it")
        void waitsForImport() {
            assertThat(answerTo("reduce [module? b same? b system/modules/brotli]")).isEqualTo("[#(true) #(true)]");
        }

        @Test
        @DisplayName("carries the header its builder writes, named brotli")
        void carriesItsHeader() {
            assertThat(answerTo("""
                    s: spec-of b
                    reduce [s/title s/name s/version s/needs]""")).isEqualTo("""
                    ["Rebol Brotli Extension" brotli 0.1.0 3.20.5]""");
        }

        @Test
        @DisplayName("hides init-words and holds the one-shot and the streaming commands")
        void holdsTheCommands() {
            assertThat(answerTo("reduce [none? find words-of b 'init-words copy skip words-of b 4]"))
                    .isEqualTo("[#(true) [version compress decompress make-encoder make-decoder write read]]");
        }

        @Test
        @DisplayName("answers Brotli's version as a tuple")
        void answersTheVersion() {
            assertThat(answerTo("reduce [b/version tuple? b/version]")).isEqualTo("[1.2.0 #(true)]");
        }

        @Test
        @DisplayName("appends a second br to Accept-Encoding, as its import check misses the one already there")
        void appendsASecondBr() {
            Interpreter interpreter = makeInterpreter();
            String source = """
                    before: copy system/schemes/http/headers/Accept-Encoding
                    import 'brotli
                    import 'brotli
                    reduce [before system/schemes/http/headers/Accept-Encoding]""";
            interpreter.defineNewWordsIn(source);

            assertThat(interpreter.display(interpreter.run(source))).isEqualTo("""
                    ["gzip,deflate,br" "gzip,deflate,br,br"]""");
        }
    }

    @Nested
    @DisplayName("compress")
    class Compress {

        @ParameterizedTest(name = "/level {0} writes what level {1} writes")
        @CsvSource({"-1, 6", "-2, 11", "12, 11", "99, 11", "4294967295, 6", "4294967294, 11"})
        @DisplayName("passes its level through an unsigned 32 bits, so -1 is no level, 6, and any other negative 11")
        void clampsTheLevel(long asked, int meant) {
            assertThat(answerTo("equal? b/compress/level {hello hello} " + asked + " b/compress/level {hello hello} "
                    + meant)).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("without /level writes level 6")
        void defaultsToSix() {
            assertThat(answerTo("equal? b/compress {hello hello} b/compress/level {hello hello} 6"))
                    .isEqualTo("#(true)");
        }

        @Test
        @DisplayName("writes the core's bytes, so compress 'br is unchanged by importing")
        void writesTheCoresBytes() {
            Interpreter interpreter = makeInterpreter();
            String source = """
                    t: copy #{} repeat i 30000 [append t remainder i * 31 + (i // 7) 256]
                    before: collect [foreach level [0 1 5 9 11] [keep compress/level t 'br level]]
                    b: import 'brotli
                    after: collect [foreach level [0 1 5 9 11] [keep compress/level t 'br level]]
                    own: collect [foreach level [0 1 5 9 11] [keep b/compress/level t level]]
                    reduce [equal? before after equal? before own]""";
            interpreter.defineNewWordsIn(source);

            assertThat(interpreter.display(interpreter.run(source))).isEqualTo("[#(true) #(true)]");
        }

        @Test
        @DisplayName("reads a string as the UTF-8 R3 stores, from its index, and /part counts within it")
        void readsStrings() {
            assertThat(answerTo("""
                    reduce [
                        b/compress {ěšč}
                        b/compress next {Xhello}
                        b/compress/part {abcdefghij} 5
                        b/compress/part {abcdefghij} -3
                    ]""")).isEqualTo("[#{8B0280C49BC5A1C48D03} #{0B028068656C6C6F03} #{0B0280616263646503} #{3B}]");
        }
    }

    @Nested
    @DisplayName("decompress")
    class Decompress {

        @Test
        @DisplayName("answers #{} without decoding for empty input, /size 0, /part 0 and a negative /part")
        void shortCuts() {
            assertThat(answerTo("""
                    c: b/compress {hello hello}
                    reduce [b/decompress #{} b/decompress/size c 0 b/decompress/part c 0 b/decompress/part c -1]"""))
                    .isEqualTo("[#{} #{} #{} #{}]");
        }

        @Test
        @DisplayName("cuts the output at /size exactly, and a negative /size is no limit")
        void stopsAtTheLimit() {
            assertThat(answerTo("""
                    c: b/compress {hello hello hello hello}
                    reduce [to string! b/decompress/size c 5 length? b/decompress/size c -1 length? b/decompress/size c 1000]"""))
                    .isEqualTo("""
                            ["hello" 23 23]""");
        }

        @Test
        @DisplayName("decompress 'br after import refuses with bad-press and the C's error number")
        void refusesWithTheCsNumber() {
            assertThat(answerTo("""
                    c: b/compress {hello hello hello hello}
                    collect [foreach bad reduce [#{FFFFFFFF} #{00} #{11} #{040000} #{1C} copy/part c (length? c) - 1] [
                        e: try [decompress bad 'br]
                        keep e/id keep e/arg1
                    ]]""")).isEqualTo("[bad-press -15 bad-press 2 bad-press -13 bad-press -1 bad-press -2 bad-press 2]");
        }

        @Test
        @DisplayName("ignores bytes after the end of a stream")
        void ignoresTrailingBytes() {
            assertThat(answerTo("{hello} = to string! b/decompress join b/compress {hello} #{0102}"))
                    .isEqualTo("#(true)");
        }

        @Test
        @DisplayName("fails with the C's message for a stream cut short, damaged or not a stream at all")
        void failsAsTheCSays() {
            assertThat(answerTo("""
                    c: b/compress {hello hello hello hello}
                    unique collect [foreach code [
                        [b/decompress #{00}]
                        [b/decompress copy/part c (length? c) - 1]
                        [b/decompress #{FFFFFFFF}]
                        [b/decompress/part c 5]
                    ] [e: try code keep/only reduce [e/id e/arg1]]]""")).isEqualTo("""
                    [[command-fail "Failed to decompress using the Brotli decoder!"]]""");
        }
    }

    @Nested
    @DisplayName("an encoder")
    class AnEncoder {

        @Test
        @DisplayName("molds as a brotli-encoder handle with an address, and answers its type")
        void moldsAsAHandle() {
            assertThat(answerTo("""
                    e: b/make-encoder
                    hex: charset {0123456789abcdef}
                    reduce [e/type parse mold e [{#(handle! brotli-encoder 0#} some hex {)}]]"""))
                    .isEqualTo("[brotli-encoder #(true)]");
        }

        @Test
        @DisplayName("write hands back the handle and keeps what it encoded until a read, a flush or a finish")
        void writeKeepsUntilAsked() {
            assertThat(answerTo("""
                    e: b/make-encoder
                    t: {hello hello hello hello}
                    reduce [
                        same? e b/write e t
                        c: b/read e
                        b/read e
                        equal? t to string! b/decompress append copy c b/write e none
                    ]""")).isEqualTo("[#(true) #{0B0B000024406A1065EAF09C3E03} #{} #(true)]");
        }

        @Test
        @DisplayName("write/flush and write/finish give out what is ready, and the pieces join into one stream")
        void flushAndFinishJoin() {
            assertThat(answerTo("""
                    e: b/make-encoder/level 9
                    t: {the quick brown fox jumps over the lazy dog}
                    out: copy #{}
                    foreach piece [{the quick } {brown fox } {jumps over the lazy dog}] [append out b/write/flush e piece]
                    append out b/write/finish e #{}
                    reduce [e/finished equal? t to string! b/decompress out]""")).isEqualTo("[#(true) #(true)]");
        }

        @Test
        @DisplayName("refuses every write and read once finished, with the C's message")
        void refusesOnceFinished() {
            assertThat(answerTo("""
                    e: b/make-encoder
                    b/write/finish e {hello}
                    unique collect [foreach code [[b/write e {more}] [b/write e none] [b/write/flush e #{}] [b/read e]] [
                        r: try code keep/only reduce [r/id r/arg1]
                    ]]""")).isEqualTo("""
                    [[command-fail "Brotli encoder is finished!"]]""");
        }

        @Test
        @DisplayName("answers none to a read or a write of none before anything was written, and stays unfinished")
        void answersNoneWhenUntouched() {
            assertThat(answerTo("""
                    e: b/make-encoder
                    reduce [b/read e b/write e none b/write/flush e none b/write/finish e none e/finished]"""))
                    .isEqualTo("[_ _ _ _ #(false)]");
        }

        @Test
        @DisplayName("clamps /level to 0 through 11, so -1 is level 0 and 12 is level 11")
        void clampsTheLevel() {
            assertThat(answerTo("""
                    t: {hello hello hello hello}
                    finish-at: func [level] [b/write/finish b/make-encoder/level level t]
                    reduce [equal? finish-at -1 finish-at 0 equal? finish-at 12 finish-at 11 equal? finish-at 0 finish-at 11]"""))
                    .isEqualTo("[#(true) #(true) #(false)]");
        }

        @Test
        @DisplayName("at levels 0 and 1 finishing with none finishes as finishing with #{}, where the C crashed")
        void finishesFastLevelsWithNone() {
            assertThat(answerTo("""
                    t: {hello hello hello hello}
                    collect [foreach level [0 1] [
                        e: b/make-encoder/level level  b/write e t  with-none: b/write e none
                        e: b/make-encoder/level level  b/write e t  with-empty: b/write/finish e #{}
                        keep equal? with-none with-empty
                        keep equal? t to string! b/decompress with-none
                    ]]""")).isEqualTo("[#(true) #(true) #(true) #(true)]");
        }

        @Test
        @DisplayName("reads a string from its index as the UTF-8 R3 stores")
        void readsStrings() {
            assertThat(answerTo("""
                    reduce [b/write/finish b/make-encoder {ěšč} b/write/finish b/make-encoder next {Xhello}]"""))
                    .isEqualTo("[#{8B0280C49BC5A1C48D03} #{0B028068656C6C6F03}]");
        }
    }

    @Nested
    @DisplayName("an encoder's fields")
    class AnEncodersFields {

        @Test
        @DisplayName("finished reads, and mode and size-hint are written but never read")
        void readsAndWrites() {
            assertThat(answerTo("""
                    e: b/make-encoder
                    collect [foreach code [[e/finished] [e/mode] [e/size-hint] [e/mode: 1] [e/size-hint: 23]
                            [e/mode: {text}] [e/finished: true] [e/nonsense: 1] [e/nonsense]] [
                        r: try code keep/only either error? r [reduce [r/id r/arg1]] [r]
                    ]]""")).isEqualTo("""
                    [#(false) [invalid-path e/mode] [invalid-path e/size-hint] 1 23 [bad-field-set mode] [bad-path-set e/finished:] [bad-path-set e/nonsense:] [invalid-path e/nonsense]]""");
        }

        @Test
        @DisplayName("mode 2 is font mode, which changes level 5's bytes and not level 11's")
        void fontMode() {
            assertThat(answerTo("""
                    t: copy {} loop 400 [append t {the quick brown fox jumps over the lazy dog }]
                    finish-in: func [level mode /local e] [e: b/make-encoder/level level e/mode: mode b/write/finish e t]
                    reduce [
                        equal? finish-in 5 0 finish-in 5 2
                        equal? finish-in 11 0 finish-in 11 2
                        equal? t to string! b/decompress finish-in 5 2
                    ]""")).isEqualTo("[#(false) #(true) #(true)]");
        }

        @Test
        @DisplayName("a size hint set after the first write is taken and changes nothing")
        void lateSizeHint() {
            assertThat(answerTo("""
                    t: copy {} loop 400 [append t {the quick brown fox jumps over the lazy dog }]
                    e: b/make-encoder/level 5  b/write e copy/part t 100
                    late: e/size-hint: 20000
                    with-late: b/write/finish e skip t 100
                    e: b/make-encoder/level 5  b/write e copy/part t 100
                    without: b/write/finish e skip t 100
                    reduce [late equal? with-late without]""")).isEqualTo("[20000 #(true)]");
        }

        @Test
        @DisplayName("a size hint of -1 is read as 2^32 - 1, which the encoder takes as a large input")
        void sizeHintIsUnsigned() {
            assertThat(answerTo("""
                    t: copy {} loop 500 [append t {the quick brown fox jumps over the lazy dog }]
                    finish-with: func [hint /local e] [e: b/make-encoder/level 5 e/size-hint: hint b/write/finish e t]
                    reduce [equal? finish-with -1 finish-with 4294967295 equal? finish-with -1 finish-with 0]"""))
                    .isEqualTo("[#(true) #(false)]");
        }
    }

    @Nested
    @DisplayName("a decoder")
    class ADecoder {

        @Test
        @DisplayName("decodes a stream given in pieces, keeping what it decoded until a read, a flush or a finish")
        void decodesInPieces() {
            assertThat(answerTo("""
                    t: copy {} loop 2000 [append t {the quick brown fox jumps over the lazy dog }]
                    c: b/compress t
                    d: b/make-decoder
                    out: copy #{}
                    at: c
                    while [not tail? at] [b/write d copy/part at 777  append out b/read d  at: skip at 777]
                    reduce [d/finished equal? t to string! out]""")).isEqualTo("[#(true) #(true)]");
        }

        @Test
        @DisplayName("gives #{} for what is not a stream, with no error, and stays unfinished")
        void ignoresDamage() {
            assertThat(answerTo("""
                    d: b/make-decoder
                    reduce [b/write/finish d #{FFFFFFFF} d/finished b/write/finish d b/compress {hello} b/read d]"""))
                    .isEqualTo("[#{} #(false) #{} #{}]");
        }

        @Test
        @DisplayName("takes more input after finishing and gives nothing for it")
        void ignoresInputAfterFinishing() {
            assertThat(answerTo("""
                    d: b/make-decoder
                    b/write/finish d b/compress {hello}
                    reduce [same? d b/write d #{00} b/read d b/write d none d/finished]"""))
                    .isEqualTo("[#(true) #{} #{} #(true)]");
        }

        @Test
        @DisplayName("takes mode and size-hint as integers and changes nothing, refuses finished, and reads only finished")
        void fields() {
            assertThat(answerTo("""
                    d: b/make-decoder
                    collect [foreach code [[d/finished] [d/mode] [d/mode: 1] [d/size-hint: 9] [d/mode: {x}] [d/finished: true]] [
                        r: try code keep/only either error? r [reduce [r/id r/arg1]] [r]
                    ]]""")).isEqualTo("""
                    [#(false) [invalid-path d/mode] 1 9 [bad-field-set mode] [bad-path-set d/finished:]]""");
        }
    }

    @Nested
    @DisplayName("CVE-2026-111497")
    class BlockSwitchWithOneBlockType {

        private String answerWithStream(byte[] stream, String source) {
            Interpreter interpreter = makeInterpreter();
            interpreter.define("stream", BinaryValue.ofBytes(stream));
            interpreter.defineNewWordsIn(source);
            return interpreter.display(interpreter.run(source));
        }

        @ParameterizedTest(name = "command block types {0}")
        @ValueSource(ints = {1, 2})
        @DisplayName("a block switch where there is one block type is refused by decompress 'br, by the module, and by its decoder")
        void refusedEveryWay(int commandTypes) {
            byte[] stream = BrotliBlockSwitchStreams.buildEmptyWordsThenWord(
                    BrotliBlockSwitchStreams.BLOCK_SIZE_CAP, commandTypes);

            assertThat(answerWithStream(stream, """
                    before: error? try [decompress stream 'br]
                    b: import 'brotli
                    d: b/make-decoder
                    r: try [b/decompress stream]
                    after: try [decompress stream 'br]
                    reduce [before after/id after/arg1 r/arg1 b/write/finish d stream d/finished]"""))
                    .isEqualTo("""
                            [#(true) bad-press -17 "Failed to decompress using the Brotli decoder!" #{} #(false)]""");
        }

        @Test
        @DisplayName("2^24 commands of one block type are not refused by decompress 'br")
        void capItselfDecodes() {
            byte[] stream = BrotliBlockSwitchStreams.buildEmptyWordsThenWord(
                    BrotliBlockSwitchStreams.BLOCK_SIZE_CAP - 1, 1);

            assertThat(answerWithStream(stream, "length? decompress stream 'br")).isEqualTo("4");
        }
    }

    @Nested
    @DisplayName("write and read")
    class WriteAndRead {

        @Test
        @DisplayName("refuse a handle that is neither an encoder nor a decoder, with the C's message")
        void refuseOtherHandles() {
            assertThat(answerTo("""
                    k: rc4/key #{0102}
                    unique collect [foreach code [[b/write k {hello}] [b/read k]] [r: try code keep/only reduce [r/id r/arg1]]]"""))
                    .isEqualTo("""
                    [[command-fail "Invalid Brotli encoder or decoder handle!"]]""");
        }

        @Test
        @DisplayName("write refuses a handle as its data")
        void refusesHandleAsData() {
            assertThat(answerTo("r: try [b/write b/make-decoder b/make-encoder] reduce [r/id r/arg1]"))
                    .isEqualTo("[expect-arg write]");
        }
    }
}
