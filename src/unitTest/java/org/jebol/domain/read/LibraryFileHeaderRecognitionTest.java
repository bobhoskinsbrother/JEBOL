package org.jebol.domain.read;

import org.jebol.domain.value.AnyBlockValue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LibraryFileHeaderRecognitionTest {

    private static LibraryFile readAFileFrom(String source) {
        return LibraryFile.readFrom(
                Transcoder.transcode(source).values().orElseThrow());
    }

    private static String moldTheBodyOf(String source) {
        AnyBlockValue body = readAFileFrom(source).body();
        return body.remaining().toString();
    }

    @Test
    @DisplayName("The word REBOL followed by a block is a header, and the body starts after it")
    void recognisesTheWordRebolThenABlock() {
        LibraryFile file = readAFileFrom("""
                REBOL [Title: "x"] a: 1""");
        assertThat(file.body().remaining()).hasSize(2);
        assertThat(moldTheBodyOf("""
                REBOL [Title: "x"] a: 1""")).contains("a:");
    }

    @Test
    @DisplayName("An empty file has no header and an empty body")
    void findsNoHeaderInAnEmptyFile() {
        LibraryFile file = readAFileFrom("");
        assertThat(file.body().remaining()).isEmpty();
        assertThat(file.header()).isEqualTo(LibraryFileHeader.none());
    }

    @Test
    @DisplayName("A file of one value has no header and keeps that value")
    void findsNoHeaderInASingleValue() {
        LibraryFile file = readAFileFrom("a");
        assertThat(file.body().remaining()).hasSize(1);
        assertThat(file.header()).isEqualTo(LibraryFileHeader.none());
    }

    @Test
    @DisplayName("Two values that are not a header are both body")
    void treatsTwoValuesThatAreNotAHeaderAsBody() {
        LibraryFile file = readAFileFrom("a: 1");
        assertThat(file.body().remaining()).hasSize(2);
        assertThat(file.header()).isEqualTo(LibraryFileHeader.none());
    }

    @Test
    @DisplayName("Three values that are not a header are all body")
    void treatsThreeValuesThatAreNotAHeaderAsBody() {
        LibraryFile file = readAFileFrom("a: 1 b");
        assertThat(file.body().remaining()).hasSize(3);
    }

    @Test
    @DisplayName("The word REBOL without a block after it is not a header")
    void rejectsTheWordRebolWithoutABlock() {
        LibraryFile file = readAFileFrom("REBOL 1");
        assertThat(file.body().remaining()).hasSize(2);
        assertThat(file.header()).isEqualTo(LibraryFileHeader.none());
    }

    @Test
    @DisplayName("The word REBOL followed by a string is not a header")
    void rejectsTheWordRebolFollowedByAString() {
        LibraryFile file = readAFileFrom("""
                REBOL "not a block\"""");
        assertThat(file.body().remaining()).hasSize(2);
    }

    @Test
    @DisplayName("A block without the word REBOL before it is not a header")
    void rejectsABlockWithoutRebolBeforeIt() {
        LibraryFile file = readAFileFrom("other [Title: 1] a");
        assertThat(file.body().remaining()).hasSize(3);
        assertThat(file.header()).isEqualTo(LibraryFileHeader.none());
    }

    @Test
    @DisplayName("The word REBOL is recognised whatever its case")
    void recognisesTheWordRebolWhateverItsCase() {
        assertThat(readAFileFrom("rebol [] a").body().remaining()).hasSize(1);
        assertThat(readAFileFrom("Rebol [] a").body().remaining()).hasSize(1);
        assertThat(readAFileFrom("REBOL [] a").body().remaining()).hasSize(1);
    }

    @Test
    @DisplayName("An empty header block is still a header")
    void acceptsAnEmptyHeaderBlock() {
        LibraryFile file = readAFileFrom("REBOL [] a b");
        assertThat(file.body().remaining()).hasSize(2);
    }

    @Test
    @DisplayName("The header's Type and Exports fields are read through the same path")
    void readsTheHeaderFields() {
        LibraryFile file = readAFileFrom("""
                REBOL [Type: module Name: thing Exports: [one two]] a: 1""");
        assertThat(file.header().declaresModule()).isTrue();
        assertThat(file.header().moduleName()).isEqualTo("thing");
        assertThat(file.header().exportedNames()).containsExactly("one", "two");
    }
}
