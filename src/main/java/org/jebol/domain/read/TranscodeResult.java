package org.jebol.domain.read;

import java.util.Optional;
import org.jebol.domain.value.BlockValue;
import org.jebol.domain.value.ErrorCategory;
import org.jebol.domain.value.ErrorValue;
import org.jebol.domain.value.NoneValue;
import org.jebol.domain.value.StringValue;
import org.jebol.domain.value.Value;

/**
 * What a whole-source read produced: either every value in the source, or an
 * error and no values.
 *
 * <p>A read that stops early on purpose -- TRANSCODE's /next, /only and
 * /error -- answers through {@link Transcoder.Reading} instead, which keeps
 * the values read before stopping alongside why it stopped.
 */
public sealed interface TranscodeResult {

    /** Whether the source was read in full. */
    boolean succeeded();

    /** The values read, present only on success. */
    Optional<BlockValue> values();

    /** The failure, present only on failure. */
    Optional<ErrorValue> error();

    /** Every value in the source, at the head of a fresh block. */
    record Success(BlockValue block) implements TranscodeResult {

        public Success {
            if (block == null) {
                throw new IllegalArgumentException("a successful read produced no block");
            }
        }

        @Override
        public boolean succeeded() {
            return true;
        }

        @Override
        public Optional<BlockValue> values() {
            return Optional.of(block);
        }

        @Override
        public Optional<ErrorValue> error() {
            return Optional.empty();
        }
    }

    /**
     * The first failure and where it was found. Reading reports one failure
     * and stops: a syntax error leaves no reliable place to resume, and a
     * second error guessed at from a bad position is worse than none.
     */
    record Failure(
            SyntaxFailure failure,
            SourcePosition position,
            Optional<OpenDelimiter> delimiterInvolved,
            Optional<String> tokenKind,
            Optional<String> fragment,
            Optional<String> offendingText) implements TranscodeResult {

        public Failure {
            if (failure == null || position == null || delimiterInvolved == null
                    || tokenKind == null || fragment == null || offendingText == null) {
                throw new IllegalArgumentException("a failure needs a reason and a position");
            }
        }

        /** The older three-field form, for a failure that names no token. */
        Failure(
                SyntaxFailure failure,
                SourcePosition position,
                Optional<OpenDelimiter> delimiterInvolved) {
            this(failure, position, delimiterInvolved,
                    Optional.empty(), Optional.empty(), Optional.empty());
        }

        /** The form that names a token and a line but not the offending text. */
        Failure(
                SyntaxFailure failure,
                SourcePosition position,
                Optional<OpenDelimiter> delimiterInvolved,
                Optional<String> tokenKind,
                Optional<String> fragment) {
            this(failure, position, delimiterInvolved, tokenKind, fragment, Optional.empty());
        }

        @Override
        public boolean succeeded() {
            return false;
        }

        @Override
        public Optional<BlockValue> values() {
            return Optional.empty();
        }

        /**
         * The failure as an error value, in the shape R3 gives one.
         *
         * <p>ARG1 names the kind of token the reader was building -- "word-lit",
         * "tag", "end-of-script" -- and ARG2 carries what it was reading or
         * what it wanted instead. NEAR is the line number and the source
         * fragment, written as R3 writes it: {@code (line 2) 1d}. A script
         * catching a syntax error reads those three rather than the message,
         * and Rebol's own suite asserts on all of them.
         */
        @Override
        public Optional<ErrorValue> error() {
            ErrorValue built = ErrorValue.of(
                    ErrorCategory.SYNTAX,
                    failure.errorId(),
                    failure.description() + " at " + position);
            if (tokenKind.isPresent()) {
                // ARG2 is the offending *token*, not the line. `Scan_Error` sets
                // the three from three different places:
                //
                //     Set_String(&error->nearest, "(line N) " + the whole line);
                //     Set_String(&error->arg1, the token's name);
                //     Set_String(&error->arg2, Copy_Bytes(arg, size));  // bp..ep
                //
                // Giving ARG2 the line put the same text in two fields and left
                // the one a script actually compares -- `e/arg2 = "$1*$2"` in
                // Rebol's money group -- with the wrong thing in it.
                built = ErrorValue.about(
                        ErrorCategory.SYNTAX,
                        failure.errorId(),
                        failure.description() + " at " + position,
                        StringValue.of(tokenKind.orElseThrow()),
                        offendingText.<Value>map(StringValue::of).orElseGet(NoneValue::none),
                        NoneValue.none());
            }
            return Optional.of(fragment.isPresent()
                    ? built.near(StringValue.of(
                            "(line " + position.line() + ") " + fragment.orElseThrow()))
                    : built);
        }
    }
}
