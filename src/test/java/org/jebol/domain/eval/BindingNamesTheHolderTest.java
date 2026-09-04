package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.jebol.domain.value.Context;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.WordValue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A bound word names the context that holds its slot, never a descendant
 * that only reaches the slot through a parent.
 *
 * <p>Claimed in {@code spec/values.allium} and used by the rule
 * {@code LoadBindsIntoTargetContext} in {@code spec/load.allium}. It matters
 * because BIND asked for the home of a word answers whatever the word says,
 * and code that then defines a name there expects the definition to outlive
 * the block that was being bound. REBOL's own boot writes its six reflector
 * functions that way, inside a USE whose scope is thrown away straight
 * afterwards.
 *
 * <p>The boundaries here are the depths at which a slot can sit relative to
 * the target: in the target itself, one context up, several up, in two
 * places at once, and nowhere at all.
 */
class BindingNamesTheHolderTest {

    private static final String WORD = "counted";

    private static WordValue bindOneWord(String spelling, Context into) {
        var block = org.jebol.domain.value.BlockValue.block(
                java.util.List.of(WordValue.of(spelling)));
        return (WordValue) Binder.bind(block, into).remaining().getFirst();
    }

    @Test
    @DisplayName("a word the target itself holds binds to the target")
    void aSlotInTheTargetBindsToTheTarget() {
        Context outer = Context.root();
        Context target = Context.childOf(outer);
        target.set(WORD, IntegerValue.of(1));

        assertThat(bindOneWord(WORD, target).binding())
                .as("the target holds the slot, so it is the holder")
                .isSameAs(target);
    }

    @Test
    @DisplayName("a word only the parent holds binds to the parent, not the target")
    void aSlotOneContextUpBindsToThatContext() {
        Context outer = Context.root();
        outer.set(WORD, IntegerValue.of(1));
        Context target = Context.childOf(outer);

        assertThat(bindOneWord(WORD, target).binding())
                .as("the child only reaches the slot; the parent holds it")
                .isSameAs(outer);
    }

    @Test
    @DisplayName("a word several contexts up binds to whichever one holds it")
    void aSlotSeveralContextsUpBindsToItsHolder() {
        Context outermost = Context.root();
        outermost.set(WORD, IntegerValue.of(1));
        Context target = Context.childOf(Context.childOf(Context.childOf(outermost)));

        assertThat(bindOneWord(WORD, target).binding())
                .as("depth must not change which context is named")
                .isSameAs(outermost);
    }

    @Test
    @DisplayName("a word held in both places binds to the nearer one")
    void theNearestHolderWins() {
        Context outer = Context.root();
        outer.set(WORD, IntegerValue.of(1));
        Context target = Context.childOf(outer);
        target.set(WORD, IntegerValue.of(2));

        assertThat(bindOneWord(WORD, target).binding())
                .as("shadowing still works: the nearest slot is the one meant")
                .isSameAs(target);
    }

    @Test
    @DisplayName("a word nothing in the chain holds is left unbound")
    void anUnknownWordStaysUnbound() {
        Context target = Context.childOf(Context.root());

        assertThat(bindOneWord(WORD, target).isBound())
                .as("an unbound word and a word bound to unset report differently")
                .isFalse();
    }

    @Test
    @DisplayName("a word inside a nested block binds to its holder too")
    void nestedBlocksFollowTheSameRule() {
        Context outer = Context.root();
        outer.set(WORD, IntegerValue.of(1));
        Context target = Context.childOf(outer);

        var nested = org.jebol.domain.value.BlockValue.block(java.util.List.of(
                org.jebol.domain.value.BlockValue.block(
                        java.util.List.of(WordValue.of(WORD)))));
        var inner = (org.jebol.domain.value.BlockValue)
                Binder.bind(nested, target).remaining().getFirst();

        assertThat(((WordValue) inner.remaining().getFirst()).binding())
                .as("binding walks into blocks and must answer the same there")
                .isSameAs(outer);
    }

    @Test
    @DisplayName("BIND/NEW through a word defines the name where that word lives")
    void bindNewDefinesInTheHolderNotTheInnerScope() {
        Interpreter interpreter = Interpreter.create();

        String source = "use [made] ["
                + " made: make word! \"borrowed-name\""
                + " made: bind/new made 'append"
                + " set made 7]";
        interpreter.defineFreshWordsIn(source);
        interpreter.run(source);

        assertThat(interpreter.display(interpreter.run("borrowed-name")))
                .as("the name was hung off APPEND's home, which outlives the USE")
                .isEqualTo("7");
    }

    @Test
    @DisplayName("BIND given one word places it where BIND given a block would")
    void theWordBranchAgreesWithTheBlockBranch() {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn("outer-value: 10 holder: make object! [a: 1]");
        interpreter.run("outer-value: 10 holder: make object! [a: 1]");

        String throughAWord = "bind/new 'left-by-word bind 'outer-value holder";
        String throughABlock = "bind/new 'left-by-block first bind [outer-value] holder";
        for (String each : List.of(throughAWord, throughABlock)) {
            interpreter.defineFreshWordsIn(each);
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
    void theWordBranchStillNamesTheTargetWhenItIsTheHolder() {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn("holder: make object! [a: 1]");
        interpreter.run("holder: make object! [a: 1]");

        String hangANameOffA = "bind/new 'added-here bind 'a holder";
        interpreter.defineFreshWordsIn(hangANameOffA);
        interpreter.run(hangANameOffA);

        assertThat(interpreter.display(interpreter.run(
                "not none? find words-of holder 'added-here")))
                .as("A does live in the object, so that is where its home is")
                .isEqualTo("#(true)");
    }

    @Test
    @DisplayName("BIND refuses a target that is neither an object nor a bound word")
    void bindRefusesAnUnusableTarget() {
        Interpreter interpreter = Interpreter.create();

        assertThat(interpreter.run("bind 'a 1").succeeded())
                .as("an integer names no context and must not be coerced into one")
                .isFalse();
    }

    @Test
    @DisplayName("BIND refuses a word target with no binding of its own")
    void bindRefusesAnUnboundWordAsTarget() {
        Interpreter interpreter = Interpreter.create();

        assertThat(interpreter.run("bind 'a to word! \"nowhere-at-all\"").succeeded())
                .as("a word that lives nowhere cannot say where anything lives")
                .isFalse();
    }

    @Test
    @DisplayName("REBOL's own reflector generator survives its USE scope")
    void theBorrowedReflectorGeneratorWorks() {
        Interpreter interpreter = Interpreter.create();

        assertThat(interpreter.display(interpreter.run("mold words-of make object! [a: 1]")))
                .as("WORDS-OF is generated inside a USE in REBOL's base-defs.reb")
                .isEqualTo("\"[a]\"");
    }
}
