package org.jebol.domain.eval;

import org.jebol.domain.value.*;

final class ImagePath {

    private ImagePath() {
    }

    static Value read(ImageValue image, Value selector) {
        if (selector instanceof WordValue field) {
            return aboutTheImage(image, field);
        }
        int pixel = pixelNamedBy(image, selector);
        if (pixel < 1 || pixel > image.lengthFromHere()) {
            return NoneValue.none();
        }
        int[] channels = image.pixelAt(pixel);
        return alwaysFourParts(channels);
    }

    private static TupleValue alwaysFourParts(int[] channels) {
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
        int alpha = alphaFrom(written);
        storage.setAlphaAt(pixelFromHead, alpha);
    }

    static void writeThroughPath(ImageValue image, Value selector, Value written) {
        if (selector instanceof WordValue field) {
            writeTheWholePicture(image, field, written);
            return;
        }
        int pixel = pixelNamedBy(image, selector);
        if (pixel < 1 || pixel > image.lengthFromHere()) {
            throw Raised.of(EvaluationFailure.BAD_PATH_SET,
                    "pixel " + pixel + " is outside the image");
        }
        write(image, image.index() + pixel - 1, written);
    }

    private static void writeTheWholePicture(
            ImageValue image, WordValue field, Value written) {

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
        image.storage().reshape(across, howManyWholeRowsThePixelsMake(image, asked, across));
    }

    private static int howManyWholeRowsThePixelsMake(
            ImageValue image, PairValue asked, int across) {
        return Math.min((int) asked.y(), image.storage().length() / across);
    }

    private static void fillEveryPixel(
            ImageValue image, Value written, int... order) {

        if (aPicturesWorthOfBytes(written) instanceof int[] laid) {
            layOver(image, laid, order);
            return;
        }
        int[] parts = written instanceof TupleValue colour
                ? colour.segments()
                : greyParts(byteOrRefuse(written), order.length);
        for (int pixel = image.index(); pixel <= image.storageLength(); pixel++) {
            image.storage().setColourAt(pixel,
                    partOr(parts, 0, 0), partOr(parts, 1, 0), partOr(parts, 2, 0));
            if (order.length > 3) {
                image.storage().setAlphaAt(pixel, partOr(parts, 3, 0xFF));
            }
        }
    }

    private static int stoppingAtWhicheverRunsOutFirst(int given, int pixelsLeft) {
        return Math.min(given, pixelsLeft);
    }

    private static int[] greyParts(int grey, int channels) {
        return channels > 3
                ? new int[] {grey, grey, grey, grey}
                : new int[] {grey, grey, grey};
    }

    private static void fillTheSameByteEverywhere(ImageValue image, Value written) {
        if (aPicturesWorthOfBytes(written) instanceof int[] greys) {
            int pixels = stoppingAtWhicheverRunsOutFirst(
                    greys.length, image.lengthFromHere());
            for (int step = 0; step < pixels; step++) {
                image.storage().setColourAt(image.index() + step,
                        greys[step], greys[step], greys[step]);
            }
            return;
        }
        int grey = byteOrRefuse(written);
        for (int pixel = image.index(); pixel <= image.storageLength(); pixel++) {
            image.storage().setColourAt(pixel, grey, grey, grey);
            image.storage().setAlphaAt(pixel, grey);
        }
    }

    private static void fillTheOpacity(ImageValue image, Value written) {
        if (aPicturesWorthOfBytes(written) instanceof int[] laid) {
            int pixels = stoppingAtWhicheverRunsOutFirst(
                    laid.length, image.lengthFromHere());
            for (int step = 0; step < pixels; step++) {
                image.storage().setAlphaAt(image.index() + step,
                        opacityOfTheAlpha(laid[step]));
            }
            return;
        }
        int octet = byteOrRefuse(written);
        for (int pixel = image.index(); pixel <= image.storageLength(); pixel++) {
            image.storage().setAlphaAt(pixel, opacityOfTheAlpha(octet));
        }
    }

    private static void fillOneChannel(ImageValue image, Value written, int channel) {
        if (aPicturesWorthOfBytes(written) instanceof int[] laid) {
            int pixels = stoppingAtWhicheverRunsOutFirst(
                    laid.length, image.lengthFromHere());
            for (int step = 0; step < pixels; step++) {
                image.storage().setChannelAt(image.index() + step, channel, laid[step]);
            }
            return;
        }
        int octet = byteOrRefuse(written);
        for (int pixel = image.index(); pixel <= image.storageLength(); pixel++) {
            image.storage().setChannelAt(pixel, channel, octet);
        }
    }

    private static void layOver(ImageValue image, int[] laid, int[] order) {
        int pixels = stoppingAtWhicheverRunsOutFirst(
                laid.length / order.length, image.lengthFromHere());
        for (int step = 0; step < pixels; step++) {
            for (int which = 0; which < order.length; which++) {
                image.storage().setChannelAt(image.index() + step,
                        order[which] + 1, laid[step * order.length + which]);
            }
        }
    }

    private static int[] aPicturesWorthOfBytes(Value written) {
        if (written instanceof BinaryValue binary) {
            int[] octets = new int[binary.lengthFromHere()];
            for (int at = 0; at < octets.length; at++) {
                octets[at] = binary.storage().at(binary.index() + at);
            }
            return octets;
        }
        if (written instanceof VectorValue vector && vector.kind().bytes() == 1) {
            java.util.List<Value> numbers = vector.remaining();
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

    private static int byteOrRefuse(Value written) {
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
        int pixel = pixelNamedBy(image, pixelSegment);
        if (pixel < 1 || pixel > image.lengthFromHere()) {
            throw Raised.of(EvaluationFailure.BAD_PATH_SET,
                    "pixel " + pixel + " is outside the image");
        }
        image.storage().setChannelAt(image.index() + pixel - 1, channel,
                (int) magnitude);
    }

    private static int pixelNamedBy(ImageValue image, Value selector) {
        return switch (selector) {
            case PairValue coordinate -> ((int) coordinate.y() - 1) * image.storage().wide()
                    + ((int) coordinate.x() - 1) + 1;
            case IntegerValue number -> (int) number.magnitude();
            case DecimalValue number -> (int) number.quantity();
            case LogicValue yesOrNo -> yesOrNo.isTruthy() ? 1 : 2;
            default -> 0;
        };
    }

    private static int partOr(int[] parts, int at, int otherwise) {
        return at < parts.length ? parts[at] : otherwise;
    }

    private static int alphaFrom(Value written) {
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

    private static Value aboutTheImage(ImageValue image, WordValue field) {
        return switch (field.canonical()) {
            case "size" -> image.size();
            case "width" -> IntegerValue.of(image.storage().wide());
            case "height" -> IntegerValue.of(image.storage().high());
            case "rgb" -> channels(image, 0, 1, 2);
            case "bgr" -> channels(image, 2, 1, 0);
            case "rgba", "rgbo" -> channels(image, 0, 1, 2, 3);
            case "bgra", "bgro" -> channels(image, 2, 1, 0, 3);
            case "argb", "orgb" -> channels(image, 3, 0, 1, 2);
            case "abgr", "obgr" -> channels(image, 3, 2, 1, 0);
            case "alpha" -> channels(image, 3);
            case "opacity" -> opacitiesOf(image);
            case "red" -> channels(image, 0);
            case "green" -> channels(image, 1);
            case "blue" -> channels(image, 2);
            case "color" -> averageColourOf(image);
            case "luminosity" -> oneBytePerPixel(image, ImagePath::luminosityOf);
            case "gray" -> oneBytePerPixel(image, ImagePath::greyOf);
            default -> throw Raised.of(EvaluationFailure.INVALID_PATH, field.spelling());
        };
    }

    private static Value averageColourOf(ImageValue image) {
        int pixels = image.lengthFromHere();
        if (pixels == 0) {
            return TRANSPARENT_BLACK;
        }
        long red = 0;
        long green = 0;
        long blue = 0;
        long alpha = 0;
        for (int pixel = 1; pixel <= pixels; pixel++) {
            int[] channels = image.pixelAt(pixel);
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

    private static BinaryValue opacitiesOf(ImageValue image) {
        int pixels = image.lengthFromHere();
        int[] octets = new int[pixels];
        for (int pixel = 1; pixel <= pixels; pixel++) {
            octets[pixel - 1] = opacityOfTheAlpha(image.pixelAt(pixel)[3]);
        }
        return new BinaryValue(BinaryStorage.of(octets), 1);
    }

    private static int opacityOfTheAlpha(int alpha) {
        return 255 - alpha;
    }

    private static int luminosityOf(int red, int green, int blue) {
        return (int) (0.2126 * red + 0.7152 * green + 0.0722 * blue);
    }

    private static int greyOf(int red, int green, int blue) {
        return (red + green + blue) / 3;
    }

    private static BinaryValue oneBytePerPixel(
            ImageValue image, ThreeChannelsToOne squeeze) {

        int pixels = image.lengthFromHere();
        int[] octets = new int[pixels];
        for (int pixel = 1; pixel <= pixels; pixel++) {
            int[] channels = image.pixelAt(pixel);
            octets[pixel - 1] = squeeze.of(channels[0], channels[1], channels[2]);
        }
        return new BinaryValue(BinaryStorage.of(octets), 1);
    }

    @FunctionalInterface
    private interface ThreeChannelsToOne {
        int of(int red, int green, int blue);
    }

    private static BinaryValue channels(ImageValue image, int... order) {
        int pixels = image.lengthFromHere();
        int[] octets = new int[pixels * order.length];
        int written = 0;
        for (int pixel = 1; pixel <= pixels; pixel++) {
            int[] channels = image.pixelAt(pixel);
            for (int which : order) {
                octets[written++] = channels[which];
            }
        }
        return new BinaryValue(BinaryStorage.of(octets), 1);
    }
}
