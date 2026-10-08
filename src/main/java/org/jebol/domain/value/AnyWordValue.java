package org.jebol.domain.value;

import java.util.List;
import java.util.Optional;

public abstract sealed class AnyWordValue implements Value
        permits WordValue, SetWordValue, GetWordValue, LitWordValue, RefinementValue, IssueValue {

    private static final String THE_NAME_AN_OBJECT_ANSWERS_TO_ITSELF_BY = "self";

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

    public abstract AnyWordValue boundTo(Context context);

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
            super(spelling, Typeset.ANY_WORD);
        }

        public abstract AnyWordValue spelt(String spelling, Context binding);

        public AnyWordValue spelt(String spelling) {
            return spelt(spelling, Context.unbound());
        }

        String asTheScannerReadsIt(String spelling) {
            return spelling;
        }

        Datatype theDatatypeTheScannerReadsItAs() {
            return WordValue.TYPE;
        }

        @Override
        protected void refuseToBuildSomethingOutOfNothing(Value from) {
        }

        @Override
        public Value constructedFrom(List<Value> contents, Construction construction) {
            throw refusingConstruction(contents);
        }

        @Override
        public Value as(Value value) {
            return value instanceof AnyWordValue word
                    ? spelt(word.spelling(), word.binding())
                    : super.as(value);
        }

        @Override
        protected Value built(Conversion asking, Value from, Maker maker) {
            return switch (from) {
                case AnyWordValue word -> spelt(word.spelling());
                case LogicValue(boolean truth) -> spelt(Boolean.toString(truth));
                case CharacterValue letter -> spelt(theWordASingleCharacterSpells(letter));
                case AnyStringValue text -> spelt(spellingReadFrom(text.text(), maker));
                case Datatype asked -> spelt(spellingReadFrom(asked.literalSpelling(), maker));
                default -> throw Raised.of(EvaluationFailure.EXPECT_VAL, WordValue.TYPE, from.datatype());
            };
        }

        private String theWordASingleCharacterSpells(CharacterValue letter) {
            if (!spellsAWordAlone(letter.codepoint())) {
                throw Raised.of(EvaluationFailure.BAD_CHAR, letter);
            }
            return Character.toString(letter.codepoint());
        }

        private boolean spellsAWordAlone(int codepoint) {
            return codepoint >= THE_FIRST_CODE_POINT_THE_SCANNER_TAKES_FOR_A_LETTER
                    || Character.isLetter(codepoint)
                    || THE_PUNCTUATION_THAT_SPELLS_A_WORD_ALONE.indexOf(codepoint) >= 0;
        }

        private String spellingReadFrom(String text, Maker maker) {
            String trimmed = new WrittenText(text).theOneWordIn();
            List<Value> read;
            try {
                read = maker.valuesReadFrom(asTheScannerReadsIt(trimmed)).orElse(List.of());
            } catch (RuntimeException unreadable) {
                throw Raised.of(EvaluationFailure.INVALID_CHARS);
            }
            if (read.size() != 1 || !(read.getFirst() instanceof AnyWordValue word)
                    || word.datatype() != theDatatypeTheScannerReadsItAs()
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
    public boolean equalTo(Value other, Sameness how) {
        return other instanceof AnyWordValue theirs && namesSameAs(theirs);
    }

    @Override
    public Optional<Value[]> broughtTogetherWith(Value other) {
        return other instanceof AnyWordValue ? both(this, other) : Optional.empty();
    }

    @Override
    public void refuseToBeWrittenWhenItNamesSelf() {
        if (canonical.equals(THE_NAME_AN_OBJECT_ANSWERS_TO_ITSELF_BY)) {
            throw Raised.of(EvaluationFailure.SELF_PROTECTED);
        }
    }

    public boolean isBound() {
        return !binding.isUnbound();
    }

    public ContextSlot boundSlot() {
        if (!isBound() || !binding.knows(canonical)) {
            throw Raised.of(EvaluationFailure.NOT_DEFINED, spelling);
        }
        return binding.slotFor(canonical);
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
