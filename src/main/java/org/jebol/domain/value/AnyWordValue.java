package org.jebol.domain.value;

import java.util.List;
import java.util.Optional;

public abstract sealed class AnyWordValue implements Value
        permits WordValue, SetWordValue, GetWordValue, LitWordValue, RefinementValue, IssueValue {

    private final String spelling;
    private final String canonical;
    private final Context binding;

    AnyWordValue(String spelling, Context binding) {
        if (spelling == null || spelling.isEmpty()) {
            throw new IllegalArgumentException("a word needs a spelling");
        }
        if (binding == null) {
            throw new IllegalArgumentException(
                    "an unbound word carries Context.unbound(), never null");
        }
        this.spelling = spelling;
        this.canonical = Context.canonicalise(spelling);
        this.binding = binding;
    }

    @Override
    public abstract Datatype datatype();

    public abstract AnyWordValue bindTo(Context context);

    public abstract String mold();

    public String form() {
        return spelling;
    }

    public boolean looksUpItsDeclaration() {
        return false;
    }

    public ParameterKind kindOfParameterItDeclares() {
        return ParameterKind.NORMAL;
    }

    public abstract static class AnyWordDatatype extends Datatype {

        private static final String THE_PUNCTUATION_THAT_SPELLS_A_WORD_ALONE = "!%&*+-./<=>?^`|~";

        private static final int THE_FIRST_CODE_POINT_THE_SCANNER_TAKES_FOR_A_LETTER = 128;

        AnyWordDatatype(String spelling) {
            super(spelling);
        }

        public abstract AnyWordValue spell(String spelling, Context binding);

        public AnyWordValue spell(String spelling) {
            return spell(spelling, Context.unbound());
        }

        String asTheScannerReadsIt(String spelling) {
            return spelling;
        }

        Datatype findTheDatatypeTheScannerReads() {
            return WordValue.TYPE;
        }

        @Override
        protected void refuseToBuildSomethingOutOfNothing(Value from) {
        }

        @Override
        public Value construct(List<Value> contents, Construction construction) {
            throw refuseTheConstruction(contents);
        }

        @Override
        public Value as(Value value) {
            return value instanceof AnyWordValue word
                    ? spell(word.spelling(), word.binding())
                    : super.as(value);
        }

        @Override
        protected Value build(Conversion asking, Value from, Maker maker) {
            return switch (from) {
                case AnyWordValue word -> spell(word.spelling(), word.binding());
                case LogicValue(boolean truth) -> spell(Boolean.toString(truth));
                case CharacterValue letter -> spell(spellTheWordOfACharacter(letter));
                case AnyStringValue text -> spell(readTheSpellingFrom(text.text(), maker));
                case Datatype asked -> spell(readTheSpellingFrom(asked.literalSpelling(), maker));
                default -> throw Raised.of(EvaluationFailure.EXPECT_VAL, WordValue.TYPE, from.datatype());
            };
        }

        private String spellTheWordOfACharacter(CharacterValue letter) {
            if (!spellsAWordAlone(letter.codepoint())) {
                throw Raised.of(EvaluationFailure.BAD_CHAR, letter);
            }
            return new String(Character.toChars(letter.codepoint()));
        }

        private boolean spellsAWordAlone(int codepoint) {
            return codepoint >= THE_FIRST_CODE_POINT_THE_SCANNER_TAKES_FOR_A_LETTER
                    || Character.isLetter(codepoint)
                    || THE_PUNCTUATION_THAT_SPELLS_A_WORD_ALONE.indexOf(codepoint) >= 0;
        }

        private String readTheSpellingFrom(String text, Maker maker) {
            String trimmed = new WrittenText(text).findTheOneWord();
            List<Value> read;
            try {
                read = maker.readTheValuesFrom(asTheScannerReadsIt(trimmed)).orElse(List.of());
            } catch (RuntimeException unreadable) {
                throw Raised.of(EvaluationFailure.INVALID_CHARS);
            }
            if (read.size() != 1 || !(read.getFirst() instanceof AnyWordValue word)
                    || word.datatype() != findTheDatatypeTheScannerReads()
                    || !word.spelling().equals(trimmed)) {
                throw Raised.of(EvaluationFailure.INVALID_CHARS);
            }
            return word.spelling();
        }
    }

    public String spelling() {
        return spelling;
    }

    public String canonical() {
        return canonical;
    }

    public Context binding() {
        return binding;
    }

    @Override
    public boolean isEqualTo(Value other, Sameness how) {
        return other instanceof AnyWordValue theirs && namesSameAs(theirs);
    }

    @Override
    public Optional<Value[]> bringTogetherWith(Value other) {
        return other instanceof AnyWordValue ? pairUp(this, other) : Optional.empty();
    }

    @Override
    public void refuseToBeWrittenWhenItReachesItsContextsOwnSelf() {
        ContextSlot reached = binding.findTheSlotOrNone(canonical);
        if (reached != null && reached.isItsContextsOwnSelf()) {
            throw Raised.of(EvaluationFailure.SELF_PROTECTED);
        }
    }

    public boolean isBound() {
        return !binding.isUnbound();
    }

    public ContextSlot findTheBoundSlot() {
        ContextSlot slot = binding.findTheSlotOrNone(canonical);
        if (slot == null) {
            throw Raised.of(EvaluationFailure.NOT_DEFINED, spelling);
        }
        return slot;
    }

    public WordValue asWord() {
        return new WordValue(spelling, binding);
    }

    public SetWordValue asSetWord() {
        return new SetWordValue(spelling, binding);
    }

    public boolean namesSameAs(AnyWordValue other) {
        return canonical.equals(other.canonical);
    }

    public boolean isSameAs(AnyWordValue other) {
        return equals(other)
                && (other.binding == binding || shareAFunctionsFrames(other));
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof AnyWordValue word
                && word.datatype() == datatype()
                && word.spelling.equals(spelling);
    }

    @Override
    public int hashCode() {
        return datatype().hashCode() * 31 + spelling.hashCode();
    }

    @Override
    public String toString() {
        return mold();
    }

    private boolean shareAFunctionsFrames(AnyWordValue other) {
        Value ours = binding.functionOwningThisFrame();
        return ours != null && ours == other.binding.functionOwningThisFrame();
    }
}
