package org.jebol.domain.value;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ABlockListsTheWordsTextInItTest {

    private static final boolean DEEPLY = true;
    private static final boolean ONLY_AT_THE_TOP = false;
    private static final boolean SETTERS_ONLY = true;
    private static final boolean EVERY_WORD = false;

    private static AnyBlockValue readABlock(Value... items) {
        return BlockValue.of(List.of(items));
    }

    private static List<String> listTheSpellingsOf(List<Value> words) {
        return words.stream().map(word -> ((AnyWordValue) word).spelling()).toList();
    }

    private static AnyBlockValue readABlockWithANestedOne() {
        return readABlock(WordValue.of("a"), SetWordValue.of("b"),
                IntegerValue.of(1),
                readABlock(WordValue.of("c"), SetWordValue.of("d")));
    }

    @Test
    @DisplayName("an empty block holds no words")
    void listsNothingForAnEmptyBlock() {
        assertThat(readABlock().listWordsWritten(DEEPLY, EVERY_WORD)).isEmpty();
    }

    @Test
    @DisplayName("at the top only, a nested block is passed over")
    void listsOnlyAtTheTop() {
        assertThat(listTheSpellingsOf(readABlockWithANestedOne().listWordsWritten(ONLY_AT_THE_TOP, EVERY_WORD)))
                .containsExactly("a", "b");
    }

    @Test
    @DisplayName("deeply, the nested block's words follow in the order they are written")
    void deeply() {
        assertThat(listTheSpellingsOf(readABlockWithANestedOne().listWordsWritten(DEEPLY, EVERY_WORD)))
                .containsExactly("a", "b", "c", "d");
    }

    @Test
    @DisplayName("setters only keeps the set-words and nothing else")
    void settersOnly() {
        assertThat(listTheSpellingsOf(readABlockWithANestedOne().listWordsWritten(DEEPLY, SETTERS_ONLY)))
                .containsExactly("b", "d");
    }

    @Test
    @DisplayName("each word comes back as a plain word, unbound, whatever type it was written as")
    void listsEachPlain() {
        Context somewhere = Context.root();
        somewhere.register("a", IntegerValue.of(1));
        AnyBlockValue written = readABlock(SetWordValue.of("a").bindTo(somewhere),
                GetWordValue.of("b"), LitWordValue.of("c"));

        assertThat(written.listWordsWritten(DEEPLY, EVERY_WORD)).allSatisfy(word -> {
            assertThat(word.datatype()).isEqualTo(WordValue.TYPE);
            assertThat(((AnyWordValue) word).isBound()).isFalse();
        });
    }

    @Test
    @DisplayName("a word written twice, in any case, comes back once, as first spelled")
    void listsAWordWrittenTwice() {
        AnyBlockValue written = readABlock(WordValue.of("Total"), SetWordValue.of("total"),
                readABlock(WordValue.of("TOTAL")));

        assertThat(listTheSpellingsOf(written.listWordsWritten(DEEPLY, EVERY_WORD))).containsExactly("Total");
    }

    @Test
    @DisplayName("values that are not words are not listed")
    void skipsValuesThatAreNotWords() {
        AnyBlockValue written = readABlock(IntegerValue.of(1), StringValue.of("a"), NoneValue.none());

        assertThat(written.listWordsWritten(DEEPLY, EVERY_WORD)).isEmpty();
    }
}
