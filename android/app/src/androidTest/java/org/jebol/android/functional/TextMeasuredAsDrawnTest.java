package org.jebol.android.functional;

import android.content.Context;

import androidx.test.platform.app.InstrumentationRegistry;

import org.jebol.android.AppSession;
import org.jebol.android.JebolView;
import org.jebol.application.Interpreter;
import org.jebol.application.MemoryFiles;
import org.jebol.domain.host.OutputPort;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class TextMeasuredAsDrawnTest {

    private static final String CARET_AFTER_THE_TEXT_AND_WHERE_ITS_INK_ENDS = """
            shown: {%s}
            plain: make gob! [size: 600x40 text: shown]
            caret: caret-to-offset plain 1 1 + length? shown
            picture: to image! plain
            inked: 0
            repeat across picture/size/x [
                repeat down picture/size/y [
                    seen: pick picture as-pair across down
                    if 230 > min min seen/1 seen/2 seen/3 [inked: across]
                ]
            ]
            to integer! caret/x - inked
            """;

    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    private final AtomicReference<JebolView> made = new AtomicReference<>();
    private AppSession session;

    @BeforeEach
    void openSession() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> made.set(new JebolView(context)));
        session = AppSession.open(made.get(), new MemoryFiles(), OutputPort.discard(), wanted -> {
        }, () -> false);
        made.get().measureViewport(1600, 1200);
    }

    @AfterEach
    void closeSession() {
        session.close();
    }

    private int answer(String source) {
        Interpreter interpreter = session.interpreter();
        interpreter.defineNewWordsIn(source);
        return Integer.parseInt(interpreter.display(interpreter.run(source)));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "The quick brown fox jumps over the lazy dog",
            "AVATAR To Wa Yo fi fl office",
            "iiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiii",
            "a😀b",
            "😀😃😄"})
    void theCaretAfterTheTextIsWhereItsInkEnds(String shown) {
        assertThat(answer(CARET_AFTER_THE_TEXT_AND_WHERE_ITS_INK_ENDS.formatted(shown))).isBetween(-1, 2);
    }
}
