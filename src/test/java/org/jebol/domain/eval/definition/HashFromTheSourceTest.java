package org.jebol.domain.eval.definition;

import org.jebol.application.Interpreter;
import org.jebol.domain.value.Molder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class HashFromTheSourceTest {

    private String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return Molder.moldFlat(interpreter.run(source).value());
    }

    @Nested
    @DisplayName("Hash_Value hashes a value by its kind, as n-hash.c does")
    class EachKind {

        @ParameterizedTest(name = "hash {0} is {1}")
        @CsvSource(delimiter = '|', quoteCharacter = '`', textBlock = """
                1                               | 1364076727
                0                               | 0
                -1                              | 0
                2147483648                      | 1832674720
                -9223372036854775808            | 1832674720
                1.0                             | 2524143827
                0.0                             | 0
                -0.0                            | 1832674720
                1.5                             | 2321223580
                50%                             | 1558924552
                $0                              | 0
                $1                              | 1
                $1.50                           | 4261413014
                -$1                             | 8388609
                $123.456                        | 4244759104
                $0.01                           | 4261412865
                `#"a"`                          | 187541
                `#"A"`                          | 187541
                `#"^@"`                         | 0
                `#"é"`                          | 450485
                `#"É"`                          | 450485
                `#"€"`                          | 16171073
                `#"^(10000)"`                   | 126708207
                1x2                             | 2139095040
                -1.5x2.25                       | 4291821568
                1.2.3                           | 579979952
                1.2.3.4                         | 3627674367
                255.255.255.255                 | 2180083513
                1.2.3.4.5                       | 3366886613
                1.2.3.4.5.6.7.8.9.10.11.12      | 2407593787
                1:00                            | 817409552
                -1:30                           | 1226111720
                1:02:03.5                       | 4058324363
                1-Jan-2020                      | 3722089084
                1-Jan-2020/10:00                | 3771915296
                1-Jan-2020/10:00+2:00           | 2183225352
                1-Jan-2020/10:00-5:30           | 4106844434
                31-Dec-1999/23:59:59.123        | 1523029055
                ""                              | 14
                "a"                             | 721651727
                "A"                             | 721651727
                "abcde"                         | 699351560
                "Hello World"                   | 406428417
                "é"                             | 1721706972
                "日本"                          | 3196646041
                next "abc"                      | 139932130
                %F                              | 3240139388
                a@b                             | 4196154795
                url://x                         | 4153418610
                <t>                             | 822711895
                @ref                            | 3435545272
                `#{}`                           | 0
                `#{00}`                         | 1364076727
                `#{01}`                         | 2492571497
                `#{0102}`                       | 1753975625
                `#{010203}`                     | 2992766861
                `#{01020304}`                   | 1043635621
                `#{0102030405}`                 | 3366886613
                `#{0102030405060708}`           | 223027131
                []                              | 233334602
                [1 2]                           | 1847544345
                [[1] 2]                         | 112768747
                next [1 2 3]                    | 1599147329
                integer!                        | 6520781
                end!                            | 3088191
                none                            | 821347078
                true                            | 3
                false                           | 2
                make typeset! [integer!]        | 2857019256
                make object! [a: 1]             | 3824075370
                ()                              | 1364076727
                """)
        void hashesAsRebolDoes(String value, String wanted) {
            assertThat(answerTo("hash " + value)).isEqualTo(wanted);
        }
    }

    @Nested
    @DisplayName("a word hashes as its place in the symbol table, which a session extends as it reads")
    class Words {

        @ParameterizedTest(name = "{0} is {1}")
        @CsvSource(delimiter = '|', quoteCharacter = '`', textBlock = """
                hash 'end                                                         | 189
                hash 'unset!                                                      | 2
                hash 'true                                                        | 103
                hash 'self                                                        | 101
                hash 'print                                                       | 780
                hash 'probe                                                       | 1518
                reduce [hash quote a: hash quote 'a hash 'a hash 'A hash /a hash #a] | [914 914 914 914 914 914]
                hash to word! {zz-brand-new}                                      | 3187
                reduce [hash 'qq-one hash 'qq-two hash 'QQ-ONE]                   | [3187 3188 3187]
                b: [qq-one [qq-two qq-three] QQ-ONE #[qq-four 1]] hash 'qq-four   | 3191
                w1: to word! {qq-x} w2: to word! {qq-y} reduce [hash w2 hash w1]  | [3190 3189]
                x: load {qq-five qq-six} hash 'qq-six                             | 3187
                y: transcode {qq-seven} hash 'qq-seven                            | 3187
                hash [a]                                                          | 2540274097
                hash quote (a b)                                                  | 2065161527
                hash 'a/b                                                         | 2174514921
                """)
        void numbersWordsAsRebolDoes(String source, String wanted) {
            assertThat(answerTo(source)).isEqualTo(wanted);
        }
    }
}
