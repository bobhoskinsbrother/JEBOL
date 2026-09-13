package org.jebol.domain.read;

import org.jebol.domain.value.*;

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

        Failure(
                SyntaxFailure failure,
                SourcePosition position,
                Optional<OpenDelimiter> delimiterInvolved) {
            this(failure, position, delimiterInvolved,
                    Optional.empty(), Optional.empty(), Optional.empty());
        }

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

        @Override
        public Optional<ErrorValue> error() {
            ErrorValue built = ErrorValue.of(
                    failure.category(),
                    failure.errorId(),
                    failure.description() + " at " + position);
            if (tokenKind.isPresent()) {
                Value theKindOfTokenTheReaderWasBuilding =
                        StringValue.of(tokenKind.orElseThrow());
                Value whatItWasReadingOrWantedInstead =
                        offendingText.<Value>map(StringValue::of).orElseGet(NoneValue::none);
                built = ErrorValue.about(
                        failure.category(),
                        failure.errorId(),
                        failure.description() + " at " + position,
                        theKindOfTokenTheReaderWasBuilding,
                        whatItWasReadingOrWantedInstead,
                        NoneValue.none());
            }
            return Optional.of(fragment.isPresent()
                    ? built.near(theLineAndFragmentWrittenAsRebolWritesThem())
                    : built);
        }

        private StringValue theLineAndFragmentWrittenAsRebolWritesThem() {
            return StringValue.of(
                    "(line " + position.line() + ") " + fragment.orElseThrow());
        }
    }
}
