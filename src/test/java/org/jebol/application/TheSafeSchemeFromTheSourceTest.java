package org.jebol.application;

import org.jebol.adapter.host.ProcessEnvironment;
import org.jebol.domain.host.HostService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A user's encrypted key-value storage, which is what REBOL's SAFE codec and
 * scheme are between them.
 *
 * <p>{@code codec-safe.reb}, and every line of it is REBOL. The codec writes a
 * header, a checksum and the data through a cipher port; the scheme wraps a
 * file of that shape as something a script can PUT and SELECT. Nothing here is
 * Java, which is why this is the test that SAFE works rather than a test of
 * anything JEBOL wrote: what was missing was the ability to open a port on a
 * scheme written in REBOL at all.
 *
 * <p>{@code set-user} and {@code su} in {@code mezz-shell.reb} are the words a
 * script uses. They reach the scheme through
 * {@code open [scheme: 'safe pass: ... path: ... target: ...]}, so a build that
 * cannot open one has no logged-in user either.
 *
 * <p>The password would otherwise be asked for at the console --
 * {@code ask/hide "Enter password: "} -- so every test here passes one, the
 * same way REBOL's own suite avoids the prompt with an environment variable.
 *
 * <p>Every expectation was read off a real 3.22.5 first.
 */
class TheSafeSchemeFromTheSourceTest {

    private static Interpreter reaching(Path directory) {
        Interpreter interpreter = Interpreter.withBounds(
                Bounds.standard()
                        .granting(HostService.FILES)
                        .granting(HostService.ENVIRONMENT)
                        .granting(HostService.CLOCK));
        interpreter.useFileSystem(FileSystemPort.rootedAt(directory));
        interpreter.useEnvironment(new ProcessEnvironment());
        return interpreter;
    }

    private static String answerTo(Path directory, String source) {
        Interpreter interpreter = reaching(directory);
        String withAPassword = """
                set-env "REBOL_SAFE_PASS" "my-pass"
                system/options/home: %./
                """ + source;
        interpreter.defineFreshWordsIn(withAPassword);
        return interpreter.display(interpreter.run(withAPassword));
    }

    /**
     * The codec on its own, which worked all along: a value molded, compressed,
     * checksummed and enciphered, and the whole trip back again.
     */
    @Nested
    @DisplayName("the codec, which is what SAVE and LOAD of a .safe file use")
    class TheCodec {

        @Test
        @DisplayName("every kind of value survives the round trip")
        void everyKindOfValueSurvivesTheRoundTrip(@TempDir Path directory) {
            assertThat(answerTo(directory, """
                    collect [
                        foreach data [#[key: "Hello"] 43 #{DEADBEEF} [key: "aaa" value: 12]][
                            keep equal? data load save %temp.safe data
                            try [delete %temp.safe]
                        ]
                    ]""")).isEqualTo("[#(true) #(true) #(true) #(true)]");
        }

        @Test
        @DisplayName("and the bytes it writes start with the name of the format")
        void theBytesItWritesStartWithTheNameOfTheFormat() {
            assertThat(answerTo(Path.of("."), """
                    written: system/codecs/safe/encode #{DEADBEEF}
                    to string! copy/part written 8"""))
                    .isEqualTo("\"SAFE 1.0\"");
        }

        @Test
        @DisplayName("a wrong password reads nothing back rather than rubbish")
        void aWrongPasswordReadsNothingBack(@TempDir Path directory) {
            assertThat(answerTo(directory, """
                    written: system/codecs/safe/encode/key #{DEADBEEF} "right"
                    none? system/codecs/safe/decode/key written "wrong\""""))
                    .isEqualTo("#(true)");
        }
    }

    /**
     * The scheme, which is the half that could not run. A port on it is a map
     * that lives in a file: PUT stores, SELECT reads back, and closing it
     * writes the file out.
     */
    @Nested
    @DisplayName("the scheme, which wraps such a file as a store")
    class TheScheme {

        @Test
        @DisplayName("opening one makes the file and answers a port")
        void openingOneMakesTheFileAndAnswersAPort(@TempDir Path directory) {
            assertThat(answerTo(directory, """
                    store: open [scheme: 'safe pass: "pw" path: %./ target: %store.safe]
                    reduce [port? store  open? store  true? exists? %store.safe]"""))
                    .isEqualTo("[#(true) #(true) #(true)]");
        }

        @Test
        @DisplayName("what is put in comes back out")
        void whatIsPutInComesBackOut(@TempDir Path directory) {
            assertThat(answerTo(directory, """
                    store: open [scheme: 'safe pass: "pw" path: %./ target: %store.safe]
                    put store 'greeting "hello"
                    reduce [select store 'greeting  pick store 'greeting]"""))
                    .isEqualTo("[\"hello\" \"hello\"]");
        }

        @Test
        @DisplayName("and it is still there after the port is closed and opened again")
        void itIsStillThereAfterClosingAndOpeningAgain(@TempDir Path directory) {
            assertThat(answerTo(directory, """
                    store: open [scheme: 'safe pass: "pw" path: %./ target: %store.safe]
                    put store 'greeting "hello"
                    close store
                    again: open [scheme: 'safe pass: "pw" path: %./ target: %store.safe]
                    select again 'greeting""")).isEqualTo("\"hello\"");
        }

        @Test
        @DisplayName("a key that was removed is gone")
        void aKeyThatWasRemovedIsGone(@TempDir Path directory) {
            assertThat(answerTo(directory, """
                    store: open [scheme: 'safe pass: "pw" path: %./ target: %store.safe]
                    put store 'greeting "hello"
                    remove/key store 'greeting
                    none? select store 'greeting""")).isEqualTo("#(true)");
        }
    }

    /**
     * SET-USER is the word a script uses, and the scheme is how it keeps what
     * it is given. /N makes the storage file when it is not there yet, and
     * without it a user nobody has set up is reported and not created.
     */
    @Nested
    @DisplayName("SET-USER, which is the whole of it from a script's side")
    class TheUser {

        @Test
        @DisplayName("a user who does not exist is not an error")
        void aUserWhoDoesNotExistIsNotAnError(@TempDir Path directory) {
            assertThat(answerTo(directory, """
                    reduce [
                        not error? try [set-user not-existing-user]
                        none? system/user/name
                    ]""")).isEqualTo("[#(true) #(true)]");
        }

        @Test
        @DisplayName("and /N sets one up, with a storage file to show for it")
        void andSlashNSetsOneUp(@TempDir Path directory) {
            assertThat(answerTo(directory, """
                    set-user/n/p temp-user "passw"
                    reduce [
                        system/user/name = @temp-user
                        'file = exists? system/user/data/spec/ref
                    ]""")).isEqualTo("[#(true) #(true)]");
        }

        @Test
        @DisplayName("data stored under a user is read back by USER'S")
        void dataStoredUnderAUserIsReadBack(@TempDir Path directory) {
            assertThat(answerTo(directory, """
                    set-user/n/p temp-user "passw"
                    put system/user/data 'key "hello"
                    user's key""")).isEqualTo("\"hello\"");
        }

        @Test
        @DisplayName("SU releases the user, and logging back in finds the data")
        void suReleasesTheUserAndLoggingBackInFindsTheData(@TempDir Path directory) {
            assertThat(answerTo(directory, """
                    set-user/n/p temp-user "passw"
                    put system/user/data 'key "hello"
                    do [su]
                    released: none? system/user/name
                    set-user/p temp-user "passw"
                    reduce [released  user's key]""")).isEqualTo("[#(true) \"hello\"]");
        }

        @Test
        @DisplayName("and SU of nothing releases it too")
        void suOfNothingReleasesItToo(@TempDir Path directory) {
            assertThat(answerTo(directory, """
                    set-user/n/p temp-user "passw"
                    su #(none)
                    none? system/user/name""")).isEqualTo("#(true)");
        }
    }
}
