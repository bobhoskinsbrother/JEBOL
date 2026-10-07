package org.jebol.domain.read;

import org.jebol.application.Interpreter;
import org.jebol.domain.value.StringValue;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

class MalformedSourceAsRebolReportsItFromTheSourceTest {

    private String whatLoadingAnswers(String source) {
        String written = HexFormat.of().formatHex(source.getBytes(StandardCharsets.UTF_8));
        String asked = """
                e: try [load to string! #{%s}]
                either error? :e [mold/flat reduce [e/id e/arg1 e/arg2]][mold/flat/all reduce [type? :e :e]]
                """.formatted(written);
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(asked);
        return ((StringValue) interpreter.run(asked).value()).text();
    }

    @ParameterizedTest(name = "{0} loads as {1}")
    @CsvSource(delimiter = '|', quoteCharacter = '`', textBlock = """
            `"abc` | `[invalid "string" {"abc}]`
            `{abc` | `[invalid "string" "{abc"]`
            `{a{b}` | `[invalid "string" "{a{b}"]`
            `%{abc` | `[invalid "string" "%"]`
            `%%{abc}%` | `[invalid "string" "%%"]`
            `#{0}` | `[#(binary!) #{00}]`
            `#{GG}` | `[invalid "binary" "#{GG}"]`
            `#{00` | `[invalid "binary" "#{00"]`
            `2#{0101` | `[invalid "binary" "2#{0101"]`
            `64#{AA=}` | `[invalid "binary" "64#{AA=}"]`
            `#(` | `[missing "end-of-script" ")"]`
            `#(foo` | `[missing "end-of-script" ")"]`
            `#(none` | `[missing "end-of-script" ")"]`
            `#[foo` | `[missing "end-of-script" "]"]`
            `#(foo! 1)` | `[malconstruct [foo! 1] _]`
            `1d` | `[invalid "integer" "1d"]`
            `1.2.3.4.5.6.7.8.9.10.11.12.13` | `[invalid "tuple" "1.2.3.4.5.6.7.8.9.10.11.12.13"]`
            `99999999999999999999` | `[invalid "integer" "99999999999999999999"]`
            `1e` | `[#(decimal!) 1.0]`
            `$` | `[invalid "money" "$"]`
            `$1x` | `[invalid "money" "$1x"]`
            `1:2:3:4` | `[invalid "time" "1:2:3:4"]`
            `1-Jan-20x` | `[invalid "date" "1-Jan-20x"]`
            `32-Jan-2000` | `[invalid "date" "32-Jan-2000"]`
            `1x` | `[invalid "pair" "1x"]`
            `1x2x3` | `[invalid "pair" "1x2x3"]`
            `#` | `[invalid "issue" "#"]`
            `#@` | `[#(email!) #(email! "#@")]`
            `@` | `[#(ref!) #(ref! "")]`
            `<` | `[#(word!) <]`
            `<a` | `[invalid "tag" "<a"]`
            `%` | `[#(word!) %]`
            `%"abc` | `[invalid "file" "%"]`
            `a/` | `[invalid "path" "a/"]`
            `/a/` | `[#(block!) [/a /]]`
            `'` | `[invalid "word-lit" "'"]`
            `:` | `[invalid "word-get" ":"]`
            `::` | `[invalid "word" "::"]`
            `a:b:` | `[#(url!) a:b:]`
            `a@` | `[#(email!) #(email! "a@")]`
            `^` | `[#(word!) ^]`
            `#"ab"` | `[invalid "char" {#"ab"}]`
            `#"^(zzzz)"` | `[invalid "char" {#"^^(zzzz)"}]`
            `#"^(110000)"` | `[invalid "char" {#"^^(110000)"}]`
            `"^(110000)"` | `[invalid "string" {"^^(110000)"}]`
            `"^(zz)"` | `[invalid "string" {"^^(zz)"}]`
            `{^(zz)}` | `[invalid "string" "{^^(zz)}"]`
            `a.b/` | `[invalid "path" "a.b/"]`
            `1.#` | `[invalid "integer" "1.#"]`
            `1#` | `[invalid "integer" "1#"]`
            `#x/` | `[#(block!) [#x /]]`
            `|` | `[#(word!) |]`
            `1,2` | `[#(decimal!) 1.2]`
            `--1` | `[#(word!) --1]`
            `+-1` | `[#(word!) +-1]`
            `1%%` | `[invalid "percent" "1%%"]`
            `1e500` | `[overflow _ _]`
            `1.5e` | `[#(decimal!) 1.5]`
            `-$1-` | `[invalid "money" "-$1-"]`
            `12:` | `[invalid "time" "12:"]`
            `<tag` | `[invalid "tag" "<tag"]`
            `0x` | `[invalid "pair" "0x"]`
            `1.2x` | `[invalid "pair" "1.2x"]`
            `1x2.3x` | `[invalid "pair" "1x2.3x"]`
            `url://` | `[#(url!) url://]`
            `a::b` | `[#(url!) a::b]`
            `'a:` | `[invalid "word-lit" "'a:"]`
            `:a:` | `[invalid "word-get" ":a:"]`
            `\\x` | `[invalid "word" "\\x"]`
            `1:60` | `[#(time!) 2:00]`
            `%a{` | `[invalid "string" "{"]`
            `#()` | `[malconstruct [] _]`
            `#[]` | `[#(map!) #(map! [])]`
            `#{}#` | `[invalid "issue" "#"]`
            `a[` | `[missing "end-of-script" "]"]`
            `b)` | `[missing "end-of-paren" "("]`
            `]` | `[missing "end-of-block" "["]`
            `)` | `[missing "end-of-paren" "("]`
            `(` | `[missing "end-of-script" ")"]`
            `[` | `[missing "end-of-script" "]"]`
            `1 + ]` | `[missing "end-of-block" "["]`
            `x: 1 ]` | `[missing "end-of-block" "["]`
            `#"a` | `[invalid "char" {#"a}]`
            `#"` | `[invalid "char" {#"}]`
            `"ab"cd` | `[#(block!) ["ab" cd]]`
            `#{0102}x` | `[#(block!) [#{0102} x]]`
            """)
    void answersAsRebolDoes(String source, String wanted) {
        assertThat(whatLoadingAnswers(source)).isEqualTo(wanted);
    }
}
