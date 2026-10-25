package org.jebol.domain.value;

import org.jebol.application.AnInterpreter;
import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MoldKeepsANestedSeriesPositionFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = AnInterpreter.create();
        interpreter.defineNewWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static final String WHICHEVER_DISAGREED = """
            disagreed: func [probes written][
                wrong: copy []
                foreach [name maker expected] probes [
                    unless expected = written do maker [append wrong name]
                ]
                wrong
            ]
            """;

    @Nested
    @DisplayName("under MOLD/ALL, which has to read back as the same position")
    class UnderMoldAll {

        @Test
        @DisplayName("every series datatype nested in a block keeps its position")
        void keepsThePositionOfEverySeriesInsideABlock() {
            assertThat(answerTo(WHICHEVER_DISAGREED + """
                    disagreed [
                        binary   [next #{0A0B0C}]        {[#(binary! #{0A0B0C} 2)]}
                        string   [next {abc}]            {[#(string! "abc" 2)]}
                        file     [next %abc]             {[#(file! "abc" 2)]}
                        email    [next a@bc]             {[#(email! "a@bc" 2)]}
                        ref      [next @abc]             {[#(ref! "abc" 2)]}
                        url      [next http://abc]       {[#(url! "http://abc" 2)]}
                        tag      [next <abc>]            {[#(tag! "abc" 2)]}
                        image    [next make image! 2x1]  {[#(image! 2x1 #{FFFFFFFFFFFF} 2)]}
                        vector   [next make vector! [1 2 3]] {[#(int64! [1 2 3] 2)]}
                        block    [next [1 2 3]]          {[#(block! [1 2 3] 2)]}
                        paren    [next first [(1 2 3)]]  {[#(paren! [1 2 3] 2)]}
                        path     [next first [a/b/c]]    {[#(path! [a b c] 2)]}
                        set-path [next first [a/b/c:]]   {[#(set-path! [a b c] 2)]}
                        get-path [next first [:a/b/c]]   {[#(get-path! [a b c] 2)]}
                        lit-path [next first ['a/b/c]]   {[#(lit-path! [a b c] 2)]}
                        hash     [next make hash! [1 2 3]] {[#(hash! [2 3] 2)]}
                    ] func [value][mold/flat/all reduce [value]]"""))
                    .isEqualTo("[]");
        }

        @Test
        @DisplayName("and keeps it inside a paren, an object, a map and a block in a block")
        void keepsThePositionInsideEveryContainer() {
            assertThat(answerTo(WHICHEVER_DISAGREED + """
                    held: make object! [held: none]
                    disagreed [
                        paren  [to paren! reduce [next {abc}]]
                               {(#(string! "abc" 2))}
                        object [set in held 'held next {abc} held]
                               {#(object! [held: #(string! "abc" 2)])}
                        map    [make map! reduce ['held next {abc}]]
                               {#(map! [held: #(string! "abc" 2)])}
                        twice  [reduce [reduce [next {abc}]]]
                               {[[#(string! "abc" 2)]]}
                    ] func [value][mold/flat/all value]"""))
                    .isEqualTo("[]");
        }

        @Test
        @DisplayName("a series at its head is written plainly, needing no position")
        void leavesASeriesAtItsHeadAlone() {
            assertThat(answerTo(WHICHEVER_DISAGREED + """
                    disagreed [
                        binary [#{0A0B}]                 {[#{0A0B}]}
                        string [{abc}]                   {["abc"]}
                        block  [[1 2 3]]                 {[[1 2 3]]}
                        empty  [copy []]                 {[[]]}
                    ] func [value][mold/flat/all reduce [value]]"""))
                    .isEqualTo("[]");
        }

        @Test
        @DisplayName("a series at its tail carries the index past its end")
        void carriesAnIndexPastTheEnd() {
            assertThat(answerTo(WHICHEVER_DISAGREED + """
                    disagreed [
                        block  [tail [1 2 3]]  {[#(block! [1 2 3] 4)]}
                        string [tail {abc}]    {[#(string! "abc" 4)]}
                    ] func [value][mold/flat/all reduce [value]]"""))
                    .isEqualTo("[]");
        }

        @Test
        @DisplayName("a hash is a construct even at its head, having no literal form")
        void writesAHashAsAConstructAtItsHead() {
            assertThat(answerTo("""
                    (mold/flat/all reduce [make hash! [1 2 3]]) = {[#(hash! [1 2 3])]}"""))
                    .isEqualTo("#(true)");
        }

        @Test
        @DisplayName("an empty tag is a construct too, nested as well as alone")
        void writesAnEmptyTagAsAConstruct() {
            assertThat(answerTo("""
                    reduce [
                        (mold/flat/all to tag! {}) = {#(tag! "")}
                        (mold/flat/all reduce [to tag! {}]) = {[#(tag! "")]}
                    ]""")).isEqualTo("[#(true) #(true)]");
        }

        @Test
        @DisplayName("a construct's own body writes what it holds as constructs")
        void writesAConstructBodyAsConstructsToo() {
            assertThat(answerTo("""
                    inner: next {abc}
                    outer: reduce [inner 1 2]
                    reduce [
                        (mold/flat/all outer) = {[#(string! "abc" 2) 1 2]}
                        (mold/flat/all next outer) = {#(block! [#(string! "abc" 2) 1 2] 2)}
                    ]""")).isEqualTo("[#(true) #(true)]");
        }
    }

    @Nested
    @DisplayName("under plain MOLD, which writes what it can read back")
    class UnderPlainMold {

        @Test
        @DisplayName("a nested series is written from where it stands, with no construct")
        void writesANestedSeriesFromWhereItStands() {
            assertThat(answerTo(WHICHEVER_DISAGREED + """
                    disagreed [
                        binary [next #{0A0B0C}]  {[#{0B0C}]}
                        string [next {abc}]      {["bc"]}
                        file   [next %abc]        {[%bc]}
                        block  [next [1 2 3]]    {[[2 3]]}
                        paren  [next first [(1 2 3)]] {[(2 3)]}
                        path   [next first [a/b/c]]   {[b/c]}
                        tail   [tail [1 2 3]]    {[[]]}
                    ] func [value][mold/flat reduce [value]]"""))
                    .isEqualTo("[]");
        }
    }
}
