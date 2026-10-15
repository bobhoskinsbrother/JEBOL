package org.jebol.application;

import org.jebol.domain.read.Transcoder;
import org.jebol.domain.value.SetWordValue;
import org.jebol.domain.value.Value;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

class PreludeTest {

    private static final String PRELUDE = "/org/jebol/prelude.reb";

    private static final String[] THE_LIBRARY_FILES_LOADED_BEFORE_THE_PRELUDE = {
        "/org/jebol/mezz/base-constants.reb",
        "/org/jebol/mezz/base-funcs.reb"
    };

    private static Set<String> theWordsSetAtTheTopOf(String resource) {
        try (InputStream source = PreludeTest.class.getResourceAsStream(resource)) {
            assertThat(source).as(resource).isNotNull();
            String text = new String(source.readAllBytes(), StandardCharsets.UTF_8);
            Set<String> set = new TreeSet<>();
            for (Value each : Transcoder.transcode(text).values().orElseThrow().remaining()) {
                if (each instanceof SetWordValue word) {
                    set.add(word.canonical());
                }
            }
            return set;
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }

    @Test
    @DisplayName("a function the prelude defines is callable")
    void aPreludeFunctionIsCallable() {
        Interpreter interpreter = Interpreter.create();

        assertThat(interpreter.display(interpreter.run("length? rejoin [{ab} {c}]")))
                .isEqualTo("3");
    }

    @Test
    @DisplayName("a prelude function is an ordinary function value")
    void aPreludeFunctionIsAValue() {
        Interpreter interpreter = Interpreter.create();

        assertThat(interpreter.display(interpreter.run("type? :rejoin")))
                .isEqualTo("#(function!)");
    }

    @Test
    @DisplayName("a caller cannot tell it from a native")
    void aPreludeFunctionCanBeRenamedAndCalled() {
        Interpreter interpreter = Interpreter.create();

        assertThat(interpreter.display(interpreter.run(
                "joined: :rejoin  length? joined [{ab} {c}]")))
                .isEqualTo("3");
    }

    @Test
    @DisplayName("the prelude can use the natives")
    void thePreludeSeesTheNatives() {
        Interpreter interpreter = Interpreter.create();

        assertThat(interpreter.display(interpreter.run(
                "same? :ajoin :ajoin  length? rejoin [1 2 3]")))
                .isEqualTo("3");
    }

    @Test
    @DisplayName("a script's own words do not disturb it for the next interpreter")
    void aScriptCannotBreakThePreludeForTheNextInterpreter() {
        Interpreter.create().run("rejoin: 3");

        Interpreter next = Interpreter.create();
        assertThat(next.display(next.run("length? rejoin [{ab} {c}]"))).isEqualTo("3");
    }

    @Test
    @DisplayName("the prelude sets no word that Rebol's own library has already set before it")
    void thePreludeShadowsNothingRebolDefinesFirst() {
        Set<String> alreadySet = new TreeSet<>();
        for (String earlier : THE_LIBRARY_FILES_LOADED_BEFORE_THE_PRELUDE) {
            alreadySet.addAll(theWordsSetAtTheTopOf(earlier));
        }
        assertThat(alreadySet).contains("func", "function", "funct", "does");

        Set<String> setAgain = theWordsSetAtTheTopOf(PRELUDE);
        setAgain.retainAll(alreadySet);

        assertThat(setAgain)
                .as("the prelude loads after these, so setting one replaces Rebol's own definition")
                .isEmpty();
    }
}
