package org.jebol.domain.eval.natives;

import org.jebol.application.Bounds;
import org.jebol.application.Interpreter;
import org.jebol.domain.eval.Encodings;
import org.jebol.domain.eval.GrantedServices;
import org.jebol.domain.host.ProcessPort;
import org.jebol.domain.eval.actions.RandomAction;
import org.jebol.domain.host.HostService;
import org.jebol.domain.value.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.OptionalLong;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WhatEachRemainingNativeDeclaresTest {

    private static final long REAL_USER = 501;
    private static final long EFFECTIVE_USER = 502;
    private static final long REAL_GROUP = 20;
    private static final long EFFECTIVE_GROUP = 21;

    private final class AHostThatKnowsWhoItRunsAs implements ProcessPort {

        @Override
        public ProgramResult run(ProgramToStart program) {
            throw new UnsupportedOperationException("no program is started by ACCESS-OS");
        }

        @Override
        public OptionalLong identity(WhoTheProcessRunsAs asked) {
            return OptionalLong.of(switch (asked) {
                case REAL_USER -> REAL_USER;
                case EFFECTIVE_USER -> EFFECTIVE_USER;
                case REAL_GROUP -> REAL_GROUP;
                case EFFECTIVE_GROUP -> EFFECTIVE_GROUP;
            });
        }
    }

    private Bounds everythingGranted() {
        Bounds everything = Bounds.standard();
        for (HostService service : HostService.values()) {
            everything = everything.granting(service);
        }
        return everything;
    }

    private String answerTo(String source) {
        return Molder.moldFlat(Interpreter.withBounds(everythingGranted()).run(source).value());
    }

    private String answerFromAHostThatKnowsWhoItRunsAs(String source) {
        Interpreter interpreter = Interpreter.withBounds(everythingGranted());
        interpreter.useProcesses(new AHostThatKnowsWhoItRunsAs());
        return Molder.moldFlat(interpreter.run(source).value());
    }

    private String argumentsOfTheErrorFrom(String source) {
        return answerTo("e: try [" + source + "] reduce [e/id e/arg1 e/arg2 e/arg3]");
    }

    Stream<Arguments> eachNameAndItsRefinements() {
        GrantedServices granted = new GrantedServices();
        return Stream.of(
                Arguments.of(new NowNative(granted), "now", Set.of("year", "month", "day",
                        "time", "zone", "date", "weekday", "yearday", "precise", "utc")),
                Arguments.of(new AlsoNative(), "also", Set.of()),
                Arguments.of(new CommentNative(), "comment", Set.of()),
                Arguments.of(new ToValueNative(), "to-value", Set.of()),
                Arguments.of(new TraceNative(), "trace", Set.of("back", "function")),
                Arguments.of(new LoadExtensionNative(), "load-extension", Set.of("dispatch")),
                Arguments.of(new DoCallbackNative(), "do-callback", Set.of()),
                Arguments.of(new DoCommandsNative(), "do-commands", Set.of()),
                Arguments.of(new AccessOsNative(granted), "access-os", Set.of("set")),
                Arguments.of(new RandomAction(new Encodings()), "random",
                        Set.of("seed", "secure", "only")));
    }

    @ParameterizedTest(name = "{1} declares {2}")
    @MethodSource("eachNameAndItsRefinements")
    @DisplayName("each answers to the name boot/natives.reb or boot/actions.reb gives it, with the refinements it declares")
    void answersToItsNameWithItsRefinements(DefaultNative definition, String name,
                                            Set<String> refinements) {
        assertThat(definition.nativeName()).isEqualTo(name);
        assertThat(definition.refinementsDeclaredApart()).isEqualTo(refinements);
    }

    @Nested
    @DisplayName("each declaration matches Rebol's")
    class TheDeclarations {

        @Test
        @DisplayName("now takes no argument at all")
        void nowTakesNothing() {
            assertThat(new NowNative(new GrantedServices()).parametersAsWritten()).isEmpty();
        }

        @Test
        @DisplayName("also and to-value take values of any type, unset included")
        void alsoAndToValueTakeAnyType() {
            List<Parameter> also = new AlsoNative().parametersAsWritten();
            assertThat(also).extracting(Parameter::name).containsExactly("value1", "value2");
            List<Parameter> toValue = new ToValueNative().parametersAsWritten();
            assertThat(toValue).extracting(Parameter::name).containsExactly("value");
            for (Parameter each : List.of(also.get(0), also.get(1), toValue.getFirst())) {
                for (Datatype datatype : TypesetValue.ANY_TYPE.members()) {
                    assertThat(each.accepts(datatype)).as(datatype.literalSpelling()).isTrue();
                }
                assertThat(each.accepts(UnsetValue.TYPE)).isTrue();
            }
        }

        @Test
        @DisplayName("comment and random take one value with no type named")
        void commentAndRandomNameNoType() {
            assertThat(new CommentNative().parametersAsWritten()).extracting(Parameter::name)
                    .containsExactly("value");
            assertThat(new RandomAction(new Encodings()).parametersAsWritten()).extracting(Parameter::name)
                    .containsExactly("value");
        }

        @Test
        @DisplayName("trace's mode is an integer or a logic and nothing else")
        void traceTakesAnIntegerOrALogic() {
            Parameter mode = new TraceNative().parametersAsWritten().getFirst();
            assertThat(mode.name()).isEqualTo("mode");
            assertThat(mode.accepts(IntegerValue.TYPE)).isTrue();
            assertThat(mode.accepts(LogicValue.TYPE)).isTrue();
            assertThat(mode.accepts(DecimalValue.TYPE)).isFalse();
            assertThat(mode.accepts(NoneValue.TYPE)).isFalse();
            assertThat(mode.accepts(StringValue.TYPE)).isFalse();
        }

        @Test
        @DisplayName("load-extension takes a file or a binary, and a handle that belongs to /dispatch")
        void loadExtensionTakesAFileOrABinary() {
            List<Parameter> declared = new LoadExtensionNative().parametersAsWritten();
            assertThat(declared).extracting(Parameter::name).containsExactly("name", "function");
            Parameter name = declared.get(0);
            assertThat(name.accepts(FileValue.TYPE)).isTrue();
            assertThat(name.accepts(BinaryValue.TYPE)).isTrue();
            assertThat(name.accepts(StringValue.TYPE)).isFalse();
            Parameter function = declared.get(1);
            assertThat(function.owningRefinement()).contains("dispatch");
            assertThat(function.accepts(HandleValue.TYPE)).isTrue();
            assertThat(function.accepts(NoneValue.TYPE)).isFalse();
        }

        @Test
        @DisplayName("do-callback takes an event and do-commands a block")
        void doCallbackTakesAnEventAndDoCommandsABlock() {
            Parameter event = new DoCallbackNative().parametersAsWritten().getFirst();
            assertThat(event.name()).isEqualTo("event");
            assertThat(event.accepts(EventValue.TYPE)).isTrue();
            assertThat(event.accepts(IntegerValue.TYPE)).isFalse();
            Parameter commands = new DoCommandsNative().parametersAsWritten().getFirst();
            assertThat(commands.name()).isEqualTo("commands");
            assertThat(commands.accepts(BlockValue.TYPE)).isTrue();
            assertThat(commands.accepts(ParenValue.TYPE)).isFalse();
            assertThat(commands.accepts(IntegerValue.TYPE)).isFalse();
        }

        @Test
        @DisplayName("access-os takes a word, and an integer or a block that belongs to /set")
        void accessOsTakesAWordAndAValueForSet() {
            List<Parameter> declared = new AccessOsNative(new GrantedServices()).parametersAsWritten();
            assertThat(declared).extracting(Parameter::name).containsExactly("field", "value");
            Parameter field = declared.get(0);
            assertThat(field.accepts(WordValue.TYPE)).isTrue();
            assertThat(field.accepts(LitWordValue.TYPE)).isFalse();
            assertThat(field.accepts(IntegerValue.TYPE)).isFalse();
            Parameter value = declared.get(1);
            assertThat(value.owningRefinement()).contains("set");
            assertThat(value.accepts(IntegerValue.TYPE)).isTrue();
            assertThat(value.accepts(BlockValue.TYPE)).isTrue();
            assertThat(value.accepts(DecimalValue.TYPE)).isFalse();
            assertThat(value.accepts(StringValue.TYPE)).isFalse();
        }

        @ParameterizedTest(name = "spec-of :{0} is r3's")
        @CsvSource(delimiter = '|', quoteCharacter = '`', textBlock = """
                now            | ["Returns date and time." /year "Returns year only" /month "Returns month only" /day "Returns day of the month only" /time "Returns time only" /zone "Returns time zone offset from UCT (GMT) only" /date "Returns date only" /weekday {Returns day of the week as integer (Monday is day 1)} /yearday "Returns day of the year (Julian)" /precise "High precision time" /utc "Universal time (no zone)"]
                also           | [{Returns the first value, but also evaluates the second.} value1 [any-type!] value2 [any-type!]]
                comment        | ["Ignores the argument value and returns nothing." value "A string, block, file, etc."]
                to-value       | ["Returns the value if it is a value, NONE if unset." value [any-type!]]
                trace          | [{Enables and disables evaluation tracing and backtrace.} mode [integer! logic!] /back {Set mode ON to enable or integer for lines to display} /function "Traces functions only (less output)"]
                load-extension | ["Low level extension module loader (for DLLs)." name [file! binary!] "DLL file or UTF-8 source" /dispatch {Specify native command dispatch (from hosted extensions)} function [handle!] "Command dispatcher (native)"]
                do-callback    | ["Internal function to process callback events." event [event!] "Callback event"]
                do-commands    | [{Evaluate a block of extension module command functions (special evaluation rules.)} commands [block!] "Series of commands and their arguments"]
                access-os      | [{Access to various operating system functions (getuid, setuid, getpid, kill, etc.)} field [word!] "Valid words: uid, euid, gid, egid, pid" /set "To set or kill pid (sig 15)" value [integer! block!] {Argument, such as uid, gid, or pid (in which case, it could be a block with the signal no)}]
                random         | [{Returns a random value of the same datatype; or shuffles series.} value "Maximum value of result (modified when series)" /seed "Restart or randomize" /secure "Returns a cryptographically secure random number" /only "Pick a random value from a series"]
                """)
        void specOfShowsRebolsSpec(String name, String spec) {
            assertThat(answerTo("spec-of :" + name)).isEqualTo(spec);
        }

        @ParameterizedTest(name = "{0} is a {1}")
        @CsvSource({
                "now, #(native!)", "also, #(native!)", "comment, #(native!)",
                "to-value, #(native!)", "trace, #(native!)", "load-extension, #(native!)",
                "do-callback, #(native!)", "do-commands, #(native!)", "access-os, #(native!)",
                "random, #(action!)"})
        void eachIsTheKindRebolSays(String name, String kind) {
            assertThat(answerTo("type? :" + name)).isEqualTo(kind);
        }
    }

    @Nested
    @DisplayName("the three extension points are refused whatever is granted, once their arguments pass")
    class TheExtensionPoints {

        @ParameterizedTest(name = "{0} is refused as never portable")
        @CsvSource(delimiter = '|', textBlock = """
                load-extension %a.so
                load-extension #{00}
                load-extension/dispatch #{00} rc4/key #{0102}
                do-callback make event! [type: 'key]
                do-commands []
                do-commands [a]
                """)
        void eachIsRefused(String source) {
            assertThat(answerTo("e: try [" + source + "] reduce [e/id true? find form e/arg1 {never portable}]"))
                    .isEqualTo("[no-service #(true)]");
        }

        @Test
        @DisplayName("an RC4 key is a handle, so it reaches load-extension's refusal under /dispatch")
        void anRc4KeyIsAHandle() {
            assertThat(answerTo("type? rc4/key #{0102}")).isEqualTo("#(handle!)");
        }

        @ParameterizedTest(name = "{0} is refused for its argument first")
        @CsvSource(delimiter = '|', textBlock = """
                load-extension 1                      | [expect-arg load-extension name #(integer!)]
                load-extension {a.so}                 | [expect-arg load-extension name #(string!)]
                load-extension/dispatch #{00} none    | [expect-arg load-extension function #(none!)]
                do-callback 1                         | [expect-arg do-callback event #(integer!)]
                do-callback none                      | [expect-arg do-callback event #(none!)]
                do-commands 1                         | [expect-arg do-commands commands #(integer!)]
                """)
        void eachChecksItsArgumentFirst(String source, String expected) {
            assertThat(argumentsOfTheErrorFrom(source)).isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("access-os asks the host who the process runs as")
    class AccessOs {

        @ParameterizedTest(name = "access-os ''{0} is {1}")
        @CsvSource({"uid, 501", "euid, 502", "gid, 20", "egid, 21", "UID, 501", "Egid, 21"})
        void eachFieldAsksTheHostItsOwnQuestion(String field, String expected) {
            assertThat(answerFromAHostThatKnowsWhoItRunsAs("access-os '" + field)).isEqualTo(expected);
        }

        @ParameterizedTest(name = "access-os ''{0} is not-here without a host to ask")
        @CsvSource({"uid", "euid", "gid", "egid"})
        void aHostThatCannotSayIsNotHere(String field) {
            assertThat(argumentsOfTheErrorFrom("access-os '" + field))
                    .isEqualTo("[not-here " + field + " _ _]");
        }

        @Test
        @DisplayName("pid is the JVM's own process number, with or without a host to ask")
        void pidIsTheProcessNumber() {
            String ours = String.valueOf(ProcessHandle.current().pid());
            assertThat(answerTo("access-os 'pid")).isEqualTo(ours);
            assertThat(answerFromAHostThatKnowsWhoItRunsAs("access-os 'pid")).isEqualTo(ours);
        }

        @ParameterizedTest(name = "{0} is {1}")
        @CsvSource(delimiter = '|', textBlock = """
                access-os 'nope                   | [invalid-arg nope _ _]
                access-os/set 'nope 1             | [invalid-arg nope _ _]
                access-os/set 'uid 1              | [not-here uid _ _]
                access-os/set 'egid 0             | [not-here egid _ _]
                access-os/set 'uid [1]            | [invalid-arg [1] _ _]
                access-os/set 'gid []             | [invalid-arg [] _ _]
                access-os/set 'pid []             | [invalid-arg [] _ _]
                access-os/set 'pid [1]            | [invalid-arg [1] _ _]
                access-os/set 'pid [1 2 3]        | [invalid-arg [1 2 3] _ _]
                access-os/set 'pid [a b]          | [invalid-arg a _ _]
                access-os/set 'pid [1 x]          | [invalid-arg x _ _]
                access-os/set 'pid [1.0 15]       | [invalid-arg 1.0 _ _]
                access-os/set 'pid 99999999       | [process-not-found 99999999 _ _]
                access-os/set 'pid [99999999 9]   | [process-not-found 99999999 _ _]
                """)
        void refusesAsTheCDoes(String source, String expected) {
            assertThat(argumentsOfTheErrorFrom(source)).isEqualTo(expected);
        }

        @Test
        @DisplayName("ending a process the operator may not signal is permission-denied, naming nothing")
        void permissionDeniedNamesNothing() {
            assertThat(argumentsOfTheErrorFrom("access-os/set 'pid 1"))
                    .isEqualTo("[permission-denied _ _ _]");
        }
    }

    @Nested
    @DisplayName("random answers what r3 answers")
    class Random {

        @ParameterizedTest(name = "{0} is {1}")
        @CsvSource(delimiter = '|', textBlock = """
                random/seed 1 random 100%       | 6.8454436335908281%
                random/seed 1 random 50%        | 3.422721816795414%
                random/seed 1 random -1%        | -0.068454436335908277%
                random 0%                       | 0%
                random/seed 1 random 1.0        | 0.068454436335908281
                random 0.0                      | 0.0
                """)
        void aPercentStaysAPercent(String source, String expected) {
            assertThat(Molder.moldAll(Interpreter.create().run(source).value())).isEqualTo(expected);
        }

        @ParameterizedTest(name = "{0} is {1}")
        @CsvSource(delimiter = '|', textBlock = """
                random none                                    | [cannot-use random: #(none!) _]
                random/seed none                               | [cannot-use random: #(none!) _]
                random/seed 'a                                 | [cannot-use random: #(word!) _]
                random/seed [1]                                | [bad-refines _ _ _]
                random/seed make vector! [integer! 8 [1]]      | [bad-refines _ _ _]
                random/only make vector! [integer! 8 [1 2 3]]  | [bad-refines _ _ _]
                random 4611686018427387905                     | [overflow _ _ _]
                random -4611686018427387905                    | [overflow _ _ _]
                random 9223372036854775807                     | [overflow _ _ _]
                """)
        void refusesCarryingWhatTheCCarries(String source, String expected) {
            assertThat(argumentsOfTheErrorFrom(source)).isEqualTo(expected);
        }

        @ParameterizedTest(name = "{0} is {1}")
        @CsvSource(delimiter = '|', textBlock = """
                random/seed 1 random 4611686018427387904   | 315690366949635975
                random/seed 1 random 4611686018427387903   | 315690366949635975
                random/seed 1 random -4611686018427387904  | -315690366949635975
                random/seed 1 random 1                     | 1
                random/seed 1 random -1                    | -1
                random 0                                   | 0
                """)
        void theLargestLimitIsTwoToTheSixtySecond(String source, String expected) {
            assertThat(answerTo(source)).isEqualTo(expected);
        }

        @ParameterizedTest(name = "{0} is {1}")
        @CsvSource(delimiter = '|', textBlock = """
                random/seed 1 random/secure 100                    | 46
                random/seed 1 random/secure 1                      | 1
                random/seed 1 random/secure 0                      | 0
                random/seed 1 random/secure -10                    | -6
                random/seed 1 random/secure 4611686018427387905    | 1259905703715269341
                random/seed 1 random/secure 9223372036854775807    | 5871591722142657246
                random/seed 1 random/secure -9223372036854775808   | -5871591722142657246
                random/seed 1 random/secure 1.0                    | 0.31829962505474868
                random/seed 1 random/secure 100%                   | 31.829962505474868%
                random/seed 1 random/secure 1.2.3                  | 1.255.3
                random/seed 1 random/secure 10x20                  | 6x10
                random/seed 1 random/secure 1:00                   | 0:42:02.142657246
                random/seed 1 random/secure true                   | #(true)
                random/seed 1 random/secure [1 2 3 4 5]            | [4 3 1 5 2]
                random/seed 1 random/secure/only [1 2 3 4 5]       | 2
                random/seed 1 random/secure {abcdef}               | "beadcf"
                random/seed 1 random/secure/only {abcdef}          | #"f"
                """)
        void secureHashesEachDrawTheWayTheCDoes(String source, String expected) {
            assertThat(Molder.moldAll(Interpreter.create().run(source).value())).isEqualTo(expected);
        }

        @Test
        @DisplayName("random/secure draws differently from random after the same seed")
        void secureIsNotThePlainDraw() {
            assertThat(answerTo("random/seed 1 a: random 100 random/seed 1 b: random/secure 100 a = b"))
                    .isEqualTo("#(false)");
        }
    }

    @Nested
    @DisplayName("now refuses two questions at once")
    class Now {

        @ParameterizedTest(name = "{0} is bad-refines naming nothing")
        @CsvSource(delimiter = '|', textBlock = """
                now/year/month
                now/date/time
                now/precise/time/date
                now/utc/zone
                """)
        void twoQuestionsNameNothing(String source) {
            assertThat(argumentsOfTheErrorFrom(source)).isEqualTo("[bad-refines _ _ _]");
        }

        @ParameterizedTest(name = "{0} answers")
        @CsvSource({"now/precise/time, #(time!)", "now/date/precise, #(date!)", "now/utc, #(date!)"})
        void preciseIsNotAQuestion(String source, String expected) {
            assertThat(answerTo("type? " + source)).isEqualTo(expected);
        }
    }
}
