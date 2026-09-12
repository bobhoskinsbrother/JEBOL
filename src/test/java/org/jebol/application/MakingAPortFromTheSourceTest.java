package org.jebol.application;

import org.jebol.domain.host.HostService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MakingAPortFromTheSourceTest {

    private static String answerTo(String source) {
        return answerFrom(Interpreter.create(), source);
    }

    private static String answerReachingTheFilesystem(
            java.nio.file.Path directory, String source) {

        Interpreter interpreter = Interpreter.withBounds(
                Bounds.standard().granting(HostService.FILES));
        interpreter.useFileSystem(FileSystemPort.rootedAt(directory));
        return answerFrom(interpreter, source);
    }

    private static String answerFrom(Interpreter interpreter, String source) {
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String errorIdFrom(String source) {
        return answerTo("e: try [" + source + "] either error? e [e/id] ['no-error]");
    }

    private static String errorArgumentFrom(String source) {
        return answerTo("e: try [" + source + "] either error? e [e/arg1] ['no-error]");
    }

    @Test
    @DisplayName("a block names its scheme in a set-word")
    void aBlockNamesItsScheme() {
        assertThat(answerTo("""
                p: make port! [scheme: 'checksum method: 'sha256]
                reduce [type? p p/spec/scheme p/spec/method]"""))
                .isEqualTo("[#(port!) checksum sha256]");
    }

    @Test
    @DisplayName("and a port made from a block opens and works")
    void aPortMadeFromABlockWorks() {
        assertThat(answerTo("""
                p: open make port! [scheme: 'checksum method: 'sha256]
                write p #{00}
                enbase/flat read p 16"""))
                .isEqualTo("""
                        {6E340B9CFFB37A989CA544E6BB780A2C78901D3FB33738768511A30617AFA01D}""");
    }

    @Test
    @DisplayName("a url names its scheme, and the rest of the url is spec too")
    void aUrlNamesItsScheme() {
        assertThat(answerTo("""
                p: make port! crypt://AES-128-CBC#decrypt
                reduce [p/spec/scheme p/spec/algorithm p/spec/direction]"""))
                .isEqualTo("[crypt AES-128-CBC decrypt]");
    }

    @Test
    @DisplayName("a word names its scheme and nothing else")
    void aWordNamesItsSchemeAndNothingElse() {
        assertThat(answerTo("""
                p: make port! 'checksum
                p/spec/scheme""")).isEqualTo("checksum");
    }

    @Test
    @DisplayName("an object names its scheme in a field")
    void anObjectNamesItsScheme() {
        assertThat(answerTo("""
                p: make port! make object! [scheme: 'checksum]
                p/spec/scheme""")).isEqualTo("checksum");
    }

    @Test
    @DisplayName("a file is the file scheme, and a wildcard is the directory scheme")
    void aFileIsTheFileSchemeAndAWildcardIsTheDirectoryScheme(
            @org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) {

        assertThat(answerReachingTheFilesystem(directory, """
                p: make port! %somefile.txt
                reduce [p/spec/scheme p/spec/ref p/spec/title]"""))
                .isEqualTo("""
                        [file %somefile.txt "File Access"]""");
        assertThat(answerReachingTheFilesystem(directory, """
                p: make port! %*.txt
                p/spec/scheme""")).isEqualTo("dir");
    }

    @Test
    @DisplayName("a specification whose scheme nobody serves names the scheme")
    void aSchemeNobodyServesNamesTheScheme() {
        assertThat(errorIdFrom("make port! [scheme: 'nonsense]"))
                .isEqualTo("no-scheme");
        assertThat(errorArgumentFrom("make port! [scheme: 'nonsense]"))
                .isEqualTo("nonsense");
        assertThat(errorIdFrom("make port! make object! [scheme: 'nonsense]"))
                .isEqualTo("no-scheme");
        assertThat(errorIdFrom("make port! 'nonsense")).isEqualTo("no-scheme");
        assertThat(errorArgumentFrom("make port! 'nonsense")).isEqualTo("nonsense");
        assertThat(errorIdFrom("make port! nonsense://x")).isEqualTo("no-scheme");
    }

    @Test
    @DisplayName("and a specification naming no scheme at all is the same refusal")
    void aSpecificationWithNoSchemeAtAllIsTheSameRefusal() {
        assertThat(errorIdFrom("make port! [algorithm: 'AES-128-CBC]"))
                .isEqualTo("no-scheme");
        assertThat(errorArgumentFrom("make port! [algorithm: 'AES-128-CBC]"))
                .isEqualTo("_");
        assertThat(errorIdFrom("make port! []")).isEqualTo("no-scheme");
        assertThat(errorIdFrom("make port! make object! [other: 1]"))
                .isEqualTo("no-scheme");
    }

    @Test
    @DisplayName("what is no specification at all is refused as a specification")
    void whatIsNoSpecificationAtAllIsRefusedAsOne() {
        assertThat(errorIdFrom("make port! 5")).isEqualTo("invalid-spec");
        assertThat(errorArgumentFrom("make port! 5")).isEqualTo("5");
        assertThat(errorIdFrom("make port! 1.5")).isEqualTo("invalid-spec");
        assertThat(errorArgumentFrom("make port! 1.5")).isEqualTo("1.5");
        assertThat(errorIdFrom("""
                make port! {x}""")).isEqualTo("invalid-spec");
        assertThat(errorIdFrom("make port! none")).isEqualTo("invalid-spec");
        assertThat(errorArgumentFrom("make port! none")).isEqualTo("_");
    }
}
