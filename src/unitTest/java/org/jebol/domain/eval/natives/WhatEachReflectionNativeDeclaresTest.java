package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.actions.DatatypePredicateAction;
import org.jebol.domain.eval.actions.IsEvenAction;
import org.jebol.domain.eval.actions.IsOddAction;
import org.jebol.domain.value.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WhatEachReflectionNativeDeclaresTest {

    private static final Set<String> NOTHING = Set.of();

    private static final Set<Datatype> ANY_TYPE = TypesetValue.ANY_TYPE.members();

    private static final Set<Datatype> A_BINARY = Set.of(BinaryValue.TYPE);

    private static final Set<Datatype> AN_IMAGE = Set.of(ImageValue.TYPE);

    private static final Set<Datatype> A_PAIR = Set.of(PairValue.TYPE);

    private static final Set<Datatype> A_WORD = Set.of(WordValue.TYPE);

    private static final Set<Datatype> A_BLOCK = Set.of(BlockValue.TYPE);

    private static final Set<Datatype> AN_INTEGER = Set.of(IntegerValue.TYPE);

    private Value answerOf(DefaultNative definition, Set<String> refinements,
                           Value... arguments) {
        return definition.behaviour().call(List.of(arguments), null, null, refinements);
    }

    private Value answerOf(DefaultNative definition, Value... arguments) {
        return answerOf(definition, NOTHING, arguments);
    }

    private Set<Datatype> anythingAtAll() {
        return TypesetValue.ANY_TYPE.membersAnd(UnsetValue.TYPE);
    }

    private Set<Datatype> anyStringOrABinary() {
        return TypesetValue.ANY_STRING.membersAnd(BinaryValue.TYPE);
    }

    private Set<Datatype> everyKindOfNumber() {
        return TypesetValue.NUMBER.membersAnd(MoneyValue.TYPE, PairValue.TYPE, TupleValue.TYPE,
                TimeValue.TYPE, DateValue.TYPE, CharacterValue.TYPE, VectorValue.TYPE);
    }

    Stream<Arguments> whatEachDeclares() {
        return Stream.of(
                Arguments.of(new ShiftNative(), "shift",
                        List.of(Parameter.required("value", AN_INTEGER),
                                Parameter.required("places", AN_INTEGER)),
                        Set.of("logical")),
                Arguments.of(new IsOddAction(), "odd?",
                        List.of(Parameter.required("number")), NOTHING),
                Arguments.of(new IsEvenAction(), "even?",
                        List.of(Parameter.required("number")), NOTHING),
                Arguments.of(new TypeOfNative(), "type?",
                        List.of(Parameter.required("value", ANY_TYPE)), Set.of("word")),
                Arguments.of(new IsAnyTypeNative(), "any-type?",
                        List.of(Parameter.required("value", ANY_TYPE)), NOTHING),
                Arguments.of(new IsCopyableNative(), "copyable?",
                        List.of(Parameter.required("value", ANY_TYPE)), NOTHING),
                Arguments.of(new IsImmediateNative(), "immediate?",
                        List.of(Parameter.required("value", ANY_TYPE)), NOTHING),
                Arguments.of(new IsInternalNative(), "internal?",
                        List.of(Parameter.required("value", ANY_TYPE)), NOTHING),
                Arguments.of(new DidNative(), "did",
                        List.of(Parameter.required("value", ANY_TYPE)), NOTHING),
                Arguments.of(new IsNumberNative(), "number?",
                        List.of(Parameter.required("value", anythingAtAll())), NOTHING),
                Arguments.of(new IsAsciiNative(), "ascii?",
                        List.of(Parameter.required("value")), NOTHING),
                Arguments.of(new IsLatin1Native(), "latin1?",
                        List.of(Parameter.required("value")), NOTHING),
                Arguments.of(new FormOidNative(), "form-oid",
                        List.of(Parameter.required("oid", A_BINARY)), NOTHING),
                Arguments.of(new BinaryNative(), "binary",
                        List.of(Parameter.required("ctx", Set.of(ObjectValue.TYPE,
                                        BinaryValue.TYPE, IntegerValue.TYPE, NoneValue.TYPE)),
                                Parameter.belongingTo("init", "spec", Set.of(BinaryValue.TYPE,
                                        IntegerValue.TYPE, NoneValue.TYPE)),
                                Parameter.belongingTo("write", "data",
                                        Set.of(BinaryValue.TYPE, BlockValue.TYPE)),
                                Parameter.belongingTo("read", "code", Set.of(WordValue.TYPE,
                                        BlockValue.TYPE, IntegerValue.TYPE, BinaryValue.TYPE)),
                                Parameter.belongingTo("into", "out", A_BLOCK),
                                Parameter.belongingTo("with", "num", AN_INTEGER)),
                        Set.of("init", "write", "read", "into", "with")),
                Arguments.of(new RegisterNative(), "register",
                        List.of(Parameter.hardQuoted("name"),
                                Parameter.required("value", Set.of(StructValue.TYPE))),
                        NOTHING),
                Arguments.of(new XtestNative(), "xtest", List.of(), NOTHING),
                Arguments.of(new PremultiplyNative(), "premultiply",
                        List.of(Parameter.required("image", AN_IMAGE)), NOTHING),
                Arguments.of(new BlurNative(), "blur",
                        List.of(Parameter.required("image", AN_IMAGE),
                                Parameter.required("radius", TypesetValue.NUMBER.members())),
                        NOTHING),
                Arguments.of(new ResizeNative(), "resize",
                        List.of(Parameter.required("image", AN_IMAGE),
                                Parameter.required("size", Set.of(PairValue.TYPE,
                                        PercentValue.TYPE, IntegerValue.TYPE)),
                                Parameter.belongingTo("filter", "name",
                                        Set.of(WordValue.TYPE, IntegerValue.TYPE)),
                                Parameter.belongingTo("blur", "factor",
                                        TypesetValue.NUMBER.members())),
                        Set.of("filter", "blur")),
                Arguments.of(new ImageDiffNative(), "image-diff",
                        List.of(Parameter.required("a", AN_IMAGE),
                                Parameter.required("b", AN_IMAGE),
                                Parameter.belongingTo("part", "offset", A_PAIR),
                                Parameter.belongingTo("part", "size", A_PAIR)),
                        Set.of("part")),
                Arguments.of(new ImageNative(), "image",
                        List.of(Parameter.belongingTo("load", "src-file",
                                        Set.of(FileValue.TYPE, BinaryValue.TYPE)),
                                Parameter.belongingTo("save", "dst-file",
                                        Set.of(NoneValue.TYPE, FileValue.TYPE, BinaryValue.TYPE)),
                                Parameter.belongingTo("save", "dst-image",
                                        Set.of(NoneValue.TYPE, ImageValue.TYPE)),
                                Parameter.belongingTo("frame", "num", AN_INTEGER),
                                Parameter.belongingTo("as", "type", A_WORD)),
                        Set.of("load", "save", "frame", "as")),
                Arguments.of(new GenerateNative(), "generate",
                        List.of(Parameter.required("type", A_WORD)), NOTHING),
                Arguments.of(new EcdhNative(), "ecdh",
                        List.of(Parameter.required("key",
                                        Set.of(HandleValue.TYPE, NoneValue.TYPE)),
                                Parameter.belongingTo("init", "type", A_WORD),
                                Parameter.belongingTo("secret", "public-key", A_BINARY)),
                        Set.of("init", "curve", "public", "secret")),
                Arguments.of(new EcdsaNative(), "ecdsa",
                        List.of(Parameter.required("key",
                                        Set.of(HandleValue.TYPE, BinaryValue.TYPE)),
                                Parameter.required("hash", A_BINARY),
                                Parameter.belongingTo("verify", "signature", A_BINARY),
                                Parameter.belongingTo("curve", "type", A_WORD)),
                        Set.of("sign", "verify", "curve")),
                Arguments.of(new DhInitNative(), "dh-init",
                        List.of(Parameter.required("g", A_BINARY),
                                Parameter.required("p", A_BINARY)),
                        NOTHING),
                Arguments.of(new DhNative(), "dh",
                        List.of(Parameter.required("dh-key", Set.of(HandleValue.TYPE)),
                                Parameter.belongingTo("secret", "public-key", A_BINARY)),
                        Set.of("public", "secret")),
                Arguments.of(new RsaInitNative(), "rsa-init",
                        List.of(Parameter.required("n", A_BINARY),
                                Parameter.required("e", A_BINARY),
                                Parameter.belongingTo("private", "d", A_BINARY),
                                Parameter.belongingTo("private", "p", A_BINARY),
                                Parameter.belongingTo("private", "q", A_BINARY)),
                        Set.of("private")),
                Arguments.of(new RsaNative(), "rsa",
                        List.of(Parameter.required("rsa-key", Set.of(HandleValue.TYPE)),
                                Parameter.required("data", anyStringOrABinary()),
                                Parameter.belongingTo("verify", "signature", A_BINARY),
                                Parameter.belongingTo("hash", "algorithm",
                                        Set.of(WordValue.TYPE, NoneValue.TYPE))),
                        Set.of("encrypt", "decrypt", "sign", "verify", "hash", "oaep", "pss")),
                Arguments.of(new Rc4Native(), "rc4",
                        List.of(Parameter.belongingTo("key", "crypt-key", A_BINARY),
                                Parameter.belongingTo("stream", "ctx", Set.of(HandleValue.TYPE)),
                                Parameter.belongingTo("stream", "data", A_BINARY)),
                        Set.of("key", "stream")),
                Arguments.of(new UtfNative(), "utf?",
                        List.of(Parameter.required("data", A_BINARY)), NOTHING),
                Arguments.of(new InvalidUtfNative(), "invalid-utf?",
                        List.of(Parameter.required("data", A_BINARY),
                                Parameter.belongingTo("utf", "num", AN_INTEGER)),
                        Set.of("utf")),
                Arguments.of(new IsNegativeNative(), "negative?",
                        List.of(Parameter.required("value", everyKindOfNumber())), NOTHING),
                Arguments.of(new IsPositiveNative(), "positive?",
                        List.of(Parameter.required("value", everyKindOfNumber())), NOTHING),
                Arguments.of(new IsZeroNative(), "zero?",
                        List.of(Parameter.required("value", ANY_TYPE)), NOTHING),
                Arguments.of(new IsValueNative(), "value?",
                        List.of(Parameter.required("word", A_WORD)), NOTHING),
                Arguments.of(new UnsetNative(), "unset",
                        List.of(Parameter.required("word",
                                Set.of(WordValue.TYPE, BlockValue.TYPE, NoneValue.TYPE))),
                        NOTHING),
                Arguments.of(new ProtectNative(), "protect",
                        List.of(Parameter.required("target")),
                        Set.of("deep", "words", "values", "hide", "lock")),
                Arguments.of(new UnprotectNative(), "unprotect",
                        List.of(Parameter.required("target")),
                        Set.of("deep", "words", "values")),
                Arguments.of(new DelectNative(), "delect",
                        List.of(Parameter.required("dialect", Set.of(ObjectValue.TYPE)),
                                Parameter.required("input", A_BLOCK),
                                Parameter.required("output", A_BLOCK),
                                Parameter.belongingTo("in", "where", A_BLOCK)),
                        Set.of("in", "all")));
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("whatEachDeclares")
    @DisplayName("each declares exactly what the inline definition declared")
    void declaresWhatItDeclaredInline(DefaultNative definition, String name,
            List<Parameter> parameters, Set<String> refinements) {
        assertThat(definition.nativeName()).isEqualTo(name);
        assertThat(definition.parametersAsWritten()).isEqualTo(parameters);
        assertThat(definition.refinementsDeclaredApart()).isEqualTo(refinements);
    }

    Stream<Datatype> everyDatatype() {
        return Catalogue.DATATYPES.entries().stream();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("everyDatatype")
    @DisplayName("every datatype's predicate is named after it and takes any value")
    void everyDatatypeHasAPredicate(Datatype datatype) {
        DatatypePredicateAction predicate = new DatatypePredicateAction(datatype);

        assertThat(predicate.nativeName()).isEqualTo(datatype.spelling() + "?");
        assertThat(predicate.parametersAsWritten())
                .isEqualTo(List.of(Parameter.required("value", ANY_TYPE)));
        assertThat(predicate.refinementsDeclaredApart()).isEmpty();
    }

    @Nested
    @DisplayName("shift moves the bits of a whole number")
    class Shift {

        @Test
        @DisplayName("left by a positive count, keeping the sign by default")
        void leftKeepingTheSign() {
            assertThat(answerOf(new ShiftNative(), IntegerValue.of(1), IntegerValue.of(3)))
                    .isEqualTo(IntegerValue.of(8));
            assertThat(answerOf(new ShiftNative(), IntegerValue.of(-8), IntegerValue.of(-1)))
                    .isEqualTo(IntegerValue.of(-4));
        }

        @Test
        @DisplayName("/logical fills from the left with zeros")
        void logicalFillsWithZeros() {
            assertThat(answerOf(new ShiftNative(), Set.of("logical"),
                    IntegerValue.of(-8), IntegerValue.of(-1)))
                    .isEqualTo(IntegerValue.of(9223372036854775804L));
        }

        @Test
        @DisplayName("one shifted by sixty-two still fits")
        void sixtyTwoFits() {
            assertThat(answerOf(new ShiftNative(), IntegerValue.of(1), IntegerValue.of(62)))
                    .isEqualTo(IntegerValue.of(4611686018427387904L));
        }

        @Test
        @DisplayName("one shifted by sixty-three or sixty-four overflows")
        void sixtyThreeAndSixtyFourOverflow() {
            assertThatThrownBy(() -> answerOf(new ShiftNative(),
                    IntegerValue.of(1), IntegerValue.of(63)))
                    .isInstanceOf(Raised.class);
            assertThatThrownBy(() -> answerOf(new ShiftNative(),
                    IntegerValue.of(1), IntegerValue.of(64)))
                    .isInstanceOf(Raised.class);
        }

        @Test
        @DisplayName("zero shifted past every bit is still zero")
        void zeroStaysZero() {
            assertThat(answerOf(new ShiftNative(), IntegerValue.of(0), IntegerValue.of(64)))
                    .isEqualTo(IntegerValue.of(0));
        }
    }

    @Nested
    @DisplayName("the predicates that answer about a value without an evaluator")
    class Predicates {

        @Test
        @DisplayName("odd? and even? round a decimal and ask both halves of a pair")
        void oddAndEven() {
            assertThat(answerOf(new IsOddAction(), IntegerValue.of(3)))
                    .isEqualTo(LogicValue.of(true));
            assertThat(answerOf(new IsOddAction(), PairValue.of(2, 3)))
                    .isEqualTo(LogicValue.of(false));
            assertThat(answerOf(new IsEvenAction(), DecimalValue.of(2.5)))
                    .isEqualTo(LogicValue.of(false));
        }

        @Test
        @DisplayName("type? answers the datatype, and /word answers its name")
        void typeOf() {
            assertThat(answerOf(new TypeOfNative(), IntegerValue.of(1)))
                    .isEqualTo(IntegerValue.TYPE);
            assertThat(answerOf(new TypeOfNative(), Set.of("word"), StringValue.of("a")))
                    .isEqualTo(WordValue.of("string!"));
        }

        @Test
        @DisplayName("a datatype's predicate is true of its own kind only")
        void aDatatypePredicate() {
            assertThat(answerOf(new DatatypePredicateAction(IntegerValue.TYPE),
                    IntegerValue.of(1)))
                    .isEqualTo(LogicValue.of(true));
            assertThat(answerOf(new DatatypePredicateAction(IntegerValue.TYPE),
                    DecimalValue.of(1.0)))
                    .isEqualTo(LogicValue.of(false));
        }

        @Test
        @DisplayName("zero? knows the zero of a pair and of a character")
        void zero() {
            assertThat(answerOf(new IsZeroNative(), PairValue.of(0, 0)))
                    .isEqualTo(LogicValue.of(true));
            assertThat(answerOf(new IsZeroNative(), CharacterValue.of(0)))
                    .isEqualTo(LogicValue.of(true));
            assertThat(answerOf(new IsZeroNative(), IntegerValue.of(1)))
                    .isEqualTo(LogicValue.of(false));
        }

        @Test
        @DisplayName("negative? and positive? need both halves of a pair")
        void negativeAndPositive() {
            assertThat(answerOf(new IsNegativeNative(), PairValue.of(-1, -1)))
                    .isEqualTo(LogicValue.of(true));
            assertThat(answerOf(new IsPositiveNative(), PairValue.of(1, -1)))
                    .isEqualTo(LogicValue.of(false));
        }

        @Test
        @DisplayName("ascii? stops at 127 and latin1? at 255")
        void codepointRanges() {
            assertThat(answerOf(new IsAsciiNative(), StringValue.of("abc")))
                    .isEqualTo(LogicValue.of(true));
            assertThat(answerOf(new IsAsciiNative(), CharacterValue.of(0x7F)))
                    .isEqualTo(LogicValue.of(true));
            assertThat(answerOf(new IsAsciiNative(), CharacterValue.of(0x80)))
                    .isEqualTo(LogicValue.of(false));
            assertThat(answerOf(new IsLatin1Native(), CharacterValue.of(0xFF)))
                    .isEqualTo(LogicValue.of(true));
            assertThat(answerOf(new IsLatin1Native(), CharacterValue.of(0x100)))
                    .isEqualTo(LogicValue.of(false));
        }

        @Test
        @DisplayName("did is false only of none and a false logic")
        void truth() {
            assertThat(answerOf(new DidNative(), NoneValue.none()))
                    .isEqualTo(LogicValue.of(false));
            assertThat(answerOf(new DidNative(), LogicValue.of(false)))
                    .isEqualTo(LogicValue.of(false));
            assertThat(answerOf(new DidNative(), IntegerValue.of(0)))
                    .isEqualTo(LogicValue.of(true));
        }

        @Test
        @DisplayName("number? is true of money and false of unset")
        void number() {
            assertThat(answerOf(new IsNumberNative(),
                    MoneyValue.of(BigDecimal.ONE)))
                    .isEqualTo(LogicValue.of(true));
            assertThat(answerOf(new IsNumberNative(), UnsetValue.unset()))
                    .isEqualTo(LogicValue.of(false));
        }
    }

    @Nested
    @DisplayName("the natives that read bytes")
    class Bytes {

        @Test
        @DisplayName("utf? reads the byte-order mark, and is zero without one")
        void byteOrderMarks() {
            assertThat(answerOf(new UtfNative(), BinaryValue.of(0xFE, 0xFF)))
                    .isEqualTo(IntegerValue.of(16));
            assertThat(answerOf(new UtfNative(), BinaryValue.of(0xEF, 0xBB, 0xBF)))
                    .isEqualTo(IntegerValue.of(8));
            assertThat(answerOf(new UtfNative(), BinaryValue.of()))
                    .isEqualTo(IntegerValue.of(0));
        }

        @Test
        @DisplayName("invalid-utf? stands at the first bad byte, and is none when all are good")
        void invalidUtf() {
            assertThat(answerOf(new InvalidUtfNative(), BinaryValue.of(0x41, 0xFF, 0x42)))
                    .isEqualTo(BinaryValue.of(0xFF, 0x42));
            assertThat(answerOf(new InvalidUtfNative(), BinaryValue.of(0x41, 0x42)))
                    .isEqualTo(NoneValue.none());
        }

        @Test
        @DisplayName("form-oid writes an object identifier in dotted form")
        void formOid() {
            assertThat(answerOf(new FormOidNative(),
                    BinaryValue.of(0x2A, 0x86, 0x48, 0x86, 0xF7, 0x0D)))
                    .isEqualTo(StringValue.of("1.2.840.113549"));
        }
    }
}
