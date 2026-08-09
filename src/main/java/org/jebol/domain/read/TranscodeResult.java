package org.jebol.domain.read;

import java.util.Optional;
import org.jebol.domain.value.BlockValue;
import org.jebol.domain.value.ErrorCategory;
import org.jebol.domain.value.ErrorValue;

/**
 * What reading produced: either every value in the source, or an error and no
 * values.
 *
 * <p>There is deliberately no third case. A read that handed back some of the
 * source alongside an error would let a caller run half a script.
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
            Optional<OpenDelimiter> unclosedDelimiter) implements TranscodeResult {

        public Failure {
            if (failure == null || position == null || unclosedDelimiter == null) {
                throw new IllegalArgumentException("a failure needs a reason and a position");
            }
        }

        @Override
        public boolean succeeded() {
            return false;
        }

        @Override
        public Optional<BlockValue> values() {
            return Optional.empty();
        }

        @Override
        public Optional<ErrorValue> error() {
            return Optional.of(ErrorValue.of(
                    ErrorCategory.SYNTAX,
                    failure.errorId(),
                    failure.description() + " at " + position));
        }
    }
}
