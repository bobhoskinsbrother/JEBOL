package org.jebol.domain.value;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What MOLD/ALL writes, and what MOLD/FLAT does to it.
 *
 * <p>{@code Pre_Mold} writes {@code #(type! } under MOLD/ALL and
 * {@code make type! } without it, and {@code End_Mold} closes the bracket only
 * in the first case. Nine datatypes go through that pair, and JEBOL had it for
 * two of them.
 *
 * <p>MOLD/FLAT is the other half. It is a flag on the mold rather than a way
 * of molding, so it combines with MOLD/ALL; picking one function out of a
 * chain of conditionals threw away whichever refinement lost, and
 * {@code mold/flat/all} quietly did what {@code mold/flat} does.
 */
class MoldedConstructFormsFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Nested
    @DisplayName("the construct form, one datatype at a time")
    class TheConstructForm {

        @Test
        @DisplayName("an object names itself under /ALL and MAKEs itself without")
        void anObjectNamesItself() {
            assertThat(answerTo("""
                    mold/flat/all make object! [a: 1]"""))
                    .isEqualTo("\"#(object! [a: 1])\"");
            assertThat(answerTo("""
                    mold/flat make object! [a: 1]"""))
                    .isEqualTo("\"make object! [a: 1]\"");
        }

        @Test
        @DisplayName("a map changes its brackets as well as gaining a name")
        void aMapChangesItsBrackets() {
            assertThat(answerTo("""
                    reduce [mold/flat/all make map! [a 1] mold/flat make map! [a 1]]"""))
                    .isEqualTo("[\"#(map! [a: 1])\" \"#[a: 1]\"]");
        }

        @Test
        @DisplayName("and an empty map keeps both halves of them")
        void anEmptyMapKeepsBothHalves() {
            assertThat(answerTo("""
                    reduce [mold/all make map! [] mold make map! []]"""))
                    .isEqualTo("[\"#(map! [])\" \"#[]\"]");
        }

        @Test
        @DisplayName("a gob writes the same spec either way round")
        void aGobWritesItsSpec() {
            assertThat(answerTo("""
                    reduce [mold/all make gob! [] mold make gob! []]"""))
                    .isEqualTo("[\"#(gob! [offset: 0x0 size: 100x100])\""
                            + " \"make gob! [offset: 0x0 size: 100x100]\"]");
        }

        @Test
        @DisplayName("an event puts each field on a line, and one before the bracket")
        void anEventPutsEachFieldOnALine() {
            assertThat(answerTo("""
                    mold/all make event! []""")).isEqualTo("""
                    "#(event! [^/])\"""");
            assertThat(answerTo("""
                    mold/flat/all make event! [type: 'lookup code: 3]"""))
                    .isEqualTo("\"#(event! [type: 'lookup code: 3])\"");
        }

        @Test
        @DisplayName("and the construct form of an event reads back as one")
        void anEventReadsBack() {
            assertThat(answerTo("""
                    event? load mold/all make event! []""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("a function writes the spec and body that would make it again")
        void aFunctionWritesItsSpecAndBody() {
            assertThat(answerTo("""
                    reduce [
                        mold/flat func [a][print a]
                        mold/flat/all func [a][print a]
                        mold/flat func [a "doc" /b][print a]
                    ]""")).isEqualTo("""
                    ["make function! [[a][print a]]" \
                    "#(function! [[a][print a]])" \
                    {make function! [[a "doc" /b][print a]]}]""");
        }

        @Test
        @DisplayName("and a closure says closure, because the reader has two words")
        void aClosureSaysClosure() {
            assertThat(answerTo("""
                    mold/flat closure [a][print a]"""))
                    .isEqualTo("\"make closure! [[a /local][print a]]\"");
        }

        @Test
        @DisplayName("FORM of a function is the same, there being nothing shorter")
        void formOfAFunctionIsTheSame() {
            assertThat(answerTo("""
                    f: func [a][print a]
                    (form :f) = mold :f""")).isEqualTo("#(true)");
        }
    }

    @Nested
    @DisplayName("an error, which molds as the object it is")
    class Errors {

        @Test
        @DisplayName("MOLD writes all eight fields, not a one-line summary")
        void moldWritesAllEightFields() {
            assertThat(answerTo("""
                    mold/flat make error! [type: 'Math id: 'overflow]"""))
                    .isEqualTo("{make error! [code: 401 type: 'Math id: 'overflow"
                            + " arg1: _ arg2: _ arg3: _ near: _ where: _]}");
        }

        @Test
        @DisplayName("under /ALL the words lose their quotes, as in any object")
        void underAllTheWordsLoseTheirQuotes() {
            assertThat(answerTo("""
                    mold/all/flat make error! [type: 'Math id: 'overflow]"""))
                    .isEqualTo("{#(error! [code: 401 type: Math id: overflow"
                            + " arg1: _ arg2: _ arg3: _ near: _ where: _])}");
        }

        @Test
        @DisplayName("Note and Command are categories too, at a hundred and six hundred")
        void noteAndCommandAreCategories() {
            assertThat(answerTo("""
                    exited: make error! [type: 'Note id: 'exited]
                    failed: make error! [type: 'Command id: 'command-fail]
                    unread: make error! [type: 'Note id: 'no-load]
                    reduce [exited/code failed/code unread/code]"""))
                    .isEqualTo("[101 600 100]");
        }
    }

    @Nested
    @DisplayName("an empty path, which is nothing until /ALL asks")
    class EmptyPaths {

        @Test
        @DisplayName("plainly it molds as no characters at all")
        void plainlyItMoldsAsNothing() {
            assertThat(answerTo("""
                    mold make path! []""")).isEqualTo("\"\"");
        }

        @Test
        @DisplayName("under /ALL each of the four names itself")
        void underAllEachNamesItself() {
            assertThat(answerTo("""
                    reduce [
                        mold/all make path! []
                        mold/all make set-path! []
                        mold/all make get-path! []
                        mold/all make lit-path! []
                    ]""")).isEqualTo("""
                    ["#(path! [])" "#(set-path! [])" \
                    "#(get-path! [])" "#(lit-path! [])"]""");
        }

        @Test
        @DisplayName("and that is the writing LOAD reads back")
        void thatIsTheWritingLoadReadsBack() {
            assertThat(answerTo("""
                    equal? make path! [] load mold/all make path! []"""))
                    .isEqualTo("#(true)");
        }

        @Test
        @DisplayName("a path emptied after being walked to its tail molds the same")
        void aPathEmptiedAfterBeingWalked() {
            assertThat(answerTo("""
                    a: 'a/b/c
                    b: tail a
                    clear a
                    mold/all b""")).isEqualTo("\"#(path! [])\"");
        }
    }

    @Nested
    @DisplayName("a decimal, which /ALL writes every digit of")
    class Decimals {

        @Test
        @DisplayName("seventeen digits under /ALL and fifteen without")
        void seventeenDigitsUnderAll() {
            assertThat(answerTo("""
                    reduce [mold/all 0.1 mold 0.1 mold/all 0.3 mold 0.3]"""))
                    .isEqualTo("""
                    ["0.10000000000000001" "0.1" "0.29999999999999999" "0.3"]""");
        }

        @Test
        @DisplayName("but only the digits it has, so a round number stays round")
        void onlyTheDigitsItHas() {
            assertThat(answerTo("""
                    reduce [mold/all 1.0 mold/all -2.5 mold/all 3.14159265358979]"""))
                    .isEqualTo("[\"1.0\" \"-2.5\" \"3.14159265358979\"]");
        }

        @Test
        @DisplayName("an infinite percent drops its sign, having no digits to be one of")
        void anInfinitePercentDropsItsSign() {
            assertThat(answerTo("""
                    reduce [
                        mold to percent! 1.#INF
                        mold to percent! -1.#INF
                        mold to percent! 1.#NaN
                        mold to percent! -1.#NaN
                    ]""")).isEqualTo("""
                    ["1.#INF" "-1.#INF" "1.#NaN" "1.#NaN"]""");
        }

        @Test
        @DisplayName("while a percent made of digits keeps it")
        void aPercentOfDigitsKeepsIt() {
            assertThat(answerTo("""
                    reduce [mold 50% mold -25% mold 0%]"""))
                    .isEqualTo("[\"50%\" \"-25%\" \"0%\"]");
        }
    }

    @Nested
    @DisplayName("an image, whose position goes after its pixels")
    class Images {

        @Test
        @DisplayName("the construct writes the whole picture and then where it stands")
        void theConstructWritesTheWholePicture() {
            assertThat(answerTo("""
                    mold/all/flat next make image! 8x1""")).isEqualTo("""
                    {#(image! 8x1 #{FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF} 2)}""");
        }

        @Test
        @DisplayName("and leaves the position out when there is nothing to say")
        void andLeavesThePositionOut() {
            assertThat(answerTo("""
                    mold/all/flat make image! 2x1""")).isEqualTo("""
                    "#(image! 2x1 #{FFFFFFFFFFFF})\"""");
        }

        @Test
        @DisplayName("MOLD/PART stops before encoding the pixels it will throw away")
        void moldPartStopsEarly() {
            assertThat(answerTo("""
                    mold/part to binary! make image! 1920x1080 8"""))
                    .isEqualTo("\"#{FFFFFF\"");
        }
    }

    @Nested
    @DisplayName("a ref, which is an at-sign only when the lexer would read it back")
    class Refs {

        @Test
        @DisplayName("letters and digits and the punctuation a word may hold")
        void lettersAndDigitsAndWordPunctuation() {
            assertThat(answerTo("""
                    reduce [
                        mold @name
                        mold to ref! "a.b"
                        mold to ref! "a-b"
                        mold to ref! "šiška"
                        mold to ref! "a<b"
                    ]""")).isEqualTo("""
                    ["@name" "@a.b" "@a-b" "@šiška" "@a<b"]""");
        }

        @Test
        @DisplayName("a second at-sign makes it an email, so the construct is written")
        void aSecondAtSignForcesTheConstruct() {
            assertThat(answerTo("""
                    reduce [mold to ref! "a@" mold to ref! "a@b"]"""))
                    .isEqualTo("""
                    [{#(ref! "a@")} {#(ref! "a@b")}]""");
        }

        @Test
        @DisplayName("and so does a space, a control character or a delimiter")
        void spacesAndDelimitersForceIt() {
            assertThat(answerTo("""
                    reduce [
                        mold to ref! "a b"
                        mold append @a "^/b"
                        mold to ref! "a/b"
                        mold to ref! "a;b"
                        mold to ref! "a(b"
                    ]""")).isEqualTo("""
                    [{#(ref! "a b")} {#(ref! "a^^/b")} {#(ref! "a/b")} \
                    {#(ref! "a;b")} {#(ref! "a(b")}]""");
        }

        @Test
        @DisplayName("a colon and a comma are neither, so they stay bare")
        void aColonAndACommaStayBare() {
            assertThat(answerTo("""
                    reduce [mold to ref! "a:b" mold to ref! "a,b"]"""))
                    .isEqualTo("[\"@a:b\" \"@a,b\"]");
        }
    }
}
