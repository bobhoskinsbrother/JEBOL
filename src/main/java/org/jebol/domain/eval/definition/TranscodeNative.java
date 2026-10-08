package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.Arithmetic;
import org.jebol.domain.eval.Evaluator;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.read.SyntaxFailure;
import org.jebol.domain.read.Transcoder;
import org.jebol.domain.value.*;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class TranscodeNative extends DefaultNative {

    private static final long THE_FIRST_LINE_OF_ANY_SOURCE = 1;

    private record Reading(String whole, String readable, long firstLine,
            Transcoder.Extent extent, boolean countingLines, boolean handingBackFailures,
            boolean answeringTheValueAlone, Value source) {
    }

    @Override
    public String nativeName() {
        return "transcode";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(Parameter.required("source"),
                Parameter.belongingTo("line", "count", Set.of(Datatype.INTEGER)),
                Parameter.belongingTo("part", "length", Set.of(Datatype.INTEGER)));
    }

    @Override
    public Set<String> refinementsDeclaredApart() {
        return Set.of("one", "error", "next", "part", "line", "only");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) ->
                answer(readingAskedFor(arguments, refinements), evaluator);
    }

    private Reading readingAskedFor(List<Value> arguments, Set<String> refinements) {
        Value source = arguments.getFirst();
        String whole = textOf(source);
        String readable = argumentOf("part", 0, arguments, refinements)
                .map(asked -> boundedTo(whole, charactersPermittedBy(asked)))
                .orElse(whole);
        return new Reading(whole, readable,
                firstLineNumberIn(argumentOf("line", 0, arguments, refinements)),
                extentAskedFor(refinements),
                refinements.contains("line"),
                refinements.contains("error"),
                refinements.contains("one"),
                source);
    }

    private Value answer(Reading asked, Evaluator evaluator) {
        Transcoder.Reading reading = Transcoder.read(
                asked.readable(), asked.firstLine(), asked.extent(), evaluator.construction(),
                asked.handingBackFailures()
                        ? Transcoder.Errors.STAND_IN_FOR_THE_VALUE
                        : Transcoder.Errors.STOP_THE_READ);
        evaluator.symbols().internWhatWasRead(reading.valuesReadBeforeStopping());
        reading.whyItStopped().ifPresent(failure -> {
            throw new Raised(failure.error().orElseThrow());
        });
        List<Value> values = reading.valuesReadBeforeStopping().stream()
                .map(value -> madeWhereTheCallIs(value, evaluator))
                .toList();
        if (stopsBeforeTheEndOfTheSource(asked) && values.isEmpty()) {
            return handedBackOrRaised(asked, pastEnd());
        }
        if (asked.answeringTheValueAlone()) {
            return values.getFirst();
        }
        return BlockValue.block(stopsBeforeTheEndOfTheSource(asked)
                ? withWhatWasLeftUnread(asked, values, reading)
                : values);
    }

    private Value madeWhereTheCallIs(Value read, Evaluator evaluator) {
        switch (read) {
            case ErrorValue error -> {
                error.subject().ifPresent(inside -> madeWhereTheCallIs(inside, evaluator));
                return evaluator.madeWhereTheCallIs(error);
            }
            case BlockValue block -> {
                for (int at = 1; at <= block.storageLength(); at++) {
                    block.storage().set(at, madeWhereTheCallIs(block.storage().at(at), evaluator));
                }
                return block;
            }
            default -> {
                return read;
            }
        }
    }

    private boolean stopsBeforeTheEndOfTheSource(Reading asked) {
        return asked.extent() != Transcoder.Extent.THE_WHOLE_SOURCE
                || asked.handingBackFailures();
    }

    private List<Value> withWhatWasLeftUnread(
            Reading asked, List<Value> values, Transcoder.Reading reading) {
        List<Value> answer = new ArrayList<>(values);
        String whole = asked.whole();
        String left = skippingCodePoints(whole, reading.endedAtCodePoint());
        answer.add(switch (asked.source()) {
            case BinaryValue bytes -> bytes.atIndex(bytes.index()
                    + utf8LengthOf(whole) - utf8LengthOf(left));
            case StringValue text -> text.atIndex(text.index() + whole.length() - left.length());
            default -> StringValue.of(left);
        });
        if (asked.countingLines()) {
            answer.add(IntegerValue.of(reading.lineEndedOn()));
        }
        return answer;
    }

    private Value handedBackOrRaised(Reading asked, ErrorValue failure) {
        if (asked.handingBackFailures()) {
            return failure;
        }
        throw new Raised(failure);
    }

    private ErrorValue pastEnd() {
        return ErrorValue.of(SyntaxFailure.PAST_END.category(),
                SyntaxFailure.PAST_END.errorId(),
                SyntaxFailure.PAST_END.description());
    }

    private String textOf(Value source) {
        return switch (source) {
            case StringValue given -> given.text();
            case BinaryValue given -> given.asStrictText();
            default -> throw Raised.of(EvaluationFailure.EXPECT_ARG,
                    "transcode reads text, not " + source.datatype().literalSpelling());
        };
    }

    private Transcoder.Extent extentAskedFor(Set<String> refinements) {
        if (refinements.contains("only")) {
            return Transcoder.Extent.THE_FIRST_VALUE_AT_EVERY_DEPTH;
        }
        if (refinements.contains("next") || refinements.contains("one")) {
            return Transcoder.Extent.THE_FIRST_VALUE;
        }
        return Transcoder.Extent.THE_WHOLE_SOURCE;
    }

    private long firstLineNumberIn(Optional<Value> line) {
        if (line.isEmpty()) {
            return THE_FIRST_LINE_OF_ANY_SOURCE;
        }
        long asked = (long) Arithmetic.asMagnitude(line.get());
        if (asked < THE_FIRST_LINE_OF_ANY_SOURCE) {
            throw Raised.of(EvaluationFailure.OUT_OF_RANGE, line.get());
        }
        return asked;
    }

    private int charactersPermittedBy(Value part) {
        long asked = (long) Arithmetic.asMagnitude(part);
        if (asked < 0) {
            throw Raised.of(EvaluationFailure.OUT_OF_RANGE, part);
        }
        return (int) Math.min(asked, Integer.MAX_VALUE);
    }

    private String boundedTo(String whole, int charactersPermitted) {
        return whole.substring(0, Math.min(whole.length(), charactersPermitted));
    }

    private String skippingCodePoints(String whole, int howMany) {
        int[] codepoints = whole.codePoints().toArray();
        int taken = Math.min(howMany, codepoints.length);
        return new String(codepoints, taken, codepoints.length - taken);
    }

    private int utf8LengthOf(String text) {
        return text.getBytes(StandardCharsets.UTF_8).length;
    }
}
