package org.jebol.android.functional;

import android.content.Context;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

import androidx.test.platform.app.InstrumentationRegistry;

import org.jebol.android.AppSession;
import org.jebol.android.JebolView;
import org.jebol.application.Interpreter;
import org.jebol.application.MemoryFiles;
import org.jebol.domain.host.OutputPort;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

class TheSoftKeyboardsEditsTest {

    private static final String A_FIELD_THAT_KEEPS_WHAT_IT_HEARS = """
            field: copy ""
            view/no-wait make gob! [size: 400x300]
            handle-events [
                name: 'keys
                priority: 100
                handler: func [event] [
                    if event/type = 'key [
                        either event/key = #"^H" [take/last field] [append field event/key]
                    ]
                    none
                ]
            ]
            """;

    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    private final AtomicReference<JebolView> made = new AtomicReference<>();
    private AppSession session;
    private InputConnection connection;

    @BeforeEach
    void openAFieldWithTheKeyboardUp() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> made.set(new JebolView(context)));
        session = AppSession.open(made.get(), new MemoryFiles(), OutputPort.discard(), wanted -> {
        }, () -> false);
        made.get().measureViewport(1600, 1200);
        answer(A_FIELD_THAT_KEEPS_WHAT_IT_HEARS);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(
                () -> connection = made.get().onCreateInputConnection(new EditorInfo()));
    }

    @AfterEach
    void closeSession() {
        session.close();
    }

    private String answer(String source) {
        Interpreter interpreter = session.interpreter();
        interpreter.defineNewWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private void keyboard(Consumer<InputConnection> calls) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> calls.accept(connection));
    }

    private String whatTheFieldHolds() {
        return answer("wait [system/ports/event 0.3] field");
    }

    private static String molded(String text) {
        return '"' + text + '"';
    }

    @Test
    @DisplayName("a word swiped arrives once, however often the keyboard changes its guess while the finger moves")
    void aSwipedWordArrivesOnce() {
        keyboard(it -> {
            it.setComposingText("h", 1);
            it.setComposingText("he", 1);
            it.setComposingText("hel", 1);
            it.setComposingText("hello", 1);
            it.finishComposingText();
        });

        assertThat(whatTheFieldHolds()).isEqualTo(molded("hello"));
    }

    @Test
    @DisplayName("the keyboard sees the word just typed before the cursor, so the next word swiped can start with a space")
    void theKeyboardSeesWhatWasTyped() {
        keyboard(it -> {
            it.setComposingText("hello", 1);
            it.finishComposingText();
        });

        assertThat(connection.getTextBeforeCursor(20, 0).toString()).isEqualTo("hello");
    }

    @Test
    @DisplayName("Gboard, as logged on a phone: a swiped word committed, a suggestion tapped "
            + "that replaces it with a space after, then a second swiped word")
    void gboardsSwipeThenSuggestionThenSwipe() {
        keyboard(it -> {
            it.getSurroundingText(600, 600, 1);
            it.finishComposingText();
            it.requestCursorUpdates(2, 0);
            it.commitText("freed", 1);
            it.beginBatchEdit();
            it.replaceText(0, 5, "Fred ", 1, null);
            it.endBatchEdit();
            it.commitText("Fred", 1);
        });

        assertThat(whatTheFieldHolds()).isEqualTo(molded("Fred Fred"));
    }

    @Test
    @DisplayName("the text around the cursor that Gboard asks for holds what was typed")
    void theSurroundingTextHoldsWhatWasTyped() {
        keyboard(it -> it.commitText("freed", 1));

        assertThat(connection.getSurroundingText(600, 600, 0).getText().toString()).isEqualTo("freed");
    }

    @Test
    @DisplayName("text committed or composed with a text attribute arrives once")
    void textWithAnAttributeArrivesOnce() {
        keyboard(it -> {
            it.commitText("ab", 1, null);
            it.setComposingText("cd", 1, null);
            it.finishComposingText();
        });

        assertThat(whatTheFieldHolds()).isEqualTo(molded("abcd"));
    }

    @Test
    @DisplayName("a suggestion chosen while the word is still being composed replaces it")
    void aSuggestionReplacesTheComposedWord() {
        keyboard(it -> {
            it.commitText("good ", 1);
            it.setComposingText("helo", 1);
            it.commitText("hello", 1);
        });

        assertThat(whatTheFieldHolds()).isEqualTo(molded("good hello"));
    }

    @Test
    @DisplayName("a suggestion chosen after the word was committed takes the word back and replaces it")
    void aSuggestionReplacesACommittedWord() {
        keyboard(it -> {
            it.commitText("good helo", 1);
            it.setComposingRegion(5, 9);
            it.commitText("help", 1);
        });

        assertThat(whatTheFieldHolds()).isEqualTo(molded("good help"));
    }

    @Test
    @DisplayName("a suggestion that deletes the word before the cursor and commits another replaces only that word")
    void aSuggestionThatDeletesThenCommits() {
        keyboard(it -> {
            it.commitText("good morning", 1);
            it.deleteSurroundingText(7, 0);
            it.commitText("evening", 1);
        });

        assertThat(whatTheFieldHolds()).isEqualTo(molded("good evening"));
    }

    @Test
    @DisplayName("an emoji changed for another that shares its first half is one character out and one in")
    void anEmojiIsOneCharacter() {
        keyboard(it -> {
            it.commitText("a", 1);
            it.setComposingText("😀", 1);
            it.setComposingText("😃", 1);
            it.finishComposingText();
        });

        assertThat(whatTheFieldHolds()).isEqualTo(molded("a😃"));
    }

    @Test
    @DisplayName("the keyboard's own delete key takes back what was typed, and still reaches the script with nothing typed")
    void theDeleteKeyTakesBack() {
        answer("append field {xy}");
        keyboard(it -> {
            it.commitText("ab", 1);
            it.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL));
            it.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL));
        });
        assertThat(whatTheFieldHolds()).isEqualTo(molded("xya"));
        assertThat(connection.getTextBeforeCursor(20, 0).toString()).isEqualTo("a");

        keyboard(it -> {
            it.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL));
            it.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL));
        });

        assertThat(whatTheFieldHolds()).isEqualTo(molded("x"));
        assertThat(connection.getTextBeforeCursor(20, 0).toString()).isEmpty();
    }
}
