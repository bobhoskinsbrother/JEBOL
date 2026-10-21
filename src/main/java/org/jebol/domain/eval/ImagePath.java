package org.jebol.domain.eval;

import org.jebol.domain.value.*;

final class ImagePath {

    private ImagePath() {
    }

    static Value read(ImageValue image, Value selector) {
        if (selector instanceof AnyWordValue field) {
            return describeTheImage(image, field);
        }
        int pixel = findPixelNamedBy(image, selector);
        if (pixel < 1 || pixel > image.lengthFromHere()) {
            return NoneValue.none();
        }
        int[] channels = image.readPixelAt(pixel);
        return makeFourParts(channels);
    }

    private static TupleValue makeFourParts(int[] channels) {
        return TupleValue.of(channels[0], channels[1], channels[2], channels[3]);
    }

    static void write(ImageValue image, int pixelFromHead, Value written) {
        ImageStorage storage = image.storage();
        if (pixelFromHead < 1 || pixelFromHead > storage.length()) {
            throw Raised.of(EvaluationFailure.BAD_PATH_SET,
                    "pixel " + pixelFromHead + " is outside the image");
        }
        if (written instanceof TupleValue colour) {
            int[] parts = colour.segments();
            storage.setColourAt(pixelFromHead,
                    partOr(parts, 0, 0), partOr(parts, 1, 0), partOr(parts, 2, 0));
            storage.setAlphaAt(pixelFromHead, partOr(parts, 3, OPAQUE_WHERE_NO_FOURTH_PART));
            return;
        }
        int alpha = readAlphaFrom(written);
        storage.setAlphaAt(pixelFromHead, alpha);
    }

    static void writeThroughPath(ImageValue image, Value selector, Value written) {
        if (selector instanceof AnyWordValue field) {
            writeWholePicture(image, field, written);
            return;
        }
        int pixel = findPixelNamedBy(image, selector);
        if (pixel < 1 || pixel > image.lengthFromHere()) {
            throw Raised.of(EvaluationFailure.BAD_PATH_SET,
                    "pixel " + pixel + " is outside the image");
        }
        write(image, image.index() + pixel - 1, written);
    }

    private static void writeWholePicture(
            ImageValue image, AnyWordValue field, Value written) {

        switch (field.canonical()) {
            case "size" -> reshapeTo(image, written);
            case "rgb", "color" -> fillEveryPixel(image, written, 0, 1, 2);
            case "rgba", "rgbo" -> fillEveryPixel(image, written, 0, 1, 2, 3);
            case "bgra", "bgro" -> fillEveryPixel(image, written, 2, 1, 0, 3);
            case "argb", "orgb" -> fillEveryPixel(image, written, 3, 0, 1, 2);
            case "abgr", "obgr" -> fillEveryPixel(image, written, 3, 2, 1, 0);
            case "luminosity", "gray" -> fillTheSameByteEverywhere(image, written);
            case "alpha" -> fillOneChannel(image, written, 4);
            case "opacity" -> fillTheOpacity(image, written);
            case "red" -> fillOneChannel(image, written, 1);
            case "green" -> fillOneChannel(image, written, 2);
            case "blue" -> fillOneChannel(image, written, 3);
            default -> throw Raised.of(EvaluationFailure.INVALID_PATH, field.spelling());
        }
    }

    private static void reshapeTo(ImageValue image, Value written) {
        if (!(written instanceof PairValue asked) || (int) asked.x() == 0) {
            throw Raised.of(EvaluationFailure.BAD_PATH_SET,
                    "an image is sized by a pair with a width");
        }
        int across = (int) asked.x();
        image.storage().reshape(across, countTheWholeRowsThePixelsMake(image, asked, across));
    }

    private static int countTheWholeRowsThePixelsMake(
            ImageValue image, PairValue asked, int across) {
        return Math.min((int) asked.y(), image.storage().length() / across);
    }

    private static void fillEveryPixel(
            ImageValue image, Value written, int... order) {

        if (readAPicturesWorthOfBytes(written) instanceof int[] laid) {
            layOver(image, laid, order);
            return;
        }
        int[] parts = written instanceof TupleValue colour
                ? colour.segments()
                : makeGreyParts(readAByteOrRefuse(written), order.length);
        for (int pixel = image.index(); pixel <= image.storageLength(); pixel++) {
            image.storage().setColourAt(pixel,
                    partOr(parts, 0, 0), partOr(parts, 1, 0), partOr(parts, 2, 0));
            if (order.length > 3) {
                image.storage().setAlphaAt(pixel, partOr(parts, 3, 0xFF));
            }
        }
    }

    private static int stopAtWhicheverRunsOutFirst(int given, int pixelsLeft) {
        return Math.min(given, pixelsLeft);
    }

    private static int[] makeGreyParts(int grey, int channels) {
        return channels > 3
                ? new int[] {grey, grey, grey, grey}
                : new int[] {grey, grey, grey};
    }

    private static void fillTheSameByteEverywhere(ImageValue image, Value written) {
        if (readAPicturesWorthOfBytes(written) instanceof int[] greys) {
            int pixels = stopAtWhicheverRunsOutFirst(
                    greys.length, image.lengthFromHere());
            for (int step = 0; step < pixels; step++) {
                image.storage().setColourAt(image.index() + step,
                        greys[step], greys[step], greys[step]);
            }
            return;
        }
        int grey = readAByteOrRefuse(written);
        for (int pixel = image.index(); pixel <= image.storageLength(); pixel++) {
            image.storage().setColourAt(pixel, grey, grey, grey);
            image.storage().setAlphaAt(pixel, grey);
        }
    }

    private static void fillTheOpacity(ImageValue image, Value written) {
        if (readAPicturesWorthOfBytes(written) instanceof int[] laid) {
            int pixels = stopAtWhicheverRunsOutFirst(
                    laid.length, image.lengthFromHere());
            for (int step = 0; step < pixels; step++) {
                image.storage().setAlphaAt(image.index() + step,
                        convertTheAlphaToOpacity(laid[step]));
            }
            return;
        }
        int octet = readAByteOrRefuse(written);
        for (int pixel = image.index(); pixel <= image.storageLength(); pixel++) {
            image.storage().setAlphaAt(pixel, convertTheAlphaToOpacity(octet));
        }
    }

    private static void fillOneChannel(ImageValue image, Value written, int channel) {
        if (readAPicturesWorthOfBytes(written) instanceof int[] laid) {
            int pixels = stopAtWhicheverRunsOutFirst(
                    laid.length, image.lengthFromHere());
            for (int step = 0; step < pixels; step++) {
                image.storage().setChannelAt(image.index() + step, channel, laid[step]);
            }
            return;
        }
        int octet = readAByteOrRefuse(written);
        for (int pixel = image.index(); pixel <= image.storageLength(); pixel++) {
            image.storage().setChannelAt(pixel, channel, octet);
        }
    }

    private static void layOver(ImageValue image, int[] laid, int[] order) {
        int pixels = stopAtWhicheverRunsOutFirst(
                laid.length / order.length, image.lengthFromHere());
        for (int step = 0; step < pixels; step++) {
            for (int which = 0; which < order.length; which++) {
                image.storage().setChannelAt(image.index() + step,
                        order[which] + 1, laid[step * order.length + which]);
            }
        }
    }

    private static int[] readAPicturesWorthOfBytes(Value written) {
        if (written instanceof BinaryValue binary) {
            int[] octets = new int[binary.lengthFromHere()];
            for (int at = 0; at < octets.length; at++) {
                octets[at] = binary.storage().at(binary.index() + at);
            }
            return octets;
        }
        if (written instanceof VectorValue vector && vector.kind().bytes() == 1) {
            java.util.List<Value> numbers = vector.listRemaining();
            int[] octets = new int[numbers.size()];
            for (int at = 0; at < octets.length; at++) {
                octets[at] = numbers.get(at) instanceof IntegerValue(long magnitude)
                        ? (int) magnitude & 0xFF
                        : 0;
            }
            return octets;
        }
        return null;
    }

    private static int readAByteOrRefuse(Value written) {
        if (!(written instanceof IntegerValue(long magnitude))) {
            throw Raised.of(EvaluationFailure.BAD_PATH_SET,
                    "a whole picture is filled from a tuple or a number");
        }
        if (magnitude < 0 || magnitude > 255) {
            throw Raised.of(EvaluationFailure.OUT_OF_RANGE,
                    "a colour byte holds 0 to 255");
        }
        return (int) magnitude;
    }

    static void writeOneChannel(
            ImageValue image, Value pixelSegment, int channel, Value written) {
        if (channel < 1 || channel > 4) {
            throw Raised.of(EvaluationFailure.BAD_PATH_SET,
                    "a pixel has four bytes, so " + channel + " names none of them");
        }
        if (!(written instanceof IntegerValue(long magnitude))
                || magnitude < 0 || magnitude > 255) {
            throw Raised.of(EvaluationFailure.BAD_PATH_SET,
                    "one byte of a pixel holds 0 to 255");
        }
        int pixel = findPixelNamedBy(image, pixelSegment);
        if (pixel < 1 || pixel > image.lengthFromHere()) {
            throw Raised.of(EvaluationFailure.BAD_PATH_SET,
                    "pixel " + pixel + " is outside the image");
        }
        image.storage().setChannelAt(image.index() + pixel - 1, channel,
                (int) magnitude);
    }

    private static int findPixelNamedBy(ImageValue image, Value selector) {
        return switch (selector) {
            case PairValue coordinate -> ((int) coordinate.y() - 1) * image.storage().wide()
                    + ((int) coordinate.x() - 1) + 1;
            case IntegerValue number -> (int) number.magnitude();
            case AnyDecimalValue number -> (int) number.quantity();
            case LogicValue yesOrNo -> yesOrNo.isTruthy() ? 1 : 2;
            default -> 0;
        };
    }

    private static int partOr(int[] parts, int at, int otherwise) {
        return at < parts.length ? parts[at] : otherwise;
    }

    private static int readAlphaFrom(Value written) {
        long octet = switch (written) {
            case IntegerValue number -> number.magnitude();
            case CharacterValue letter -> letter.codepoint();
            default -> -1;
        };
        if (octet < 0 || octet > 255) {
            throw Raised.of(EvaluationFailure.INVALID_ARG,
                    "a pixel takes a colour tuple or an alpha byte, not "
                            + written.datatype().literalSpelling());
        }
        return (int) octet;
    }

    private static Value describeTheImage(ImageValue image, AnyWordValue field) {
        return switch (field.canonical()) {
            case "size" -> image.size();
            case "width" -> IntegerValue.of(image.storage().wide());
            case "height" -> IntegerValue.of(image.storage().high());
            case "rgb" -> readChannels(image, 0, 1, 2);
            case "bgr" -> readChannels(image, 2, 1, 0);
            case "rgba", "rgbo" -> readChannels(image, 0, 1, 2, 3);
            case "bgra", "bgro" -> readChannels(image, 2, 1, 0, 3);
            case "argb", "orgb" -> readChannels(image, 3, 0, 1, 2);
            case "abgr", "obgr" -> readChannels(image, 3, 2, 1, 0);
            case "alpha" -> readChannels(image, 3);
            case "opacity" -> readTheOpacitiesOf(image);
            case "red" -> readChannels(image, 0);
            case "green" -> readChannels(image, 1);
            case "blue" -> readChannels(image, 2);
            case "color" -> averageTheColourOf(image);
            case "luminosity" -> squeezeToOneBytePerPixel(image, ImagePath::measureTheLuminosityOf);
            case "gray" -> squeezeToOneBytePerPixel(image, ImagePath::measureTheGreyOf);
            default -> throw Raised.of(EvaluationFailure.INVALID_PATH, field.spelling());
        };
    }

    private static Value averageTheColourOf(ImageValue image) {
        int pixels = image.lengthFromHere();
        if (pixels == 0) {
            return TRANSPARENT_BLACK;
        }
        long red = 0;
        long green = 0;
        long blue = 0;
        long alpha = 0;
        for (int pixel = 1; pixel <= pixels; pixel++) {
            int[] channels = image.readPixelAt(pixel);
            red += channels[0];
            green += channels[1];
            blue += channels[2];
            alpha += channels[3];
        }
        return TupleValue.of((int) (red / pixels), (int) (green / pixels),
                (int) (blue / pixels), (int) (alpha / pixels));
    }

    private static final int OPAQUE_WHERE_NO_FOURTH_PART = 255;

    private static final TupleValue TRANSPARENT_BLACK = TupleValue.of(0, 0, 0, 0);

    private static BinaryValue readTheOpacitiesOf(ImageValue image) {
        int pixels = image.lengthFromHere();
        int[] octets = new int[pixels];
        for (int pixel = 1; pixel <= pixels; pixel++) {
            octets[pixel - 1] = convertTheAlphaToOpacity(image.readPixelAt(pixel)[3]);
        }
        return new BinaryValue(BinaryStorage.of(octets), 1);
    }

    private static int convertTheAlphaToOpacity(int alpha) {
        return 255 - alpha;
    }

    private static int measureTheLuminosityOf(int red, int green, int blue) {
        return (int) (0.2126 * red + 0.7152 * green + 0.0722 * blue);
    }

    private static int measureTheGreyOf(int red, int green, int blue) {
        return (red + green + blue) / 3;
    }

    private static BinaryValue squeezeToOneBytePerPixel(
            ImageValue image, ThreeChannelsToOne squeeze) {

        int pixels = image.lengthFromHere();
        int[] octets = new int[pixels];
        for (int pixel = 1; pixel <= pixels; pixel++) {
            int[] channels = image.readPixelAt(pixel);
            octets[pixel - 1] = squeeze.of(channels[0], channels[1], channels[2]);
        }
        return new BinaryValue(BinaryStorage.of(octets), 1);
    }

    @FunctionalInterface
    private interface ThreeChannelsToOne {
        int of(int red, int green, int blue);
    }

    private static BinaryValue readChannels(ImageValue image, int... order) {
        int pixels = image.lengthFromHere();
        int[] octets = new int[pixels * order.length];
        int written = 0;
        for (int pixel = 1; pixel <= pixels; pixel++) {
            int[] channels = image.readPixelAt(pixel);
            for (int which : order) {
                octets[written++] = channels[which];
            }
        }
        return new BinaryValue(BinaryStorage.of(octets), 1);
    }
}
