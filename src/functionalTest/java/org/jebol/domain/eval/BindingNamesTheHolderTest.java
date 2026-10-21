package org.jebol.domain.eval;

import org.jebol.application.AnInterpreter;
import org.jebol.application.Interpreter;
import org.jebol.domain.value.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BindingNamesTheHolderTest {

    private static final String WORD = "counted";

    private static AnyWordValue bindOneWord(String spelling, Context into) {
        var block = BlockValue.of(
                java.util.List.of(WordValue.of(spelling)));
        return (AnyWordValue) Binder.bind(block, into).remaining().getFirst();
    }

    @Test
    @DisplayName("a word the target itself holds binds to the target")
    void bindsASlotInTheTargetToTheTarget() {
        Context outer = Context.root();
        Context target = Context.makeChildOf(outer);
        target.register(WORD, IntegerValue.of(1));

        assertThat(bindOneWord(WORD, target).binding())
                .as("the target holds the slot, so it is the holder")
                .isSameAs(target);
    }

    @Test
    @DisplayName("a word only the parent holds binds to the parent, not the target")
    void bindsASlotOneContextUpToThatContext() {
        Context outer = Context.root();
        outer.register(WORD, IntegerValue.of(1));
        Context target = Context.makeChildOf(outer);

        assertThat(bindOneWord(WORD, target).binding())
                .as("the child only reaches the slot; the parent holds it")
                .isSameAs(outer);
    }

    @Test
    @DisplayName("a word several contexts up binds to whichever one holds it")
    void bindsASlotSeveralContextsUpToItsHolder() {
        Context outermost = Context.root();
        outermost.register(WORD, IntegerValue.of(1));
        Context target = Context.makeChildOf(Context.makeChildOf(Context.makeChildOf(outermost)));

        assertThat(bindOneWord(WORD, target).binding())
                .as("depth must not change which context is named")
                .isSameAs(outermost);
    }

    @Test
    @DisplayName("a word held in both places binds to the nearer one")
    void picksTheNearestHolder() {
        Context outer = Context.root();
        outer.register(WORD, IntegerValue.of(1));
        Context target = Context.makeChildOf(outer);
        target.register(WORD, IntegerValue.of(2));

        assertThat(bindOneWord(WORD, target).binding())
                .as("shadowing still works: the nearest slot is the one meant")
                .isSameAs(target);
    }

    @Test
    @DisplayName("a word nothing in the chain holds is left unbound")
    void leavesAnUnknownWordUnbound() {
        Context target = Context.makeChildOf(Context.root());

        assertThat(bindOneWord(WORD, target).isBound())
                .as("an unbound word and a word bound to unset report differently")
                .isFalse();
    }

    @Test
    @DisplayName("a word inside a nested block binds to its holder too")
    void followsTheSameRuleInNestedBlocks() {
        Context outer = Context.root();
        outer.register(WORD, IntegerValue.of(1));
        Context target = Context.makeChildOf(outer);

        var nested = BlockValue.of(java.util.List.of(
                BlockValue.of(
                        java.util.List.of(WordValue.of(WORD)))));
        var inner = (AnyBlockValue)
                Binder.bind(nested, target).remaining().getFirst();

        assertThat(((AnyWordValue) inner.remaining().getFirst()).binding())
                .as("binding walks into blocks and must answer the same there")
                .isSameAs(outer);
    }

    @Test
    @DisplayName("BIND/NEW through a word defines the name where that word lives")
    void bindNewDefinesInTheHolderNotTheInnerScope() {
        Interpreter interpreter = AnInterpreter.create();

        String source = "use [made] ["
                + " made: make word! \"borrowed-name\""
                + " made: bind/new made 'append"
                + " set made 7]";
        interpreter.defineNewWordsIn(source);
        interpreter.run(source);

        assertThat(interpreter.display(interpreter.run("borrowed-name")))
                .as("the name was hung off APPEND's home, which outlives the USE")
                .isEqualTo("7");
    }

    @Test
    @DisplayName("BIND given one word places it where BIND given a block would")
    void agreesBetweenTheWordAndBlockBranches() {
        Interpreter interpreter = AnInterpreter.create();
        interpreter.defineNewWordsIn("outer-value: 10 holder: make object! [a: 1]");
        interpreter.run("outer-value: 10 holder: make object! [a: 1]");

        String throughAWord = "bind/new 'left-by-word bind 'outer-value holder";
        String throughABlock = "bind/new 'left-by-block first bind [outer-value] holder";
        for (String each : List.of(throughAWord, throughABlock)) {
            interpreter.defineNewWordsIn(each);
            interpreter.run(each);
        }

        assertThat(interpreter.display(interpreter.run(
                "reduce [find words-of holder 'left-by-word "
                        + "find words-of holder 'left-by-block]")))
                .as("neither name belongs inside the object")
                .isEqualTo("[_ _]");
    }

    @Test
    @DisplayName("BIND on a word the target itself holds still names the target")
    void stillNamesTheTargetFromTheWordBranchWhenItHolds() {
        Interpreter interpreter = AnInterpreter.create();
        interpreter.defineNewWordsIn("holder: make object! [a: 1]");
        interpreter.run("holder: make object! [a: 1]");

        String hangANameOffA = "bind/new 'added-here bind 'a holder";
        interpreter.defineNewWordsIn(hangANameOffA);
        interpreter.run(hangANameOffA);

        assertThat(interpreter.display(interpreter.run(
                "not none? find words-of holder 'added-here")))
                .as("A does live in the object, so that is where its home is")
                .isEqualTo("#(true)");
    }

    @Test
    @DisplayName("BIND refuses a target that is neither an object nor a bound word")
    void bindRefusesAnUnusableTarget() {
        Interpreter interpreter = AnInterpreter.create();

        assertThat(interpreter.run("bind 'a 1").succeeded())
                .as("an integer names no context and must not be coerced into one")
                .isFalse();
    }

    @Test
    @DisplayName("BIND refuses a word target with no binding of its own")
    void bindRefusesAnUnboundWordAsTarget() {
        Interpreter interpreter = AnInterpreter.create();

        assertThat(interpreter.run("bind 'a to word! \"nowhere-at-all\"").succeeded())
                .as("a word that lives nowhere cannot say where anything lives")
                .isFalse();
    }

    @Test
    @DisplayName("REBOL's own reflector generator survives its USE scope")
    void letsTheBorrowedReflectorGeneratorWork() {
        Interpreter interpreter = AnInterpreter.create();

        assertThat(interpreter.display(interpreter.run("mold words-of make object! [a: 1]")))
                .as("WORDS-OF is generated inside a USE in REBOL's base-defs.reb")
                .isEqualTo("\"[a]\"");
    }
}
