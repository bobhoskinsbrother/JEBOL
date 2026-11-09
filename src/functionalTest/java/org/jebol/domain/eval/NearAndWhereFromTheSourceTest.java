package org.jebol.domain.eval;

import org.jebol.application.AnInterpreter;
import org.jebol.application.Interpreter;
import org.jebol.domain.value.BlockValue;
import org.jebol.domain.value.ErrorValue;
import org.jebol.domain.value.Molder;
import org.jebol.domain.value.Value;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class NearAndWhereFromTheSourceTest {

    private static final String HELPERS = """
            f: func [x [block!]] [x]
            h: func [] [6]
            q: func ['x [block!]] [x]
            u: func ['x [integer!]] [x]
            z: func [] [1 / 0]
            """;

    private record Case(String source, String id, String near, String where) {
    }

    private static final List<Case> GRID = List.of(
            // A refusal, by how the last argument arrived.
            new Case("f 6", "expect-arg", "[try [f 6]]", "[try]"),
            new Case("f (6)", "expect-arg", "[try [f (6)]]", "[try]"),
            new Case("f h", "expect-arg", "[try [f h]]", "[try]"),
            new Case("f :h", "expect-arg", "[try [f :h]]", "[try]"),
            new Case("f (1 + 1)", "expect-arg", "[try [f (1 + 1)]]", "[try]"),
            new Case("1 + {a}", "expect-arg", """
                    [try [1 + "a"]]""", "[try]"),
            new Case("1 + ({a})", "expect-arg", """
                    [try [1 + ("a")]]""", "[try]"),
            new Case("q 6", "expect-arg", "[try [q 6]]", "[try]"),
            new Case("if false () 6", "expect-arg", "[try [if false () 6]]", "[try]"),
            new Case("u", "expect-arg", "[try [u]]", "[try]"),
            // A call that reaches the end of its block still wanting one.
            new Case("compress", "no-arg", "[try [compress]]", "[try]"),
            new Case("f", "no-arg", "[try [f]]", "[try]"),
            // A raise made with the call itself open.
            new Case("compress {a} 'brotli", "invalid-arg", """
                    [compress "a" 'brotli]""", "[compress try]"),
            new Case("compress {a} (quote brotli)", "invalid-arg", """
                    [compress "a" (quote brotli)]""", "[compress try]"),
            new Case("z", "zero-divide", "[/ 0]", "[/ z try]"),
            new Case("1 / 0", "zero-divide", "[/ 0]", "[/ try]"),
            new Case("do (quote (1 / 0))", "zero-divide", "[/ 0]", "[/ do try]"),
            new Case("do [1 / 0]", "zero-divide", "[/ 0]", "[/ do try]"),
            new Case("reduce [1 / 0]", "zero-divide", "[/ 0]", "[/ reduce try]"),
            new Case("repeat i 1 [1 / 0]", "zero-divide", "[/ 0]", "[/ repeat try]"),
            new Case("foreach i [1] [1 / 0]", "zero-divide", "[/ 0]", "[/ foreach try]"),
            new Case("if true [1 / 0]", "zero-divide", "[/ 0]", "[/ if try]"),
            new Case("while [1 / 0] [1]", "zero-divide", "[/ 0]", "[/ while try]"),
            new Case("parse {a} [(1 / 0)]", "zero-divide", "[/ 0]", "[/ parse try]"),
            new Case("make object! [x: 1 / 0]", "zero-divide", "[/ 0]", "[/ make try]"),
            new Case("either 1 / 0 [1] [2]", "zero-divide", "[/ 0 [1] [2]]", "[/ try]"),
            new Case("copy/part {abc} {x}", "invalid-part", """
                    [copy/part "abc" "x"]""", "[copy try]"),
            new Case("apply :f [6]", "expect-arg", "[apply :f [6]]", "[apply try]"),
            // The two that carry no location at all, in both.
            new Case("cause-error 'user 'message {x}", "message", "_", "_"),
            new Case("do make error! {x}", "message", "_", "_"));

    private ErrorValue failureOf(String source) {
        Interpreter interpreter = AnInterpreter.create();
        interpreter.defineNewWordsIn(HELPERS + source);
        Value result = interpreter.run(HELPERS + source).value();
        assertThat(result).isInstanceOf(ErrorValue.class);
        return (ErrorValue) result;
    }

    private static String molded(Optional<Value> location) {
        return location.map(Molder::moldFlat).orElse("_");
    }

    @TestFactory
    @DisplayName("an error is located the same however the failing call's last argument arrived")
    Stream<DynamicTest> locatesHoweverTheArgumentArrived() {
        return GRID.stream().map(each -> DynamicTest.dynamicTest(
                "try [" + each.source() + "] answers " + each.id() + " at " + each.near(),
                () -> {
                    ErrorValue failure = failureOf("try [" + each.source() + "]\n");
                    assertThat(failure.errorId()).isEqualTo(each.id());
                    assertThat(molded(failure.near())).isEqualTo(each.near());
                    assertThat(molded(failure.whereChain())).isEqualTo(each.where());
                    failure.near().ifPresent(near -> assertThat(near)
                            .as("a location is always a block, never a paren")
                            .isInstanceOf(BlockValue.class));
                }));
    }

    @Test
    @DisplayName("a call written over a paren answers a block over the paren's series")
    void makesABlockOverTheParensSeries() {
        ErrorValue failure = failureOf("try [do (quote (1 / 0))]\n");

        assertThat(failure.near()).containsInstanceOf(BlockValue.class);
        assertThat(molded(failure.near())).isEqualTo("[/ 0]");
        assertThat(molded(failure.whereChain())).isEqualTo("[/ do try]");
    }

    @Test
    @DisplayName("a raise inside a port's awake is located like any other")
    void locatesARaiseInsideAPortsAwake() {
        ErrorValue failure = failureOf("""
                p: to port! system/standard/port
                p/awake: func [event] [1 / 0]
                try [wake-up p make event! [type: 'read]]
                """);

        assertThat(failure.errorId()).isEqualTo("zero-divide");
        assertThat(molded(failure.near())).isEqualTo("[/ 0]");
        assertThat(molded(failure.whereChain())).isEqualTo("[/ -apply- wake-up try]");
    }

    @Test
    @DisplayName("at the top of a console line a refusal carries no near and no where")
    void saysNothingAtTheTopOfAConsoleLine() {
        ErrorValue failure = failureOf("(mold/part {abc} 2.5)\n");

        assertThat(failure.errorId()).isEqualTo("expect-arg");
        assertThat(failure.near()).isEmpty();
        assertThat(failure.whereChain()).isEmpty();
    }
}
