package org.jebol.application;

import org.jebol.domain.value.AnyWordValue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.ref.WeakReference;

import static org.assertj.core.api.Assertions.assertThat;

class NothingAScriptNamesOutlivesItsInterpreterTest {

    private static final int COLLECTIONS_ASKED_FOR = 20;

    private static WeakReference<String> nameOfWordMadeBy(String source) {
        Interpreter interpreter = AnInterpreter.create();
        ScriptOutcome outcome = interpreter.run(source);

        assertThat(outcome.conclusion()).as(outcome.display()).isEqualTo(Conclusion.PRODUCED_VALUE);
        assertThat(outcome.value()).isInstanceOf(AnyWordValue.class);
        return new WeakReference<>(((AnyWordValue) outcome.value()).canonical());
    }

    private static boolean isCollectedEventually(WeakReference<String> name) throws InterruptedException {
        for (int asked = 0; asked < COLLECTIONS_ASKED_FOR && name.get() != null; asked++) {
            System.gc();
            Thread.sleep(10);
        }
        return name.get() == null;
    }

    @Test
    @DisplayName("the name of an unbound word a script made is collected once its interpreter is gone")
    void collectsAnUnboundWordsName() throws InterruptedException {
        WeakReference<String> name = nameOfWordMadeBy("""
                words: collect [repeat i 1000 [keep to word! join "Key" i]]
                last words
                """);

        assertThat(isCollectedEventually(name)).isTrue();
    }

    @Test
    @DisplayName("the name of a word bound to an object a script made is collected once its interpreter is gone")
    void collectsABoundWordsName() throws InterruptedException {
        WeakReference<String> name = nameOfWordMadeBy("""
                words: collect [repeat i 1000 [keep to word! join "Key" i]]
                fields: make object! collect [foreach word words [keep to set-word! word keep 0]]
                in fields last words
                """);

        assertThat(isCollectedEventually(name)).isTrue();
    }
}
