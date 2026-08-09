package org.jebol.domain.value;

/**
 * A word in one of its six shapes: {@code word}, {@code word:}, {@code :word},
 * {@code 'word}, {@code /word} or {@code #word}.
 *
 * <p>Case is preserved for printing and discarded for comparison, so
 * {@code Print} and {@code print} are the same word written differently.
 *
 * <p>The binding is a {@link Context}, using {@link Context#unbound()} rather
 * than null when the word names nothing yet. That keeps the distinction the
 * evaluator needs: a word with no binding reports {@code not-defined}, and a
 * word bound to a slot holding {@code unset} reports {@code no-value}. They
 * are different mistakes and they need different messages.
 */
public record WordValue(String spelling, String canonical, Context binding, Datatype datatype)
        implements Value {

    public WordValue {
        if (spelling == null || spelling.isEmpty()) {
            throw new IllegalArgumentException("a word needs a spelling");
        }
        if (binding == null) {
            throw new IllegalArgumentException(
                    "an unbound word carries Context.unbound(), never null");
        }
        if (!datatype.isAnyWord()) {
            throw new IllegalArgumentException(
                    datatype.literalSpelling() + " is not an any-word! datatype");
        }
        if (!canonical.equals(Context.canonicalise(spelling))) {
            throw new IllegalArgumentException(
                    "canonical \"" + canonical + "\" does not match spelling \""
                            + spelling + "\"");
        }
    }

    public static WordValue of(String spelling) {
        return of(spelling, Datatype.WORD);
    }

    public static WordValue of(String spelling, Datatype datatype) {
        return new WordValue(
                spelling, Context.canonicalise(spelling), Context.unbound(), datatype);
    }

    public boolean isBound() {
        return !binding.isUnbound();
    }

    /** The same word, bound to a context. */
    public WordValue boundTo(Context context) {
        return new WordValue(spelling, canonical, context, datatype);
    }

    /** The same word, in a different shape. {@code 'foo} becomes {@code foo}. */
    public WordValue as(Datatype otherDatatype) {
        return new WordValue(spelling, canonical, binding, otherDatatype);
    }

    /** Whether two words name the same thing, ignoring case and shape. */
    public boolean namesSameAs(WordValue other) {
        return canonical.equals(other.canonical);
    }

    /**
     * REBOL's {@code ==}: same shape, same spelling, case sensitive.
     * <strong>Binding is not part of it.</strong>
     *
     * <p>From the REBOL bindology reference: two words are the <em>same</em>
     * if and only if they have strict equal spelling and equal binding, while
     * equal words need not have equal binding. So equality asks what a word
     * says and {@link #isSameAs} asks which word it is.
     *
     * <p>This matters beyond pedantry. MOLD does not print bindings, because
     * a binding is not syntax. If equality counted binding then no bound block
     * could survive a round trip through MOLD, and blocks are bound the moment
     * they are about to be evaluated.
     */
    @Override
    public boolean equals(Object other) {
        return other instanceof WordValue word
                && word.datatype == datatype
                && word.spelling.equals(spelling);
    }

    @Override
    public int hashCode() {
        return datatype.hashCode() * 31 + spelling.hashCode();
    }

    /** REBOL's {@code same?}: equal, and bound to the same context. */
    public boolean isSameAs(WordValue other) {
        return equals(other) && other.binding == binding;
    }

    @Override
    public String toString() {
        return switch (datatype) {
            case SET_WORD -> spelling + ":";
            case GET_WORD -> ":" + spelling;
            case LIT_WORD -> "'" + spelling;
            case REFINEMENT -> "/" + spelling;
            case ISSUE -> "#" + spelling;
            default -> spelling;
        };
    }
}
