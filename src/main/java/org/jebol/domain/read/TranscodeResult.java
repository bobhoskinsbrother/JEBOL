package org.jebol.domain.read;

import org.jebol.domain.value.*;

import java.util.List;
import java.util.Optional;

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
    record Failure(SyntaxFailure failure, List<Value> arguments, Optional<String> near)
            implements TranscodeResult {

        public Failure {
            if (failure == null || arguments == null || near == null) {
                throw new IllegalArgumentException("a failure needs a reason, its arguments and where it was");
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
            ErrorValue built = switch (arguments.size()) {
                case 0 -> ErrorValue.of(failure.category(), failure.errorId(), failure.description());
                case 1 -> ErrorValue.about(failure.category(), failure.errorId(), failure.description(),
                        arguments.getFirst());
                default -> ErrorValue.about(failure.category(), failure.errorId(), failure.description(),
                        arguments.get(0), arguments.get(1));
            };
            return Optional.of(near.map(written -> built.near(StringValue.of(written))).orElse(built));
        }
    }
}
