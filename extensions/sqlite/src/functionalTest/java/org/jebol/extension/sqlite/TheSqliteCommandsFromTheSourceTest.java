package org.jebol.extension.sqlite;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class TheSqliteCommandsFromTheSourceTest {

    private static final String PRELUDE = """
            s: import 'sqlite
            db: s/open to file! ":memory:"
            fail-of: func [code /local e] [
                either error? e: try code [reduce [e/id e/arg1]] ['no-error]
            ]
            """;

    @TempDir
    Path directory;

    private ASqliteInterpreter sqlite;

    @BeforeEach
    void openADatabase() {
        sqlite = new ASqliteInterpreter(directory);
        sqlite.answerTo(PRELUDE);
    }

    private String answerTo(String source) {
        return sqlite.answerTo(source);
    }

    @Nested
    @DisplayName("the module")
    class TheModule {

        @Test
        @DisplayName("exports nothing, so its open and close never reach lib")
        void exportsNothing() {
            assertThat(answerTo("reduce [action? :open action? :close none? find words-of lib 'last-insert-id]"))
                    .isEqualTo("[#(true) #(true) #(true)]");
        }

        @Test
        @DisplayName("holds the fourteen commands in the C's order")
        void holdsTheCommands() {
            assertThat(answerTo("copy skip words-of s 4")).isEqualTo(
                    "[info open exec eval last-insert-id finalize trace prepare reset step close columns "
                            + "initialize shutdown]");
        }

        @Test
        @DisplayName("info names the Rebol it needs, the one running and the engine's SQLite")
        void reportsTheLibrary() {
            assertThat(answerTo("""
                    i: s/info
                    reduce [
                        true? find i {^/Rebol-needed:   3.10.2^/}
                        true? find i {^/Rebol-current:  3.22.5^/}
                        true? find i {^/SQLite-version: 3.54.1^/}
                        true? find i {^/SQLite-memory:  }
                        true? find i {^/SQLite-mem-top: }
                    ]""")).isEqualTo("[#(true) #(true) #(true) #(true) #(true)]");
        }

        @Test
        @DisplayName("shutdown twice and initialize again each answer true, and a database stays usable")
        void shutsDownAndStartsAgain() {
            assertThat(answerTo("reduce [s/shutdown s/shutdown s/initialize s/initialize s/eval db {select 7}]"))
                    .isEqualTo("[#(true) #(true) #(true) #(true) [7]]");
        }
    }

    @Nested
    @DisplayName("exec")
    class Exec {

        @Test
        @DisplayName("empty and blank SQL run nothing and answer unset")
        void runsNothing() {
            assertThat(answerTo("reduce [unset? s/exec db {} unset? s/exec db {   }]"))
                    .isEqualTo("[#(true) #(true)]");
        }

        @Test
        @DisplayName("runs every statement and prints each row as name = value lines, then a blank line")
        void printsEveryRow() {
            assertThat(sqlite.printedBy("""
                    s/exec db {create table t(a, b); insert into t values (1, 'one'), (2, null); select * from t; select 9 as n}""")
                    .replace("\r\n", "\n")).isEqualTo("""
                    a = 1
                    b = one

                    a = 2
                    b = NULL

                    n = 9

                    """);
        }

        @Test
        @DisplayName("prints a blob as its text, which stops at its first zero octet")
        void printsABlobAsText() {
            assertThat(sqlite.printedBy("s/exec db {select x'41004200' as bin, 1.5 as half}").replace("\r\n", "\n"))
                    .isEqualTo("""
                            bin = A
                            half = 1.5

                            """);
        }

        @Test
        @DisplayName("a syntax error names the error and SQLite's message")
        void reportsASyntaxError() {
            assertThat(answerTo("fail-of [s/exec db {nonsense}]")).isEqualTo("""
                    [command-fail {[SQLITE] SQL logic error near "nonsense": syntax error}]""");
        }

        @Test
        @DisplayName("a constraint error names the constraint")
        void reportsAConstraint() {
            assertThat(answerTo("fail-of [s/exec db {create table nn(a not null); insert into nn values (null)}]"))
                    .isEqualTo("""
                            [command-fail {[SQLITE] constraint failed NOT NULL constraint failed: nn.a}]""");
        }

        @Test
        @DisplayName("statements before a failing one have run, and those after it have not")
        void stopsAtTheFailure() {
            assertThat(sqlite.printedBy("try [s/exec db {select 1 as one; nonsense; select 2 as two}]")
                    .replace("\r\n", "\n")).isEqualTo("""
                    one = 1

                    """);
        }

        @Test
        @DisplayName("SQL not at its head runs from where it stands")
        void runsFromTheIndex() {
            assertThat(sqlite.printedBy("s/exec db next {Xselect 7 as seven}").replace("\r\n", "\n"))
                    .isEqualTo("""
                            seven = 7

                            """);
        }
    }

    @Nested
    @DisplayName("values read back")
    class Values {

        @Test
        @DisplayName("integers at both 64-bit extremes")
        void readsIntegers() {
            assertThat(answerTo("s/eval db {select 0, -1, 9223372036854775807, -9223372036854775808}"))
                    .isEqualTo("[0 -1 9223372036854775807 -9223372036854775808]");
        }

        @Test
        @DisplayName("an integer past the extremes becomes a real, as SQLite makes it")
        void overflowsToReal() {
            assertThat(answerTo("s/eval db {select typeof(9223372036854775807 + 1)}")).isEqualTo("""
                    ["real"]""");
        }

        @Test
        @DisplayName("reals, a whole one included, as decimals")
        void readsReals() {
            assertThat(answerTo("s/eval db {select 1.5, -0.25, 3.0, 1e300}")).isEqualTo("[1.5 -0.25 3.0 1.0e300]");
        }

        @Test
        @DisplayName("text, empty and wide alike, as strings")
        void readsText() {
            assertThat(answerTo("s/eval db {select 'text', '', 'Océane', 'ěšč', '😀'}")).isEqualTo("""
                    ["text" "" "Océane" "ěšč" "😀"]""");
        }

        @Test
        @DisplayName("NULL as none and a blob as binary")
        void readsNullAndBlobs() {
            assertThat(answerTo("s/eval db {select null, x'00ff', x'01'}")).isEqualTo("[_ #{00FF} #{01}]");
        }

        @Test
        @DisplayName("a large blob whole")
        void readsALargeBlob() {
            assertThat(answerTo("b: first s/eval db {select zeroblob(100000)} reduce [length? b b/1 last b]"))
                    .isEqualTo("[100000 0 0]");
        }

        @ParameterizedTest(name = "{0}")
        @CsvSource(delimiter = '|', quoteCharacter = '`', textBlock = """
                select x''         | [#{}]
                select zeroblob(0) | [#{}]
                select x'', 1      | [#{} 1]
                select 1, x''      | [1 #{}]
                select 'abc', x''  | ["abc" #{}]
                select x'01', x''  | [#{01} #{}]
                """)
        @DisplayName("a zero-length blob is an empty binary wherever it stands, where the C crashes or repeats a column")
        void readsAnEmptyBlob(String sql, String rows) {
            assertThat(answerTo("s/eval db {" + sql + "}")).isEqualTo(rows);
        }
    }

    @Nested
    @DisplayName("eval")
    class Eval {

        @BeforeEach
        void makeATable() {
            answerTo("s/exec db {create table t(a, b); insert into t values (1, 'one'), (2, 'two')}");
        }

        @Test
        @DisplayName("a query answers its rows laid out flat")
        void answersRowsFlat() {
            assertThat(answerTo("s/eval db {select * from t order by a}")).isEqualTo("""
                    [1 "one" 2 "two"]""");
        }

        @Test
        @DisplayName("a write answers how many rows it changed")
        void answersTheCountOfChanges() {
            assertThat(answerTo("""
                    reduce [
                        s/eval db {insert into t values (3, 'three')}
                        s/eval db {update t set b = upper(b)}
                    ]""")).isEqualTo("[1 3]");
        }

        @Test
        @DisplayName("a query with no rows answers what SQLite says the last write changed, as the C asks it")
        void answersTheLastWritesCountForNoRows() {
            assertThat(answerTo("""
                    reduce [
                        s/eval db {select * from t where a > 100}
                        attempt [s/exec db {create table nn(a not null); insert into nn values (null)}]
                        s/eval db {select * from t where a > 100}
                    ]""")).isEqualTo("[2 _ 0]");
        }

        @Test
        @DisplayName("only the first of several statements runs")
        void runsOnlyTheFirst() {
            assertThat(answerTo("s/eval db {select a from t order by a; select 99}")).isEqualTo("[1 2]");
        }

        @Test
        @DisplayName("a query not at its head runs from where it stands, in a block or not")
        void runsFromTheIndex() {
            assertThat(answerTo("reduce [s/eval db next {Xselect 7} s/eval db reduce [next {Xselect 8}]]"))
                    .isEqualTo("[[7] [8]]");
        }

        @Test
        @DisplayName("a syntax error names only the error, and empty SQL is misuse")
        void reportsErrors() {
            assertThat(answerTo("reduce [fail-of [s/eval db {nonsense}] fail-of [s/eval db {}]]")).isEqualTo("""
                    [[command-fail "[SQLITE] SQL logic error"] [command-fail "[SQLITE] bad parameter or other API misuse"]]""");
        }

        @Test
        @DisplayName("a block that starts with neither text nor a statement is misuse")
        void refusesAnOddBlock() {
            assertThat(answerTo("reduce [fail-of [s/eval db []] fail-of [s/eval db [1]] fail-of [s/eval db reduce [db]]]"))
                    .isEqualTo("""
                            [[command-fail "[SQLITE] bad parameter or other API misuse"] [command-fail "[SQLITE] bad parameter or other API misuse"] [command-fail "[SQLITE] bad parameter or other API misuse"]]""");
        }

        @Test
        @DisplayName("fewer values than placeholders leave the rest NULL, and extra ones are a second run")
        void bindsWhatThereIs() {
            assertThat(answerTo("reduce [s/eval db [{select ?, ?} 5] s/eval db [{select ?} 5 6] s/eval db [{select 1}]]"))
                    .isEqualTo("[[5 _] [5] [1]]");
        }

        @Test
        @DisplayName("each bindable type goes in and comes back")
        void bindsEachType() {
            assertThat(answerTo("s/eval db [{select ?, ?, ?, ?, ?, ?} 1 2.5 {x} #(none) #(true) #(false)]"))
                    .isEqualTo("""
                            [1 2.5 "x" _ 1 0]""");
        }

        @Test
        @DisplayName("a word, a get-word, a path and a get-path are looked up first")
        void resolvesWords() {
            assertThat(answerTo("""
                    sql: {select count(*) from t}
                    o: object [y: {why}]
                    reduce [s/eval db [sql] s/eval db [:sql] s/eval db [{select ?} o/y] s/eval db [{select ?} :o/y]]"""))
                    .isEqualTo("""
                            [[2] [2] ["why"] ["why"]]""");
        }

        @Test
        @DisplayName("a string not at its head binds whole, from its head, wide or not")
        void bindsAStringFromItsHead() {
            assertThat(answerTo("s/eval db reduce [{select ?, ?} next {xA_di} next {ěšč}]")).isEqualTo("""
                    ["xA_di" "ěšč"]""");
        }

        @Test
        @DisplayName("binary, an unset word and a word naming a word are misuse")
        void refusesWhatItCannotBind() {
            assertThat(answerTo("""
                    reduce [
                        fail-of [s/eval db [{select ?} #{01}]]
                        fail-of [s/eval db [{select ?} no-such-word]]
                        fail-of [s/eval db reduce [{select ?} 'no-such-word]]
                    ]""")).isEqualTo("""
                    [[command-fail "[SQLITE] bad parameter or other API misuse"] [command-fail "[SQLITE] bad parameter or other API misuse"] [command-fail "[SQLITE] bad parameter or other API misuse"]]""");
        }

        @Test
        @DisplayName("a value with no extension type, a function or a map, binds as NULL")
        void bindsAnUntypedValueAsNull() {
            assertThat(answerTo("reduce [s/eval db [{select ?} next {x}] s/eval db reduce [{select ?} make map! []]]"))
                    .isEqualTo("[[_] [_]]");
        }

        @Test
        @DisplayName("rows may be flat, one block each, short, long, or both mixed")
        void bindsRowsEveryWay() {
            assertThat(answerTo("""
                    s/exec db {create table p(a, b, c)}
                    reduce [
                        s/eval db [{insert into p values (?, ?, ?)} 1 2 3 4 5 6]
                        s/eval db [{insert into p values (?, ?, ?)} 7 8]
                        s/eval db [{insert into p values (?, ?, ?)} [10 11 12] [13 14] [15 16 17 18]]
                        s/eval db [{insert into p values (?, ?, ?)} [20 21 22] 23 24 25]
                        s/eval db {select * from p order by rowid}
                    ]""")).isEqualTo(
                    "[2 1 3 2 [1 2 3 4 5 6 7 8 _ 10 11 12 13 14 _ 15 16 17 20 21 22 23 24 25]]");
        }

        @Test
        @DisplayName("a refused value in a row stops it, and the rows before it stay written")
        void stopsAtARefusedRow() {
            assertThat(answerTo("""
                    s/exec db {create table q(a not null)}
                    reduce [
                        fail-of [s/eval db [{insert into q values (?)} 1 #(none) 2]]
                        s/eval db {select * from q}
                    ]""")).isEqualTo("""
                    [[command-fail "[SQLITE] constraint failed"] [1]]""");
        }

        @Test
        @DisplayName("a prepared statement answers its rows, and with values binds them, resetting after DONE")
        void runsAPreparedStatement() {
            assertThat(answerTo("""
                    st: s/prepare db {select b from t where a = ?}
                    reduce [s/eval db reduce [st 1] s/eval db reduce [st 2] s/eval db reduce [st 99] s/eval db reduce [st 1]]"""))
                    .isEqualTo("""
                            [["one"] ["two"] 2 ["one"]]""");
        }

        @Test
        @DisplayName("a finalized statement is an invalid handle alone, and misuse in a block")
        void refusesAFinalizedStatement() {
            assertThat(answerTo("""
                    st: s/prepare db {select 1}
                    s/finalize st
                    reduce [fail-of [s/eval db st] fail-of [s/eval db reduce [st]] fail-of [s/eval db reduce [st 1]]]"""))
                    .isEqualTo("""
                            [[command-fail "Invalid SQLite STMT handle!"] [command-fail "[SQLITE] bad parameter or other API misuse"] [command-fail "[SQLITE] bad parameter or other API misuse"]]""");
        }

        @Test
        @DisplayName("more than a thousand rows come back whole")
        void answersManyRows() {
            assertThat(answerTo("""
                    r: s/eval db {with recursive c(x) as (select 1 union all select x + 1 from c limit 1500) select x from c}
                    reduce [length? r first r last r]""")).isEqualTo("[1500 1 1500]");
        }
    }

    @Nested
    @DisplayName("prepare, step, columns, reset and finalize")
    class Statements {

        @BeforeEach
        void makeATable() {
            answerTo("""
                    s/exec db {create table t(a, b); insert into t values (1, 'one'), (2, 'two'), (3, 'three')}
                    st: s/prepare db {select a, b from t order by a}""");
        }

        @Test
        @DisplayName("columns names each column, and none for a statement with none")
        void namesColumns() {
            assertThat(answerTo("""
                    reduce [
                        s/columns st
                        s/columns s/prepare db {select 1 as one, 2, x'01' as b}
                        s/columns s/prepare db {create table cc(a)}
                    ]""")).isEqualTo("""
                    [["a" "b"] ["one" "2" "b"] _]""");
        }

        @Test
        @DisplayName("step answers one row at a time, then none for ever until reset")
        void stepsOneRow() {
            assertThat(answerTo("""
                    r: reduce [s/step st s/step st s/step st s/step st s/step st]
                    s/reset st
                    append/only r s/step st""")).isEqualTo("""
                    [[1 "one"] [2 "two"] [3 "three"] _ _ [1 "one"]]""");
        }

        @ParameterizedTest(name = "/rows {0}")
        @CsvSource(delimiter = '|', textBlock = """
                0   | [1 "one" 2 "two" 3 "three"]
                -1  | [1 "one" 2 "two" 3 "three"]
                1   | [1 "one"]
                2   | [1 "one" 2 "two"]
                100 | [1 "one" 2 "two" 3 "three"]
                """)
        @DisplayName("/rows takes that many, and below one takes every row")
        void takesRows(int count, String rows) {
            assertThat(answerTo("s/step/rows st " + count)).isEqualTo(rows);
        }

        @Test
        @DisplayName("/rows past the end answers what is left, then none")
        void runsOutOfRows() {
            assertThat(answerTo("reduce [s/step/rows st 2 s/step/rows st 2 s/step/rows st 2]")).isEqualTo("""
                    [[1 "one" 2 "two"] [3 "three"] _]""");
        }

        @Test
        @DisplayName("/rows over a thousand, where the C stops preallocating, takes them all")
        void takesMoreThanAThousand() {
            assertThat(answerTo("""
                    m: s/prepare db {with recursive c(x) as (select 1 union all select x + 1 from c limit 1500) select x from c}
                    a: s/step/rows m 1001
                    b: s/step/rows m 1001
                    reduce [length? a last a length? b first b last b s/step/rows m 1001]"""))
                    .isEqualTo("[1001 1001 499 1002 1500 _]");
        }

        @Test
        @DisplayName("a finalized statement is an invalid handle to every command")
        void refusesAFinalizedStatement() {
            assertThat(answerTo("""
                    s/finalize st
                    reduce [fail-of [s/step st] fail-of [s/columns st] fail-of [s/reset st] fail-of [s/finalize st]]"""))
                    .isEqualTo("""
                            [[command-fail "Invalid SQLite STMT handle!"] [command-fail "Invalid SQLite STMT handle!"] [command-fail "Invalid SQLite STMT handle!"] [command-fail "Invalid SQLite STMT handle!"]]""");
        }

        @Test
        @DisplayName("an empty statement is a handle that every command refuses")
        void refusesAnEmptyStatement() {
            assertThat(answerTo("""
                    e: s/prepare db {}
                    reduce [handle? :e fail-of [s/step e] fail-of [s/columns e]]""")).isEqualTo("""
                    [#(true) [command-fail "Invalid SQLite STMT handle!"] [command-fail "Invalid SQLite STMT handle!"]]""");
        }

        @Test
        @DisplayName("prepare reports a syntax error with SQLite's message, and runs SQL from its index")
        void preparesFromTheIndex() {
            assertThat(answerTo("reduce [fail-of [s/prepare db {nonsense}] s/step s/prepare db next {Xselect 1}]"))
                    .isEqualTo("""
                            [[command-fail {[SQLITE] SQL logic error near "nonsense": syntax error}] [1]]""");
        }

        @Test
        @DisplayName("a write steps again after DONE, where a read answers none")
        void stepsAWriteAgain() {
            assertThat(answerTo("""
                    w: s/prepare db {insert into t values (9, 'nine')}
                    reduce [s/step w s/step w s/step w s/eval db {select count(*) from t where a = 9}]"""))
                    .isEqualTo("[_ _ _ [3]]");
        }

        @Test
        @DisplayName("a failing step is the bare error, without the prefix")
        void reportsAFailingStep() {
            assertThat(answerTo("""
                    s/exec db {create table u(a unique)}
                    d: s/prepare db {insert into u values (1)}
                    reduce [s/step d fail-of [s/step d]]""")).isEqualTo("""
                    [_ [command-fail "constraint failed"]]""");
        }
    }

    @Nested
    @DisplayName("step/with")
    class StepWith {

        @BeforeEach
        void prepareTwoPlaceholders() {
            answerTo("w: s/prepare db {select ?, ?}");
        }

        @ParameterizedTest(name = "{0}")
        @CsvSource(delimiter = '|', textBlock = """
                [1 2]             | [1 2]
                [1]               | [1 _]
                []                | [_ _]
                [1 2 3]           | [1 2]
                [1.5 {x}]         | [1.5 "x"]
                [#(none) #(true)] | [_ 1]
                [#(false) {ěšč}]  | [0 "ěšč"]
                """)
        @DisplayName("binds by position, and fewer values leave NULL while more are ignored")
        void bindsByPosition(String values, String row) {
            assertThat(answerTo("s/step/with w " + values)).isEqualTo(row);
        }

        @Test
        @DisplayName("a string not at its head binds whole, from its head")
        void bindsAStringFromItsHead() {
            assertThat(answerTo("s/step/with w reduce [next {xA_di} next {ěšč}]")).isEqualTo("""
                    ["xA_di" "ěšč"]""");
        }

        @Test
        @DisplayName("a word, a get-word and a get-path are looked up, and a path is not")
        void resolvesSomeWords() {
            assertThat(answerTo("""
                    x: 41 o: object [y: {why}]
                    a: s/step/with w [x :x] s/reset w
                    b: s/step/with w [:o/y 1] s/reset w
                    reduce [a b fail-of [s/step/with w [o/y 1]]]"""))
                    .isEqualTo("""
                            [[41 41] ["why" 1] [command-fail "[SQLITE] Unsupported value type!"]]""");
        }

        @ParameterizedTest(name = "{0}")
        @CsvSource({"[#{01} 1]", "[[1] 1]", "[unset-word 1]", "['x 1]", "[1 2-Nov-2026]", "[(1 + 1) 1]",
                "[1 $1]", "[1 10%]"})
        @DisplayName("any other type is unsupported, binary and block included")
        void refusesOtherTypes(String values) {
            assertThat(answerTo("fail-of [s/step/with w " + values + "]")).isEqualTo("""
                    [command-fail "[SQLITE] Unsupported value type!"]""");
        }

        @Test
        @DisplayName("named placeholders bind by position too")
        void bindsNamedPlaceholders() {
            assertThat(answerTo("s/step/with s/prepare db {select :a, @b, $c, ?4} [1 2 3 4]")).isEqualTo("[1 2 3 4]");
        }

        @Test
        @DisplayName("binding does not reset, so a read already stepped answers none")
        void doesNotReset() {
            assertThat(answerTo("""
                    a: s/step/with w [1 2]
                    b: s/step/with w [3 4]
                    s/reset w
                    reduce [a b s/step/with w [5 6]]""")).isEqualTo("[[1 2] _ [5 6]]");
        }
    }

    @Nested
    @DisplayName("handles")
    class Handles {

        @Test
        @DisplayName("a database and a statement are handles of their own kinds")
        void moldAsTheirKinds() {
            assertThat(answerTo("reduce [mold :db mold s/prepare db {select 1}]")).isEqualTo("""
                    ["#(handle! sqlite-db)" "#(handle! sqlite-stmt)"]""");
        }

        @Test
        @DisplayName("a statement where a database belongs is an invalid DB handle, to every command that takes one")
        void refusesAStatementForADatabase() {
            assertThat(answerTo("""
                    st: s/prepare db {select 1}
                    unique collect [foreach code [
                        [s/exec st {select 1}] [s/eval st {select 1}] [s/prepare st {select 1}]
                        [s/last-insert-id st] [s/trace st 0] [s/close st]
                    ] [keep/only fail-of code]]""")).isEqualTo("""
                    [[command-fail "Invalid SQLite DB handle!"]]""");
        }

        @Test
        @DisplayName("a database where a statement belongs is an invalid STMT handle")
        void refusesADatabaseForAStatement() {
            assertThat(answerTo("""
                    unique collect [foreach code [
                        [s/step db] [s/columns db] [s/reset db] [s/finalize db] [s/eval db db]
                    ] [keep/only fail-of code]]""")).isEqualTo("""
                    [[command-fail "Invalid SQLite STMT handle!"]]""");
        }

        @Test
        @DisplayName("another extension's handle is refused the same way, and info/of answers an empty string")
        void refusesAnotherKind() {
            assertThat(answerTo("""
                    k: rc4/key #{0102}
                    reduce [fail-of [s/exec k {select 1}] fail-of [s/step k] fail-of [s/eval db reduce [k]] s/info/of k]"""))
                    .isEqualTo("""
                            [[command-fail "Invalid SQLite DB handle!"] [command-fail "Invalid SQLite STMT handle!"] [command-fail "[SQLITE] bad parameter or other API misuse"] ""]""");
        }

        @Test
        @DisplayName("info/of a statement reports its last result, its parameters and its data count")
        void describesAStatement() {
            assertThat(answerTo("""
                    st: s/prepare db {select ?, ?}
                    s/step/with st [1 2]
                    i: s/info/of st
                    reduce [
                        true? find i {^/last-result-code:  100^/}
                        true? find i {^/bind-parameters:   2^/}
                        true? find i {^/data-count:        2^/}
                    ]""")).isEqualTo("[#(true) #(true) #(true)]");
        }
    }

    @Nested
    @DisplayName("a closed database")
    class Closed {

        @Test
        @DisplayName("closing twice is fine, and info/of then names no connection")
        void closesTwice() {
            assertThat(answerTo("""
                    reduce [unset? s/close db unset? s/close db true? find s/info/of db {sqlite-ctx-DB:  <(nil)>}]"""))
                    .isEqualTo("[#(true) #(true) #(true)]");
        }

        @Test
        @DisplayName("exec and prepare are misuse with no connection's message, and eval misuse alone")
        void refusesWork() {
            assertThat(answerTo("""
                    s/close db
                    reduce [fail-of [s/exec db {select 1}] fail-of [s/prepare db {select 1}] fail-of [s/eval db {select 1}]]"""))
                    .isEqualTo("""
                            [[command-fail {[SQLITE] bad parameter or other API misuse out of memory}] [command-fail {[SQLITE] bad parameter or other API misuse out of memory}] [command-fail "[SQLITE] bad parameter or other API misuse"]]""");
        }

        @Test
        @DisplayName("last-insert-id answers 0, where the C crashes")
        void answersNoLastInsertId() {
            assertThat(answerTo("s/exec db {create table t(a); insert into t values (1)} s/close db s/last-insert-id db"))
                    .isEqualTo("0");
        }

        @Test
        @DisplayName("a statement prepared before the close keeps working, because the connection waits for it")
        void keepsItsStatements() {
            assertThat(answerTo("""
                    st: s/prepare db {select 1}
                    s/close db
                    a: s/eval db st
                    s/reset st
                    reduce [a s/columns st s/step st unset? s/finalize st]""")).isEqualTo("""
                    [[1] ["1"] [1] #(true)]""");
        }
    }

    @Nested
    @DisplayName("last-insert-id")
    class LastInsertId {

        @Test
        @DisplayName("answers the rowid of the last insert")
        void answersTheLastRowid() {
            assertThat(answerTo("""
                    s/exec db {create table t(a); insert into t values (1), (2), (3)}
                    s/last-insert-id db""")).isEqualTo("3");
        }
    }
}
