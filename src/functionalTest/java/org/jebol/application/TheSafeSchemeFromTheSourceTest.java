package org.jebol.application;

import org.jebol.adapter.host.FileSystemPort;
import org.jebol.adapter.host.ProcessEnvironment;
import org.jebol.domain.host.HostService;
import org.jebol.domain.host.OutputPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class TheSafeSchemeFromTheSourceTest {

    private static Interpreter makeAnInterpreterReaching(Path directory) {
        return Interpreter.createOn(
                AnInterpreter.standardPorts()
                        .withFiles(FileSystemPort.rootFileSystem(directory))
                        .withEnvironment(new ProcessEnvironment()),
                OutputPort.discard(),
                Bounds.standard()
                        .grant(HostService.FILES)
                        .grant(HostService.ENVIRONMENT)
                        .grant(HostService.CLOCK));
    }

    private static String answerTo(Path directory, String source) {
        Interpreter interpreter = makeAnInterpreterReaching(directory);
        String withAPassword = """
                set-env "REBOL_SAFE_PASS" "my-pass"
                system/options/home: %./
                """ + source;
        interpreter.defineNewWordsIn(withAPassword);
        return interpreter.display(interpreter.run(withAPassword));
    }

    @Nested
    @DisplayName("the codec, which is what SAVE and LOAD of a .safe file use")
    class TheCodec {

        @Test
        @DisplayName("every type of value survives the round trip")
        void roundTripsEveryKindOfValue(@TempDir Path directory) {
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
        void startsItsBytesWithTheNameOfTheFormat() {
            assertThat(answerTo(Path.of("."), """
                    text: system/codecs/safe/encode #{DEADBEEF}
                    to string! copy/part text 8"""))
                    .isEqualTo("\"SAFE 1.0\"");
        }

        @Test
        @DisplayName("a wrong password reads nothing back rather than rubbish")
        void readsNothingBackWithAWrongPassword(@TempDir Path directory) {
            assertThat(answerTo(directory, """
                    text: system/codecs/safe/encode/key #{DEADBEEF} "right"
                    none? system/codecs/safe/decode/key text "wrong\""""))
                    .isEqualTo("#(true)");
        }
    }

    @Nested
    @DisplayName("the scheme, which wraps such a file as a store")
    class TheScheme {

        @Test
        @DisplayName("opening one makes the file and answers a port")
        void makesTheFileAndAnswersAPortOnOpening(@TempDir Path directory) {
            assertThat(answerTo(directory, """
                    store: open [scheme: 'safe pass: "pw" path: %./ target: %store.safe]
                    reduce [port? store  open? store  true? exists? %store.safe]"""))
                    .isEqualTo("[#(true) #(true) #(true)]");
        }

        @Test
        @DisplayName("what is put in comes back out")
        void givesBackWhatIsPutIn(@TempDir Path directory) {
            assertThat(answerTo(directory, """
                    store: open [scheme: 'safe pass: "pw" path: %./ target: %store.safe]
                    put store 'greeting "hello"
                    reduce [select store 'greeting  pick store 'greeting]"""))
                    .isEqualTo("[\"hello\" \"hello\"]");
        }

        @Test
        @DisplayName("and it is still there after the port is closed and opened again")
        void keepsItAfterClosingAndOpeningAgain(@TempDir Path directory) {
            assertThat(answerTo(directory, """
                    store: open [scheme: 'safe pass: "pw" path: %./ target: %store.safe]
                    put store 'greeting "hello"
                    close store
                    again: open [scheme: 'safe pass: "pw" path: %./ target: %store.safe]
                    select again 'greeting""")).isEqualTo("\"hello\"");
        }

        @Test
        @DisplayName("a key that was removed is gone")
        void forgetsARemovedKey(@TempDir Path directory) {
            assertThat(answerTo(directory, """
                    store: open [scheme: 'safe pass: "pw" path: %./ target: %store.safe]
                    put store 'greeting "hello"
                    remove/key store 'greeting
                    none? select store 'greeting""")).isEqualTo("#(true)");
        }
    }

    @Nested
    @DisplayName("SET-USER, which is the whole of it from a script's side")
    class TheUser {

        @Test
        @DisplayName("a user who does not exist is not an error")
        void acceptsAUserWhoDoesNotExist(@TempDir Path directory) {
            assertThat(answerTo(directory, """
                    reduce [
                        not error? try [set-user not-existing-user]
                        none? system/user/name
                    ]""")).isEqualTo("[#(true) #(true)]");
        }

        @Test
        @DisplayName("and /N sets one up, with a storage file to show for it")
        void setsOneUpWithN(@TempDir Path directory) {
            assertThat(answerTo(directory, """
                    set-user/n/p temp-user "passw"
                    reduce [
                        system/user/name = @temp-user
                        'file = exists? system/user/data/spec/ref
                    ]""")).isEqualTo("[#(true) #(true)]");
        }

        @Test
        @DisplayName("data stored under a user is read back by USER'S")
        void readsBackDataStoredUnderAUser(@TempDir Path directory) {
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
