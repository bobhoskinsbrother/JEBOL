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

    /**
     * REBOL's {@code same?}: equal, and bound to the same context.
     *
     * <p>Or to two frames of one function, which is not the same condition
     * and is the whole of {@code VAL_WORD_FRAME}. The C binds a function's
     * body once, when the function is made, and the frame it writes into
     * every word there is the function's own parameter list rather than any
     * one call's frame. So a word the body wrote is the same word on every
     * call, however many calls are alive at the time. Here a body is bound to
     * the frame of the call running it, which is a different object each
     * time, so the question has to be asked of what those frames belong to.
     *
     * <p>Rebol's ARRAY turns on this. It hands itself {@code 'tag} as a token
     * saying the call came from inside, and the guard that reads the token is
     * {@code unless same? :tag 'tag} -- one word from the caller's frame
     * against the same word from the callee's. Compare the frames themselves
     * and the token never matches, so ARRAY drops the indexes it was passing
     * down and {@code array/initial [2 2] func [x y] [...]} builds every row
     * from the same pair of numbers.
     *
     * <p>A closure is bound to a real object for each call and is marked as
     * no function's frame, so two closures' words stay two words. That is
     * what the C does as well, because a closure's body is bound for real
     * rather than relatively.
     */
    public boolean isSameAs(WordValue other) {
        return equals(other)
                && (other.binding == binding || shareAFunctionsFrames(other));
    }

    private boolean shareAFunctionsFrames(WordValue other) {
        Value ours = binding.functionOwningThisFrame();
        return ours != null && ours == other.binding.functionOwningThisFrame();
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
