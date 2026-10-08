package org.jebol.domain.value;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ABlockListsTheWordsWrittenInItTest {

    private static final boolean DEEPLY = true;
    private static final boolean ONLY_AT_THE_TOP = false;
    private static final boolean SETTERS_ONLY = true;
    private static final boolean EVERY_WORD = false;

    private static BlockValue block(Value... items) {
        return BlockValue.block(List.of(items));
    }

    private static List<String> spellingsOf(List<Value> words) {
        return words.stream().map(word -> ((AnyWordValue) word).spelling()).toList();
    }

    private static BlockValue aBlockWithANestedOne() {
        return block(WordValue.of("a"), SetWordValue.of("b"),
                IntegerValue.of(1),
                block(WordValue.of("c"), SetWordValue.of("d")));
    }

    @Test
    @DisplayName("an empty block holds no words")
    void anEmptyBlock() {
        assertThat(block().wordsWritten(DEEPLY, EVERY_WORD)).isEmpty();
    }

    @Test
    @DisplayName("at the top only, a nested block is passed over")
    void onlyAtTheTop() {
        assertThat(spellingsOf(aBlockWithANestedOne().wordsWritten(ONLY_AT_THE_TOP, EVERY_WORD)))
                .containsExactly("a", "b");
    }

    @Test
    @DisplayName("deeply, the nested block's words follow in the order they are written")
    void deeply() {
        assertThat(spellingsOf(aBlockWithANestedOne().wordsWritten(DEEPLY, EVERY_WORD)))
                .containsExactly("a", "b", "c", "d");
    }

    @Test
    @DisplayName("setters only keeps the set-words and nothing else")
    void settersOnly() {
        assertThat(spellingsOf(aBlockWithANestedOne().wordsWritten(DEEPLY, SETTERS_ONLY)))
                .containsExactly("b", "d");
    }

    @Test
    @DisplayName("each word comes back as a plain word, unbound, whatever kind it was written as")
    void eachComesBackPlain() {
        Context somewhere = Context.root();
        somewhere.register("a", IntegerValue.of(1));
        BlockValue written = block(SetWordValue.of("a").boundTo(somewhere),
                GetWordValue.of("b"), LitWordValue.of("c"));

        assertThat(written.wordsWritten(DEEPLY, EVERY_WORD)).allSatisfy(word -> {
            assertThat(word.datatype()).isEqualTo(Datatype.WORD);
            assertThat(((AnyWordValue) word).isBound()).isFalse();
        });
    }

    @Test
    @DisplayName("a word written twice, in any case, comes back once, as first spelled")
    void aWordWrittenTwice() {
        BlockValue written = block(WordValue.of("Total"), SetWordValue.of("total"),
                block(WordValue.of("TOTAL")));

        assertThat(spellingsOf(written.wordsWritten(DEEPLY, EVERY_WORD))).containsExactly("Total");
    }

    @Test
    @DisplayName("values that are not words are not listed")
    void valuesThatAreNotWords() {
        BlockValue written = block(IntegerValue.of(1), StringValue.of("a"), NoneValue.none());

        assertThat(written.wordsWritten(DEEPLY, EVERY_WORD)).isEmpty();
    }
}
