package org.jebol.domain.value;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Modifier;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class ADatatypeWithNoValuesCannotBeBuiltTest {

    private String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return Molder.form(interpreter.run(source).value());
    }

    @ParameterizedTest
    @ValueSource(strings = {"end", "frame", "rebcode", "command", "library"})
    @DisplayName("make refuses to build one, naming the datatype and what it was given")
    void makeRefuses(String datatype) {
        assertThat(answerTo("e: try [make " + datatype + "! []] mold reduce [e/id e/arg1 e/arg2]"))
                .isEqualTo("[bad-make-arg #(" + datatype + "!) []]");
    }

    @ParameterizedTest
    @ValueSource(strings = {"end", "frame", "rebcode", "command", "library"})
    @DisplayName("make refuses a number as readily as a block")
    void makeRefusesANumber(String datatype) {
        assertThat(answerTo("e: try [make " + datatype + "! 1] mold reduce [e/id e/arg1 e/arg2]"))
                .isEqualTo("[bad-make-arg #(" + datatype + "!) 1]");
    }

    @ParameterizedTest
    @ValueSource(strings = {"end", "frame", "rebcode", "command", "library"})
    @DisplayName("to refuses to build one")
    void toRefuses(String datatype) {
        assertThat(answerTo("e: try [to " + datatype + "! 1] mold reduce [e/id e/arg1 e/arg2]"))
                .isEqualTo("[bad-make-arg #(" + datatype + "!) 1]");
    }

    @Test
    @DisplayName("make refuses a utype!, as an invalid argument")
    void makeRefusesAUtype() {
        assertThat(answerTo("e: try [make utype! []] mold reduce [e/id e/arg1 e/arg2]"))
                .isEqualTo("[invalid-arg [] _]");
    }

    @Test
    @DisplayName("to refuses a utype!, as an invalid type")
    void toRefusesAUtype() {
        assertThat(answerTo("e: try [to utype! 1] mold reduce [e/id e/arg1 e/arg2]"))
                .isEqualTo("[invalid-type #(utype!) _]");
    }

    @ParameterizedTest
    @ValueSource(strings = {"end", "frame", "rebcode", "command", "library", "utype"})
    @DisplayName("the construction syntax refuses to build one")
    void constructionRefuses(String datatype) {
        assertThat(answerTo("e: try [load {#(" + datatype + "! [])}] mold reduce [e/id e/arg1]"))
                .isEqualTo("[malconstruct [" + datatype + "! []]]");
    }

    @ParameterizedTest
    @ValueSource(strings = {"end", "frame", "rebcode", "command", "library", "utype"})
    @DisplayName("the datatype itself is still a value, of datatype!")
    void theDatatypeIsAValue(String datatype) {
        assertThat(answerTo("mold reduce [type? " + datatype + "! datatype? " + datatype + "!]"))
                .isEqualTo("[#(datatype!) #(true)]");
    }

    @ParameterizedTest
    @ValueSource(classes = {EndValue.class, FrameValue.class, RebcodeValue.class,
            CommandValue.class, LibraryValue.class, UtypeValue.class})
    @DisplayName("the class that holds the datatype offers no way to make an instance")
    void theClassCannotBeInstantiated(Class<?> holder) {
        assertThat(holder.getDeclaredConstructors())
                .allMatch(constructor -> Modifier.isPrivate(constructor.getModifiers()));
        assertThat(Arrays.stream(holder.getDeclaredMethods())
                .filter(method -> !method.isSynthetic())
                .filter(method -> holder.isAssignableFrom(method.getReturnType())))
                .isEmpty();
        assertThat(Arrays.stream(holder.getDeclaredFields())
                .filter(field -> !field.isSynthetic())
                .filter(field -> holder.isAssignableFrom(field.getType())))
                .isEmpty();
    }

    @ParameterizedTest
    @ValueSource(classes = {EndValue.class, FrameValue.class, RebcodeValue.class,
            CommandValue.class, LibraryValue.class, UtypeValue.class})
    @DisplayName("and is not a value, so nothing could ever hold one")
    void theClassIsNotAValue(Class<?> holder) {
        assertThat(Value.class.isAssignableFrom(holder)).isFalse();
    }

    @Test
    @DisplayName("any-type! holds every datatype but end!")
    void anyTypeHoldsAllButEnd() {
        assertThat(answerTo("mold reduce [find any-type! end! find any-type! unset!]"))
                .isEqualTo("[#(false) #(true)]");
    }
}
