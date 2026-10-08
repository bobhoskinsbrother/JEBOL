package org.jebol.domain.eval.natives;

import org.jebol.adapter.host.JavaProcesses;
import org.jebol.application.Bounds;
import org.jebol.application.FileSystemPort;
import org.jebol.application.Interpreter;
import org.jebol.domain.eval.EnvironmentPort;
import org.jebol.domain.eval.GrantedServices;
import org.jebol.domain.eval.LocalFileSeparator;
import org.jebol.domain.eval.Ports;
import org.jebol.domain.eval.actions.*;
import org.jebol.domain.host.HostService;
import org.jebol.domain.value.DefaultNative;
import org.jebol.domain.value.Molder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WhatEachPortNativeDeclaresTest {

    private final class AnEnvironmentOfItsOwn implements EnvironmentPort {

        private final Map<String, String> held = new LinkedHashMap<>();

        @Override
        public String valueOf(String variable) {
            return held.get(variable);
        }

        @Override
        public Map<String, String> all() {
            return Map.copyOf(held);
        }

        @Override
        public void nameHolds(String variable, String value) {
            if (value == null) {
                held.remove(variable);
            } else {
                held.put(variable, value);
            }
        }
    }

    private String answerIn(Path directory, String source) {
        Interpreter interpreter = Interpreter.withBounds(Bounds.standard()
                .granting(HostService.FILES)
                .granting(HostService.WORKING_DIRECTORY)
                .granting(HostService.ENVIRONMENT)
                .granting(HostService.PROCESSES)
                .granting(HostService.CLOCK));
        interpreter.useFileSystem(FileSystemPort.rootedAt(directory));
        interpreter.useEnvironment(new AnEnvironmentOfItsOwn());
        interpreter.useProcesses(new JavaProcesses());
        interpreter.defineFreshWordsIn(source);
        return Molder.moldFlat(interpreter.run(source).value());
    }

    private String answerBesideAFileAndADirectory(Path directory, String source) throws IOException {
        Files.writeString(directory.resolve("a.txt"), "abc");
        Files.createDirectory(directory.resolve("d"));
        return answerIn(directory, source);
    }

    Stream<Arguments> eachNameAndItsRefinements() {
        GrantedServices granted = new GrantedServices();
        Ports ports = new Ports(granted);
        return Stream.of(
                Arguments.of(new ReadAction(granted, ports), "read",
                        Set.of("part", "seek", "string", "binary", "lines", "all")),
                Arguments.of(new WriteAction(granted, ports), "write",
                        Set.of("part", "seek", "append", "allow", "lines", "binary", "all")),
                Arguments.of(new ToLocalFileNative(granted, new LocalFileSeparator()), "to-local-file", Set.of("full")),
                Arguments.of(new ToRebolFileNative(), "to-rebol-file", Set.of()),
                Arguments.of(new CallNative(granted), "call",
                        Set.of("wait", "console", "shell", "info", "input", "output", "error")),
                Arguments.of(new GetEnvNative(granted), "get-env", Set.of()),
                Arguments.of(new ListEnvNative(granted), "list-env", Set.of()),
                Arguments.of(new SetEnvNative(granted), "set-env", Set.of()),
                Arguments.of(new WhatDirNative(granted), "what-dir", Set.of()),
                Arguments.of(new ChangeDirNative(granted), "change-dir", Set.of()),
                Arguments.of(new CreateAction(granted, ports), "create", Set.of()),
                Arguments.of(new DeleteAction(granted, ports), "delete", Set.of()),
                Arguments.of(new RenameAction(granted, ports), "rename", Set.of()),
                Arguments.of(new IsDirectoryNative(granted), "dir?", Set.of("check")),
                Arguments.of(new SetSchemeNative(), "set-scheme", Set.of()),
                Arguments.of(new OpenAction(granted, ports), "open", Set.of("new", "read", "write", "seek", "allow")),
                Arguments.of(new UpdateAction(granted, ports), "update", Set.of()),
                Arguments.of(new FlushAction(), "flush", Set.of()),
                Arguments.of(new IsOpenAction(granted, ports), "open?", Set.of()),
                Arguments.of(new CloseAction(granted, ports), "close", Set.of()),
                Arguments.of(new ModifyAction(granted, ports), "modify", Set.of()),
                Arguments.of(new BrowseNative(granted), "browse", Set.of()),
                Arguments.of(new RequestFileNative(granted), "request-file",
                        Set.of("save", "multi", "file", "title", "filter")),
                Arguments.of(new RequestDirNative(granted), "request-dir", Set.of("title", "dir", "keep")),
                Arguments.of(new RequestColorNative(granted), "request-color", Set.of("default")),
                Arguments.of(new RequestPasswordNative(granted), "request-password", Set.of()),
                Arguments.of(new QueryAction(granted, ports), "query", Set.of("mode")));
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("eachNameAndItsRefinements")
    @DisplayName("each answers to its Rebol name with the refinements Rebol declares")
    void declaresItsNameAndRefinements(DefaultNative definition, String name, Set<String> refinements) {
        assertThat(definition.nativeName()).isEqualTo(name);
        assertThat(definition.refinementsDeclaredApart()).isEqualTo(refinements);
    }

    @ParameterizedTest(name = "{0} is refused with {1}, naming {2} and {3}")
    @DisplayName("each refuses as Rebol does, beside a.txt holding abc and an empty d/")
    @CsvSource(delimiter = '|', quoteCharacter = '`', textBlock = """
            read/part %a.txt -1                                 | out-of-range   | -1                          | _
            read/seek %a.txt -1                                 | out-of-range   | -1                          | _
            write/seek %g.txt {x} -1                            | out-of-range   | -1                          | _
            write/part %g.txt {x} -1                            | out-of-range   | -1                          | _
            create ftp://a                                      | no-scheme      | ftp                         | _
            create %a.txt/                                      | no-create      | %a.txt/                     | _
            delete http://a                                     | no-port-action | delete:                     | _
            rename http://a %x                                  | no-port-action | rename:                     | _
            rename %a.txt http://a                              | no-rename      | %a.txt                      | _
            rename %missing.txt %other.txt                      | no-rename      | %missing.txt                | _
            query %a.txt 'nope                                  | invalid-arg    | nope                        | _
            query %a.txt [size 1]                               | invalid-arg    | 1                           | _
            query 1-Jan-2020 [year 1]                           | invalid-arg    | 1                           | _
            query #(i32! [1 2 3]) 'words                        | cannot-use     | words                       | #(vector!)
            query #(i32! [1 2 3]) [type nope]                   | invalid-arg    | nope                        | _
            h: rc4/key #{0102} query h 'nope                    | cannot-use     | nope                        | #(handle!)
            h: rc4/key #{0102} query h [type nope]              | invalid-arg    | nope                        | _
            query system/ports/output 'words                    | invalid-arg    | words                       | _
            query system/ports/output [window-cols nope]        | invalid-arg    | nope                        | _
            query 'a 'size                                      | no-scheme      | a                           | _
            query 'a none                                       | no-scheme      | a                           | _
            query [scheme: 'checksum] 'size                     | no-port-action | query:                      | _
            modify %a.txt 'bogus true                           | invalid-arg    | bogus                       | _
            modify system/ports/output 'owner-read true         | bad-file-mode  | owner-read                  | _
            p: open %a.txt update p                             | no-port-action | update:                     | _
            open %nope/                                         | cannot-open    | %nope/                      | 3
            call/wait []                                        | too-short      | _                           | _
            call/wait {surely-no-such-program-here}             | call-fail      | "No such file or directory" | _
            """)
    void refusesAsRebolDoes(String source, String id, String firstArgument, String secondArgument,
            @TempDir Path directory) throws IOException {
        assertThat(answerBesideAFileAndADirectory(directory, """
                e: try [%s] reduce [e/id e/arg1 e/arg2]""".formatted(source)))
                .isEqualTo("[" + id + " " + firstArgument + " " + secondArgument + "]");
    }

    @ParameterizedTest(name = "change-dir {0} is cannot-open with {1}")
    @DisplayName("a directory that cannot be entered carries the negated errno, as the C does")
    @CsvSource(delimiter = '|', quoteCharacter = '`', textBlock = """
            %nope/  | -2
            %a.txt  | -20
            """)
    void aDirectoryThatCannotBeEnteredCarriesTheErrno(String target, String negatedErrno,
            @TempDir Path directory) throws IOException {
        assertThat(answerBesideAFileAndADirectory(directory, """
                e: try [change-dir %s] reduce [e/id e/arg2]""".formatted(target)))
                .isEqualTo("[cannot-open " + negatedErrno + "]");
    }

    @Nested
    @DisplayName("the file verbs make a port first and answer with it")
    class TheFileVerbs {

        @ParameterizedTest(name = "{0}")
        @CsvSource(delimiter = '|', quoteCharacter = '`', textBlock = """
                port? create %g.txt                                                      | #(true)
                p: create %g.txt p/spec/ref                                              | %g.txt
                r: rename %a.txt %b.txt reduce [r/spec/ref exists? %a.txt exists? %b.txt] | [%a.txt _ file]
                d: delete %a.txt reduce [port? d d/spec/ref exists? %a.txt]              | [#(true) %a.txt _]
                delete %missing.txt                                                      | #(false)
                modify %a.txt 'owner-read true                                           | _
                p: open %a.txt modify p 'world-write false                               | #(true)
                p: open %a.txt reduce [open? p open? close p]                            | [#(true) #(false)]
                p: open %a.txt read/part p 2                                             | #{6162}
                p: open %a.txt read/seek p 1                                             | #{6263}
                read %d/                                                                 | []
                sort read %./                                                            | [%a.txt %d/]
                write %w.txt {hi} read/string %w.txt                                     | "hi"
                port? flush system/ports/output                                          | #(true)
                dir? %d                                                                  | #(false)
                dir?/check %d                                                            | #(true)
                dir? %d/                                                                 | #(true)
                dir? %""                                                                 | #(false)
                dir? none                                                                | #(false)
                dir? http://a/b/                                                         | #(true)
                """)
        void answersWhatRebolAnswers(String source, String wanted, @TempDir Path directory) throws IOException {
            assertThat(answerBesideAFileAndADirectory(directory, source)).isEqualTo(wanted);
        }
    }

    @Nested
    @DisplayName("query answers each kind of target by its own fields")
    class Query {

        @ParameterizedTest(name = "{0}")
        @CsvSource(delimiter = '|', quoteCharacter = '`', textBlock = """
                query %a.txt 'size                                 | 3
                query %a.txt [type :size]                          | [type: file 3]
                query %d/ 'type                                    | dir
                query %d 'type                                     | dir
                query %"" 'size                                    | _
                query %missing.txt 'size                           | _
                none? select query %a.txt object! 'date            | #(true)
                m: query %a.txt 'modified m/zone = now/zone        | #(true)
                query system/ports/output 'window-cols             | 80
                query system/ports/output object!                  | make object! [buffer-cols: 0 buffer-rows: 0 window-cols: 0 window-rows: 0 length: 0]
                query system/ports/output none                     | [buffer-cols buffer-rows window-cols window-rows length]
                query system/ports/output [window-cols :length]    | [window-cols: 80 0]
                unset? query 1-Jan-2020 'nope                      | #(true)
                query 1-Jan-2020 [year nope]                       | [year: 2020 nope: _]
                query 1-Jan-2020 [:month]                          | [1]
                h: rc4/key #{0102} query h 'words                  | [type]
                h: rc4/key #{0102} query h object!                 | make object! [type: 'rc4]
                query #(i32! [1 2 3]) [size :type]                 | [size: 32 integer!]
                """)
        void answersWhatRebolAnswers(String source, String wanted, @TempDir Path directory) throws IOException {
            assertThat(answerBesideAFileAndADirectory(directory, source)).isEqualTo(wanted);
        }
    }

    @Nested
    @DisplayName("set-scheme reads the name and the actor by where system/standard/scheme keeps them")
    class SetScheme {

        @ParameterizedTest(name = "{0}")
        @CsvSource(delimiter = '|', quoteCharacter = '`', textBlock = """
                set-scheme make system/standard/scheme [name: 'file]            | #(true)
                set-scheme make system/standard/scheme [name: 'udp]             | #(true)
                set-scheme make system/standard/scheme [name: 'serial]          | _
                set-scheme make system/standard/scheme [name: {file}]           | _
                set-scheme make object! [name: 'file b: 2 c: 3 d: 4 e: none]    | #(true)
                set-scheme make object! [name: 'file actor: none]               | _
                set-scheme make object! [a: 1 b: 2 c: 3 d: 4 actor: none name: 'file] | _
                s: make system/standard/scheme [name: 'file] set-scheme s type? :s/actor        | #(native!)
                s: make system/standard/scheme [name: 'file] set-scheme s mold :s/actor        | "make native! [[_ internal]]"
                s: make system/standard/scheme [name: 'file] set-scheme s spec-of :s/actor     | [_ internal]
                s: make system/standard/scheme [name: 'file] set-scheme s words-of :s/actor    | [internal]
                s: make system/standard/scheme [name: 'file] set-scheme s types-of :s/actor    | [make typeset! [end!]]
                s: make system/standard/scheme [name: 'file actor: 5] set-scheme s type? :s/actor | #(native!)
                s: make system/standard/scheme [name: 'file] protect in s 'actor set-scheme s  | #(true)
                s: make system/standard/scheme [name: 'file] set-scheme s e: try [s/actor] reduce [e/id e/arg1 e/arg2] | [no-arg actor internal]
                s: make system/standard/scheme [name: 'file] set-scheme s e: try [s/actor 'nonsense] reduce [e/id e/arg2 e/arg3] | [expect-arg internal #(word!)]
                type? get in system/schemes/file 'actor                                        | #(native!)
                (get in system/schemes/file 'actor) = get in system/schemes/dir 'actor         | #(false)
                p: make port! %a.txt same? :p/actor get in system/schemes/file 'actor          | #(true)
                p: make port! [scheme: 'console] type? :p/actor                                | #(native!)
                type? get in system/schemes/http 'actor                                        | #(object!)
                """)
        void answersWhatRebolAnswers(String source, String wanted, @TempDir Path directory) {
            assertThat(answerIn(directory, source)).isEqualTo(wanted);
        }
    }

    @Nested
    @DisplayName("a port's actor decides what its verbs do, as Do_Port_Action does")
    class ThePortsActor {

        @ParameterizedTest(name = "{0}")
        @CsvSource(delimiter = '|', quoteCharacter = '`', textBlock = """
                p: make port! %a.txt query p 'size                                     | 3
                p: make port! %a.txt p/actor: none query p 'size                       | _
                p: make port! %a.txt p/actor: none read p                              | _
                p: make port! %a.txt p/actor: none open p                              | _
                p: make port! %a.txt p/actor: 'file e: try [query p 'size] e/id        | invalid-actor
                p: make port! %a.txt p/actor: 1 e: try [query p 'size] e/id            | invalid-actor
                """)
        void answersWhatRebolAnswers(String source, String wanted, @TempDir Path directory) throws IOException {
            assertThat(answerBesideAFileAndADirectory(directory, source)).isEqualTo(wanted);
        }
    }

    @Nested
    @DisplayName("a native molds as the spec Rebol declares for it")
    class NativesMoldTheirSpecs {

        @ParameterizedTest(name = "{0}")
        @CsvSource(delimiter = '|', quoteCharacter = '`', textBlock = """
                mold/flat :if        | {make native! [[{If TRUE condition, return arg; evaluate blocks by default.} condition [any-type!] true-branch /only "Return block arg instead of evaluating it."]]}
                mold/flat/all :if    | {#(native! [[{If TRUE condition, return arg; evaluate blocks by default.} condition [any-type!] true-branch /only "Return block arg instead of evaluating it."]])}
                mold/flat :tail?     | {make action! [[{Returns TRUE if series is at or past its end; or empty for other types.} series [series! gob! port! bitset! typeset! map!]]]}
                mold/flat :+         | {make op! [["Returns the addition of two values." value1 [scalar! date! vector!] value2 [scalar! date! vector!]]]}
                mold/flat {a^/b}     | {"a^^/b"}
                length? mold :tail?  | 150
                none? find mold/flat mold :tail? newline      | #(false)
                none? find mold/flat {a[ b ]c} {a[ b ]c}      | #(false)
                mold/flat make vector! [integer! 32 12]       | "#(int32! [0 0 0 0 0 0 0 0 0 0 0 0])"
                """)
        void answersWhatRebolAnswers(String source, String wanted, @TempDir Path directory) {
            assertThat(answerIn(directory, source)).isEqualTo(wanted);
        }
    }

    @Nested
    @DisplayName("paths and the environment")
    class PathsAndTheEnvironment {

        @ParameterizedTest(name = "{0}")
        @CsvSource(delimiter = '|', quoteCharacter = '`', textBlock = """
                to-rebol-file {a\\b//c}                                        | %a/b/c
                to-rebol-file {\\\\a}                                          | %/a
                to-local-file %/a//b                                           | "/a/b"
                to-local-file/full %/a/b/../c                                  | "/a/c"
                to-local-file/full %/a/./b/.                                   | "/a/b/"
                get-env {JEBOL_SURELY_NOT_SET}                                 | _
                set-env {JEBOL_SURELY_SET} {1} get-env {JEBOL_SURELY_SET}      | "1"
                set-env 'JEBOL_SURELY_SET none get-env 'JEBOL_SURELY_SET       | _
                """)
        void answersWhatRebolAnswers(String source, String wanted, @TempDir Path directory) {
            assertThat(answerIn(directory, source)).isEqualTo(wanted);
        }
    }

    @Nested
    @DisplayName("a type error names the call as it was written")
    class TypeErrorsNameTheCall {

        @ParameterizedTest(name = "{0}")
        @CsvSource(delimiter = '|', quoteCharacter = '`', textBlock = """
                e: try [exists? create %g.txt] e/arg1                           | exists?
                e: try [reduce [exists? 1]] e/arg1                              | exists?
                f: func [a [file!]] [a] e: try [f 1] e/arg1                     | f
                o: object [f: func [a [file!]] [a]] e: try [o/f 1] e/arg1       | f
                f: func [a [file!] /r b [file!]] [a] e: try [f/r %a 1] reduce [e/arg1 e/arg2] | [f b]
                g: :exists? e: try [g 1] e/arg1                                 | g
                e: try [do reduce [func [a [file!]] [a] 1]] e/arg1              | -unnamed-
                e: try [apply func [a [file!]] [a] [1]] reduce [e/id e/arg1]    | [expect-arg -apply-]
                e: try [ap: :append ap 1 2] reduce [e/arg1 e/arg2]              | [ap series]
                e: try [do reduce [:append 1 2]] e/arg1                         | -unnamed-
                e: try [apply :append [1 2]] e/arg1                             | -apply-
                e: try [1 + {a}] reduce [e/arg1 e/arg2]                         | [+ value2]
                e: try [o: object [ap: :append] o/ap 1 2] e/arg1                | ap
                e: try [append] reduce [e/id e/arg1 e/arg2]                     | [no-arg append series]
                e: try [f: func [a] [a] f] reduce [e/id e/arg1 e/arg2]          | [no-arg f a]
                e: try [do [1 +]] reduce [e/id e/arg1 e/arg2]                   | [no-arg + value2]
                e: try [do [if true]] reduce [e/id e/arg1 e/arg2]               | [no-arg if true-branch]
                e: try [do [a:]] reduce [e/id e/arg1]                           | [need-value a:]
                e: try [swap 1 [a]] reduce [e/id e/arg1 e/arg2 e/arg3]          | [expect-arg swap series1 #(integer!)]
                e: try [head? 1] reduce [e/id e/arg1 e/arg2 e/arg3]             | [expect-arg head? series #(integer!)]
                """)
        void answersWhatRebolAnswers(String source, String wanted, @TempDir Path directory) {
            assertThat(answerIn(directory, source)).isEqualTo(wanted);
        }
    }
}
