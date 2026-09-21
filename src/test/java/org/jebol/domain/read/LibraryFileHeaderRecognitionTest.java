package org.jebol.domain.read;

import org.jebol.domain.value.BlockValue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LibraryFileHeaderRecognitionTest {

    private static LibraryFile fileFrom(String source) {
        return LibraryFile.readFrom(
                Transcoder.transcode(source).values().orElseThrow());
    }

    private static String moldedBodyOf(String source) {
        BlockValue body = fileFrom(source).body();
        return body.remaining().toString();
    }

    @Test
    @DisplayName("The word REBOL followed by a block is a header, and the body starts after it")
    void aHeaderIsTheWordRebolThenABlock() {
        LibraryFile file = fileFrom("""
                REBOL [Title: "x"] a: 1""");
        assertThat(file.body().remaining()).hasSize(2);
        assertThat(moldedBodyOf("""
                REBOL [Title: "x"] a: 1""")).contains("a:");
    }

    @Test
    @DisplayName("An empty file has no header and an empty body")
    void anEmptyFileHasNoHeader() {
        LibraryFile file = fileFrom("");
        assertThat(file.body().remaining()).isEmpty();
        assertThat(file.header()).isEqualTo(LibraryFileHeader.none());
    }

    @Test
    @DisplayName("A file of one value has no header and keeps that value")
    void aSingleValueFileHasNoHeader() {
        LibraryFile file = fileFrom("a");
        assertThat(file.body().remaining()).hasSize(1);
        assertThat(file.header()).isEqualTo(LibraryFileHeader.none());
    }

    @Test
    @DisplayName("Two values that are not a header are both body")
    void twoValuesThatAreNotAHeaderAreBothBody() {
        LibraryFile file = fileFrom("a: 1");
        assertThat(file.body().remaining()).hasSize(2);
        assertThat(file.header()).isEqualTo(LibraryFileHeader.none());
    }

    @Test
    @DisplayName("Three values that are not a header are all body")
    void threeValuesThatAreNotAHeaderAreAllBody() {
        LibraryFile file = fileFrom("a: 1 b");
        assertThat(file.body().remaining()).hasSize(3);
    }

    @Test
    @DisplayName("The word REBOL without a block after it is not a header")
    void theWordRebolWithoutABlockIsNotAHeader() {
        LibraryFile file = fileFrom("REBOL 1");
        assertThat(file.body().remaining()).hasSize(2);
        assertThat(file.header()).isEqualTo(LibraryFileHeader.none());
    }

    @Test
    @DisplayName("The word REBOL followed by a string is not a header")
    void theWordRebolFollowedByAStringIsNotAHeader() {
        LibraryFile file = fileFrom("""
                REBOL "not a block\"""");
        assertThat(file.body().remaining()).hasSize(2);
    }

    @Test
    @DisplayName("A block without the word REBOL before it is not a header")
    void aBlockWithoutRebolBeforeItIsNotAHeader() {
        LibraryFile file = fileFrom("other [Title: 1] a");
        assertThat(file.body().remaining()).hasSize(3);
        assertThat(file.header()).isEqualTo(LibraryFileHeader.none());
    }

    @Test
    @DisplayName("The word REBOL is recognised whatever its case")
    void theWordRebolIsRecognisedWhateverItsCase() {
        assertThat(fileFrom("rebol [] a").body().remaining()).hasSize(1);
        assertThat(fileFrom("Rebol [] a").body().remaining()).hasSize(1);
        assertThat(fileFrom("REBOL [] a").body().remaining()).hasSize(1);
    }

    @Test
    @DisplayName("An empty header block is still a header")
    void anEmptyHeaderBlockIsStillAHeader() {
        LibraryFile file = fileFrom("REBOL [] a b");
        assertThat(file.body().remaining()).hasSize(2);
    }

    @Test
    @DisplayName("The header's Type and Exports fields are read through the same path")
    void theHeaderFieldsAreStillRead() {
        LibraryFile file = fileFrom("""
                REBOL [Type: module Name: thing Exports: [one two]] a: 1""");
        assertThat(file.header().declaresAModule()).isTrue();
        assertThat(file.header().moduleName()).isEqualTo("thing");
        assertThat(file.header().exportedNames()).containsExactly("one", "two");
    }
}
