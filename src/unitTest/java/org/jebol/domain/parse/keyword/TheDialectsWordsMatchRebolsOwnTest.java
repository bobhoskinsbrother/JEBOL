package org.jebol.domain.parse.keyword;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TheDialectsWordsMatchRebolsOwnTest {

    private static final String THE_FIRST_WORD_THE_FILE_LISTS = "|";

    private static final String THE_LAST_WORD_THE_FILE_LISTS = "end";

    private static List<String> listTheParseWordsRebolLists() {
        List<String> listed = new ArrayList<>();
        boolean reached = false;
        for (String line : readTheVendoredWordsFile().split("\n")) {
            String word = stripItsComment(line).trim();
            if (word.isEmpty()) {
                continue;
            }
            reached = reached || word.equals(THE_FIRST_WORD_THE_FILE_LISTS);
            if (!reached) {
                continue;
            }
            listed.add(word);
            if (word.equals(THE_LAST_WORD_THE_FILE_LISTS)) {
                return listed;
            }
        }
        return listed;
    }

    private static String stripItsComment(String line) {
        int remark = line.indexOf(';');
        return remark < 0 ? line : line.substring(0, remark);
    }

    private static String readTheVendoredWordsFile() {
        try (InputStream open = TheDialectsWordsMatchRebolsOwnTest.class
                .getResourceAsStream("/rebol-boot/words.reb")) {
            return new String(open.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }

    @Test
    @DisplayName("the file's own list runs from the bar to END, thirty-four words")
    void matchTheFilesOwnList() {
        List<String> listed = listTheParseWordsRebolLists();
        assertThat(listed).hasSize(34);
        assertThat(listed.getFirst()).isEqualTo(THE_FIRST_WORD_THE_FILE_LISTS);
        assertThat(listed.getLast()).isEqualTo(THE_LAST_WORD_THE_FILE_LISTS);
        assertThat(listed).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("every keyword the table carries is one Rebol lists under parse")
    void inventNoKeyword() {
        assertThat(listTheParseWordsRebolLists())
                .containsAll(ParseKeyword.BY_SPELLING.keySet());
    }

    @Test
    @DisplayName("three of Rebol's words are not keywords here, and none of them is a gap")
    void includeTheThreeTheTableHasNotGot() {
        Set<String> missing = new LinkedHashSet<>(listTheParseWordsRebolLists());
        missing.removeAll(ParseKeyword.BY_SPELLING.keySet());
        assertThat(missing).containsExactly("|", "do", "only");
    }

    @Test
    @DisplayName("the bar marks an alternative rather than standing as a keyword")
    void leaveTheBarOut() {
        assertThat(ParseKeyword.findKeywordNamed("|")).isEmpty();
    }

    @Test
    @DisplayName("DO and ONLY are words Rebol reserves and its parse never acts on")
    void doAndOnlyAreReservedAndUnused() {
        assertThat(ParseKeyword.findKeywordNamed("do")).isEmpty();
        assertThat(ParseKeyword.findKeywordNamed("only")).isEmpty();
    }
}
