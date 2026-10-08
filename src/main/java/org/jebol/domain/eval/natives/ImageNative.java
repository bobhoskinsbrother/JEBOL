package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.Evaluator;
import org.jebol.domain.host.FilePort;
import org.jebol.domain.host.ImagePort;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.*;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public class ImageNative extends DefaultNative {

    private static final int THE_FIRST_FRAME = 1;

    @Override
    public String nativeName() {
        return "image";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(
                Parameter.belongingTo("load", "src-file", Set.of(Datatype.FILE, Datatype.BINARY)),
                Parameter.belongingTo("save", "dst-file",
                        Set.of(Datatype.NONE, Datatype.FILE, Datatype.BINARY)),
                Parameter.belongingTo("save", "dst-image", Set.of(Datatype.NONE, Datatype.IMAGE)),
                Parameter.belongingTo("frame", "num", Set.of(Datatype.INTEGER)),
                Parameter.belongingTo("as", "type", Set.of(Datatype.WORD)));
    }

    @Override
    public Set<String> refinementsDeclaredApart() {
        return Set.of("load", "save", "frame", "as");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            if (refinements.contains("load")) {
                return imageLoaded(arguments, evaluator, refinements);
            }
            if (refinements.contains("save")) {
                return imageSaved(arguments, evaluator, refinements);
            }
            return UnsetValue.unset();
        };
    }

    private String codecNamed(List<Value> arguments, Evaluator evaluator,
            Set<String> refinements) {

        Optional<Value> asked = argumentOf("as", 0, arguments, refinements);
        String type = asked.filter(WordValue.class::isInstance)
                .map(word -> ((WordValue) word).canonical())
                .orElse("");
        boolean theHostKnowsIt = evaluator.images().knows(type);
        if (asked.isPresent() && !theHostKnowsIt) {
            throw Raised.of(EvaluationFailure.BAD_FUNC_ARG, asked.get());
        }
        return type;
    }

    private Value imageLoaded(List<Value> arguments, Evaluator evaluator,
            Set<String> refinements) {

        String type = codecNamed(arguments, evaluator, refinements);
        Value source = argumentOf("load", 0, arguments, refinements)
                .orElseGet(NoneValue::none);
        int which = argumentOf("frame", 0, arguments, refinements)
                .filter(IntegerValue.class::isInstance)
                .map(frame -> (int) ((IntegerValue) frame).magnitude())
                .orElse(THE_FIRST_FRAME);
        return whatTheCodecMadeOf(source, type, which, evaluator)
                .map(this::imageOf)
                .orElseThrow(() -> source instanceof BinaryValue
                        ? Raised.of(EvaluationFailure.NO_CODEC, IntegerValue.of(0))
                        : Raised.of(EvaluationFailure.CANNOT_OPEN, source));
    }

    private Optional<ImagePort.Pixels> whatTheCodecMadeOf(
            Value source, String type, int frame, Evaluator evaluator) {

        byte[] encoded;
        try {
            encoded = source instanceof BinaryValue bytes
                    ? bytes.octetsFromHere()
                    : evaluator.files().readBytes(((AnyStringValue) source).text());
        } catch (FilePort.Denied unreadable) {
            return Optional.empty();
        }
        return Optional.ofNullable(evaluator.images().decoded(encoded, type, frame));
    }

    private Value imageOf(ImagePort.Pixels read) {
        ImageValue image = ImageValue.of(read.wide(), read.high());
        byte[] rgba = read.rgba();
        for (int pixel = 1; pixel <= read.wide() * read.high(); pixel++) {
            int at = (pixel - 1) * 4;
            image.storage().setColourAt(pixel,
                    rgba[at] & 0xFF, rgba[at + 1] & 0xFF, rgba[at + 2] & 0xFF);
            image.storage().setAlphaAt(pixel, rgba[at + 3] & 0xFF);
        }
        return image;
    }

    private Value imageSaved(List<Value> arguments, Evaluator evaluator,
            Set<String> refinements) {

        String type = codecNamed(arguments, evaluator, refinements);
        Value destination = argumentOf("save", 0, arguments, refinements)
                .orElseGet(NoneValue::none);
        Value given = argumentOf("save", 1, arguments, refinements)
                .orElseGet(NoneValue::none);
        if (!(given instanceof ImageValue image)) {
            throw Raised.of(EvaluationFailure.INVALID_ARG, Molder.mold(given));
        }
        byte[] written = evaluator.images().encoded(theWholeOf(image), type);
        if (written == null) {
            throw Raised.of(EvaluationFailure.NO_CODEC, IntegerValue.of(0));
        }
        if (destination instanceof FileValue address) {
            evaluator.files().write(address.text(), written);
            return destination;
        }
        if (destination instanceof BinaryValue holding) {
            return filledWithTheEncodedBytes(holding, written);
        }
        return BinaryValue.ofBytes(written);
    }

    private Value filledWithTheEncodedBytes(BinaryValue destination, byte[] written) {
        BinaryStorage storage = destination.storage();
        while (storage.length() >= destination.index()) {
            storage.removeAt(destination.index());
        }
        for (byte octet : written) {
            storage.append(octet & 0xFF);
        }
        return destination;
    }

    private ImagePort.Pixels theWholeOf(ImageValue image) {
        return new ImagePort.Pixels(image.storage().wide(), image.storage().high(),
                image.head().everyPixel());
    }
}
