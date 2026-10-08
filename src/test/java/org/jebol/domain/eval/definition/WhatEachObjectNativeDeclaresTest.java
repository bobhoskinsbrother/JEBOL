package org.jebol.domain.eval.definition;

import org.jebol.application.Interpreter;

import org.jebol.domain.value.BlockValue;
import org.jebol.domain.value.Context;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.LogicValue;
import org.jebol.domain.value.NoneValue;
import org.jebol.domain.value.ObjectValue;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.Typeset;
import org.jebol.domain.value.Value;
import org.jebol.domain.value.WordValue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WhatEachObjectNativeDeclaresTest {

    private static final Set<String> NOTHING = Set.of();

    private static final Set<Datatype> A_BLOCK = Set.of(Datatype.BLOCK);

    private Value answerOf(NativeDefinition definition, Set<String> refinements,
            Value... arguments) {
        return definition.behaviour().call(List.of(arguments), null, null, refinements);
    }

    private Context holding(String name, Value value) {
        Context fields = Context.root();
        fields.register(name, value);
        return fields;
    }

    Stream<Arguments> whatEachDeclares() {
        return Stream.of(
                Arguments.of(new MakeAction(), "make",
                        List.of(Parameter.required("prototype", Typeset.ANY_TYPE.members()),
                                Parameter.required("body", Typeset.ANY_TYPE.members())),
                        NOTHING),
                Arguments.of(new ConstructNative(), "construct",
                        List.of(Parameter.required("body",
                                        Set.of(Datatype.BLOCK, Datatype.STRING, Datatype.BINARY)),
                                Parameter.belongingTo("with", "object", Set.of(Datatype.OBJECT))),
                        Set.of("only", "with")),
                Arguments.of(new ContextOfWordNative(), "context?",
                        List.of(Parameter.required("word", Typeset.ANY_WORD.members())),
                        NOTHING),
                Arguments.of(new ResolveNative(), "resolve",
                        List.of(Parameter.required("target", Typeset.ANY_OBJECT.members()),
                                Parameter.required("source", Typeset.ANY_OBJECT.members()),
                                Parameter.belongingTo("only", "from",
                                        Set.of(Datatype.BLOCK, Datatype.INTEGER))),
                        Set.of("only", "all", "extend")),
                Arguments.of(new InNative(), "in",
                        List.of(Parameter.required("object",
                                        Typeset.ANY_OBJECT.membersAnd(Datatype.BLOCK)),
                                Parameter.required("word", Typeset.ANY_WORD.membersAnd(
                                        Datatype.BLOCK, Datatype.PAREN))),
                        NOTHING),
                Arguments.of(new ApplyNative(), "apply",
                        List.of(Parameter.required("func"), Parameter.required("block", A_BLOCK)),
                        Set.of("only")),
                Arguments.of(new AssertNative(), "assert",
                        List.of(Parameter.required("conditions", A_BLOCK)), Set.of("type")),
                Arguments.of(new HashNative(), "hash",
                        List.of(Parameter.required("value")), NOTHING),
                Arguments.of(new CollectWordsNative(), "collect-words",
                        List.of(Parameter.required("block", A_BLOCK),
                                Parameter.belongingTo("ignore", "words",
                                        Typeset.ANY_OBJECT.membersAnd(
                                                Datatype.BLOCK, Datatype.NONE)),
                                Parameter.belongingTo("as", "type", Set.of(Datatype.DATATYPE))),
                        Set.of("deep", "set", "ignore", "as")),
                Arguments.of(new NewLineNative(), "new-line",
                        List.of(Parameter.required("position",
                                        Set.of(Datatype.BLOCK, Datatype.PAREN)),
                                Parameter.required("value"),
                                Parameter.belongingTo("skip", "size", Set.of(Datatype.INTEGER))),
                        Set.of("all", "skip")),
                Arguments.of(new IsNewLineNative(), "new-line?",
                        List.of(Parameter.required("position",
                                Set.of(Datatype.BLOCK, Datatype.PAREN))),
                        NOTHING),
                Arguments.of(new ObjectNative(), "object",
                        List.of(Parameter.required("spec", A_BLOCK)), Set.of("only")),
                Arguments.of(new WithNative(), "with",
                        List.of(Parameter.required("context", Set.of(Datatype.OBJECT)),
                                Parameter.required("body", A_BLOCK)),
                        NOTHING),
                Arguments.of(new IsSelflessNative(), "selfless?",
                        List.of(Parameter.required("context")), NOTHING),
                Arguments.of(new IsProtectedNative(), "protected?",
                        List.of(Parameter.required("value")), NOTHING),
                Arguments.of(new UnbindNative(), "unbind",
                        List.of(Parameter.required("word",
                                Typeset.ANY_WORD.membersAnd(Datatype.BLOCK))),
                        Set.of("deep")),
                Arguments.of(new BindNative(), "bind",
                        List.of(Parameter.required("word"), Parameter.required("target")),
                        Set.of("copy", "only", "new", "set")));
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("whatEachDeclares")
    @DisplayName("each declares exactly what the inline definition declared")
    void declaresWhatItDeclaredInline(NativeDefinition definition, String name,
            List<Parameter> parameters, Set<String> refinements) {
        assertThat(definition.name()).isEqualTo(name);
        assertThat(definition.parameters()).isEqualTo(parameters);
        assertThat(definition.refinements()).isEqualTo(refinements);
    }

    @Nested
    @DisplayName("the questions that need no evaluator")
    class NoEvaluatorNeeded {

        @Test
        @DisplayName("context? of an unbound word is none")
        void theContextOfAnUnboundWord() {
            assertThat(answerOf(new ContextOfWordNative(), NOTHING, WordValue.of("a")))
                    .isEqualTo(NoneValue.none());
        }

        @Test
        @DisplayName("context? of a word bound into an object is that object")
        void theContextOfABoundWord() {
            Context fields = holding("a", IntegerValue.of(1));

            assertThat(answerOf(new ContextOfWordNative(), NOTHING,
                    WordValue.of("a").boundTo(fields)))
                    .isEqualTo(new ObjectValue(fields));
        }

        @Test
        @DisplayName("selfless? is true of an object with no self, and of anything not an object")
        void selfless() {
            Context withSelf = Context.root();
            withSelf.register("self", NoneValue.none());

            assertThat(answerOf(new IsSelflessNative(), NOTHING,
                    new ObjectValue(holding("a", IntegerValue.of(1)))))
                    .isEqualTo(LogicValue.of(true));
            assertThat(answerOf(new IsSelflessNative(), NOTHING, new ObjectValue(withSelf)))
                    .isEqualTo(LogicValue.of(false));
            assertThat(answerOf(new IsSelflessNative(), NOTHING, IntegerValue.of(1)))
                    .isEqualTo(LogicValue.of(true));
        }

        @Test
        @DisplayName("protected? is false of a fresh block and of a number")
        void protectedIsFalseOfTheUnprotected() {
            assertThat(answerOf(new IsProtectedNative(), NOTHING,
                    BlockValue.block(List.of(IntegerValue.of(1)))))
                    .isEqualTo(LogicValue.of(false));
            assertThat(answerOf(new IsProtectedNative(), NOTHING, IntegerValue.of(1)))
                    .isEqualTo(LogicValue.of(false));
        }

        @Test
        @DisplayName("new-line marks a line break that new-line? then sees, and clears it again")
        void newLineAndItsQuestion() {
            BlockValue block = BlockValue.block(List.of(IntegerValue.of(1), IntegerValue.of(2)));

            answerOf(new NewLineNative(), NOTHING, block, LogicValue.of(true));
            assertThat(answerOf(new IsNewLineNative(), NOTHING, block))
                    .isEqualTo(LogicValue.of(true));

            answerOf(new NewLineNative(), NOTHING, block, LogicValue.of(false));
            assertThat(answerOf(new IsNewLineNative(), NOTHING, block))
                    .isEqualTo(LogicValue.of(false));
        }

        @Test
        @DisplayName("unbind hands back the same word with no binding")
        void unbindAWord() {
            Value unbound = answerOf(new UnbindNative(), NOTHING,
                    WordValue.of("a").boundTo(holding("a", IntegerValue.of(1))));

            assertThat(((WordValue) unbound).isBound()).isFalse();
        }

        @Test
        @DisplayName("in binds a word the object holds, and is none for one it does not")
        void inAnObject() {
            Context fields = holding("a", IntegerValue.of(1));

            Value found = answerOf(new InNative(), NOTHING,
                    new ObjectValue(fields), WordValue.of("a"));
            assertThat(((WordValue) found).binding()).isSameAs(fields);
            assertThat(answerOf(new InNative(), NOTHING,
                    new ObjectValue(fields), WordValue.of("b")))
                    .isEqualTo(NoneValue.none());
        }

        @Test
        @DisplayName("collect-words/set/deep lists the set-words, once each")
        void collectWords() {
            BlockValue body = BlockValue.block(List.of(
                    WordValue.of("a", Datatype.SET_WORD), WordValue.of("b"),
                    BlockValue.block(List.of(WordValue.of("A", Datatype.SET_WORD),
                            WordValue.of("c", Datatype.SET_WORD)))));

            Value collected = answerOf(new CollectWordsNative(), Set.of("deep", "set"), body);

            assertThat(((BlockValue) collected).remaining())
                    .containsExactly(WordValue.of("a"), WordValue.of("c"));
        }

        @Test
        @DisplayName("collect-words/ignore leaves out the words it is told to")
        void collectWordsIgnoring() {
            BlockValue body = BlockValue.block(List.of(WordValue.of("a"), WordValue.of("b")));

            Value collected = answerOf(new CollectWordsNative(), Set.of("ignore"), body,
                    BlockValue.block(List.of(WordValue.of("a"))));

            assertThat(((BlockValue) collected).remaining()).containsExactly(WordValue.of("b"));
        }

        @Test
        @DisplayName("hash gives two equal values the same answer, reading words through the symbol table")
        void hashOfEqualValues() {
            Interpreter interpreter = Interpreter.create();
            assertThat(interpreter.display(interpreter.run("""
                    equal? hash 7 hash 7"""))).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("context, abs and true? are the library's names for object, absolute and did, as in r3")
        void theLibraryNamesTheAliases() {
            Interpreter interpreter = Interpreter.create();
            assertThat(interpreter.display(interpreter.run("""
                    reduce [same? :context :object same? :abs :absolute same? :true? :did true? none]"""
            ))).isEqualTo("[#(true) #(true) #(true) #(false)]");
        }
    }
}
