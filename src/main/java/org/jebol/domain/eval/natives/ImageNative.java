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

    private static final int FIRST_FRAME = 1;

    private static final String CODEC_NOBODY_NAMED_IS = "png";

    @Override
    public String nativeName() {
        return "image";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(
                Parameter.belongTo("load", "src-file", Set.of(FileValue.TYPE, BinaryValue.TYPE)),
                Parameter.belongTo("save", "dst-file",
                        Set.of(NoneValue.TYPE, FileValue.TYPE, BinaryValue.TYPE)),
                Parameter.belongTo("save", "dst-image", Set.of(NoneValue.TYPE, ImageValue.TYPE)),
                Parameter.belongTo("frame", "num", Set.of(IntegerValue.TYPE)),
                Parameter.belongTo("as", "type", Set.of(WordValue.TYPE)));
    }

    @Override
    public Set<String> refinementsDeclaredApart() {
        return Set.of("load", "save", "frame", "as");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            if (refinements.contains("load")) {
                return loadImage(arguments, evaluator, refinements);
            }
            if (refinements.contains("save")) {
                return saveImage(arguments, evaluator, refinements);
            }
            return UnsetValue.unset();
        };
    }

    private String readTheCodecName(List<Value> arguments, Evaluator evaluator,
                                    Set<String> refinements) {

        Optional<Value> asked = readTheArgumentOf("as", 0, arguments, refinements);
        String type = asked.filter(AnyWordValue.class::isInstance)
                .map(word -> ((AnyWordValue) word).canonical())
                .orElse("");
        boolean theHostKnowsIt = evaluator.ports().images().hasImageType(type);
        if (asked.isPresent() && !theHostKnowsIt) {
            throw Raised.of(EvaluationFailure.BAD_FUNC_ARG, asked.get());
        }
        return type;
    }

    private Value loadImage(List<Value> arguments, Evaluator evaluator,
                            Set<String> refinements) {

        String type = readTheCodecName(arguments, evaluator, refinements);
        Value source = readTheArgumentOf("load", 0, arguments, refinements)
                .orElseGet(NoneValue::none);
        int which = readTheArgumentOf("frame", 0, arguments, refinements)
                .filter(IntegerValue.class::isInstance)
                .map(frame -> (int) ((IntegerValue) frame).magnitude())
                .orElse(FIRST_FRAME);
        return decodeWithTheCodec(source, type, which, evaluator)
                .map(this::makeAnImageOf)
                .orElseThrow(() -> source instanceof BinaryValue
                        ? Raised.of(EvaluationFailure.NO_CODEC, IntegerValue.of(0))
                        : Raised.of(EvaluationFailure.CANNOT_OPEN, source));
    }

    private Optional<ImagePort.Pixels> decodeWithTheCodec(
            Value source, String type, int frame, Evaluator evaluator) {

        byte[] encoded;
        try {
            encoded = source instanceof BinaryValue bytes
                    ? bytes.octetsFromHere()
                    : evaluator.ports().files().readBytes(((AnyStringValue) source).text());
        } catch (FilePort.Denied unreadable) {
            return Optional.empty();
        }
        return Optional.ofNullable(evaluator.ports().images().decode(encoded, type, frame));
    }

    private Value makeAnImageOf(ImagePort.Pixels read) {
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

    private Value saveImage(List<Value> arguments, Evaluator evaluator,
                            Set<String> refinements) {

        String type = Optional.of(readTheCodecName(arguments, evaluator, refinements))
                .filter(named -> !named.isEmpty())
                .orElse(CODEC_NOBODY_NAMED_IS);
        Value destination = readTheArgumentOf("save", 0, arguments, refinements)
                .orElseGet(NoneValue::none);
        Value given = readTheArgumentOf("save", 1, arguments, refinements)
                .orElseGet(NoneValue::none);
        if (!(given instanceof ImageValue image)) {
            throw Raised.of(EvaluationFailure.INVALID_ARG, Molder.mold(given));
        }
        byte[] written = evaluator.ports().images().encode(readTheWholeOf(image), type);
        if (written == null) {
            throw Raised.of(EvaluationFailure.NO_CODEC, IntegerValue.of(0));
        }
        if (destination instanceof FileValue address) {
            evaluator.ports().files().write(address.text(), written);
            return destination;
        }
        if (destination instanceof BinaryValue holding) {
            return fillWithEncodedBytes(holding, written);
        }
        return BinaryValue.ofBytes(written);
    }

    private Value fillWithEncodedBytes(BinaryValue destination, byte[] written) {
        BinaryStorage storage = destination.storage();
        while (storage.length() >= destination.index()) {
            storage.removeAt(destination.index());
        }
        for (byte octet : written) {
            storage.append(octet & 0xFF);
        }
        return destination;
    }

    private ImagePort.Pixels readTheWholeOf(ImageValue image) {
        return new ImagePort.Pixels(image.storage().wide(), image.storage().high(),
                image.head().copyEveryPixel());
    }
}
