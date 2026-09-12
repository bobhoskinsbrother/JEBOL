package org.jebol.domain.eval;

import org.jebol.domain.value.*;

import java.util.ArrayList;
import java.util.List;

final class ImageSeries {

    private ImageSeries() {
    }

    private static final int WHOLLY = 0xFF;

    private static final int CHANNELS = 4;

    private record APixelWrite(int red, int green, int blue, int alpha,
            boolean writesTheColour, boolean writesTheAlpha) {

        static APixelWrite ofAColourAndAnAlpha(int red, int green, int blue, int alpha) {
            return new APixelWrite(red, green, blue, alpha, true, true);
        }

        static APixelWrite ofAColourAlone(int red, int green, int blue) {
            return new APixelWrite(red, green, blue, 0, true, false);
        }

        static APixelWrite ofAnAlphaAlone(int alpha) {
            return new APixelWrite(0, 0, 0, alpha, false, true);
        }

        void into(ImageStorage storage, int pixel) {
            if (writesTheColour) {
                storage.setColourAt(pixel, red, green, blue);
            }
            if (writesTheAlpha) {
                storage.setAlphaAt(pixel, alpha);
            }
        }
    }

    private static APixelWrite asAPixel(Value written, boolean colourOnly) {
        if (written instanceof TupleValue colour) {
            int[] parts = colour.segments();
            if (colourOnly) {
                return APixelWrite.ofAColourAlone(
                        part(parts, 0), part(parts, 1), part(parts, 2));
            }
            return APixelWrite.ofAColourAndAnAlpha(
                    part(parts, 0), part(parts, 1), part(parts, 2),
                    parts.length > 3 ? parts[3] : WHOLLY);
        }
        if (written instanceof IntegerValue alpha) {
            return APixelWrite.ofAnAlphaAlone((int) alpha.magnitude() & 0xFF);
        }
        return null;
    }

    private static int part(int[] parts, int which) {
        return which < parts.length ? parts[which] : 0;
    }

    private static List<APixelWrite> everyPixelOrNoneOfThem(
            Value given, boolean colourOnly) {
        if (given instanceof BinaryValue bytes) {
            return theBytesReadFourAtATime(bytes, colourOnly);
        }
        List<APixelWrite> pixels = new ArrayList<>();
        List<Value> named = given instanceof BlockValue block
                && block.datatype() == Datatype.BLOCK
                ? block.remaining()
                : List.of(given);
        for (Value one : named) {
            APixelWrite pixel = asAPixel(one, colourOnly);
            if (pixel == null) {
                throw Raised.of(EvaluationFailure.INVALID_TYPE,
                        DatatypeValue.of(one.datatype()));
            }
            pixels.add(pixel);
        }
        return pixels;
    }

    private static List<APixelWrite> theBytesReadFourAtATime(
            BinaryValue bytes, boolean colourOnly) {

        List<APixelWrite> pixels = new ArrayList<>();
        int howMany = bytes.lengthFromHere() / CHANNELS;
        for (int pixel = 0; pixel < howMany; pixel++) {
            int at = bytes.index() + (pixel * CHANNELS);
            int red = bytes.storage().at(at);
            int green = bytes.storage().at(at + 1);
            int blue = bytes.storage().at(at + 2);
            pixels.add(colourOnly
                    ? APixelWrite.ofAColourAlone(red, green, blue)
                    : APixelWrite.ofAColourAndAnAlpha(
                            red, green, blue, bytes.storage().at(at + 3)));
        }
        return pixels;
    }

    static Value appended(ImageValue image, Value given, long howManyTimes) {
        List<APixelWrite> pixels = everyPixelOrNoneOfThem(given, WRITES_THE_ALPHA_TOO);
        ImageStorage storage = image.storage();
        for (long again = 0; again < howManyTimes; again++) {
            for (APixelWrite pixel : pixels) {
                grownWhiteAndThenWritten(storage, storage.length() + 1, pixel);
            }
        }
        return image.head();
    }

    static Value inserted(ImageValue image, Value given, long howManyTimes) {
        List<APixelWrite> pixels = everyPixelOrNoneOfThem(given, WRITES_THE_ALPHA_TOO);
        ImageStorage storage = image.storage();
        int at = image.index();
        for (long again = 0; again < howManyTimes; again++) {
            for (APixelWrite pixel : pixels) {
                grownWhiteAndThenWritten(storage, at, pixel);
                at++;
            }
        }
        return image.atIndex(at);
    }

    private static void grownWhiteAndThenWritten(
            ImageStorage storage, int at, APixelWrite pixel) {

        storage.insertAt(at, WHOLLY, WHOLLY, WHOLLY, WHOLLY);
        pixel.into(storage, at);
    }

    private static final boolean WRITES_THE_ALPHA_TOO = false;

    static Value changed(ImageValue image, Value given, long howManyTimes,
            boolean colourOnly, Value shapeOfTheRectangle) {

        if (given instanceof ImageValue rectangle) {
            return rectangleWritten(image, rectangle, howManyTimes,
                    shapeOfTheRectangle);
        }
        List<APixelWrite> pixels = everyPixelOrNoneOfThem(given, colourOnly);
        ImageStorage storage = image.storage();
        int at = image.index();
        for (long again = 0; again < howManyTimes; again++) {
            for (APixelWrite pixel : pixels) {
                if (pastTheEndIsDroppedRatherThanLengthening(at, storage)) {
                    return image.atIndex(at);
                }
                pixel.into(storage, at);
                at++;
            }
        }
        return image.atIndex(at);
    }

    private static boolean pastTheEndIsDroppedRatherThanLengthening(
            int at, ImageStorage storage) {
        return at > storage.length();
    }

    private static Value rectangleWritten(ImageValue image, ImageValue rectangle,
            long howManyTimes, Value shapeOfTheRectangle) {

        ImageStorage storage = image.storage();
        if (storage.wide() == 0 || howManyTimes == 0) {
            return image;
        }
        int column = (image.index() - 1) % storage.wide();
        int row = (image.index() - 1) / storage.wide();
        int wanted = rectangle.storage().wide();
        int tall = rectangle.storage().high();
        if (shapeOfTheRectangle instanceof PairValue shape) {
            wanted = Math.max(0, (int) shape.x());
            tall = Math.max(0, (int) shape.y());
        } else if (aCountWhereAShapeBelongs(shapeOfTheRectangle)) {
            wanted = 0;
            tall = 0;
        }
        int columns = Math.min(wanted, storage.wide() - column);
        int rows = Math.min(tall, storage.high() - row);
        for (int down = 0; down < rows; down++) {
            for (int across = 0; across < columns; across++) {
                int[] pixel = rectangle.storage()
                        .pixelAt((down * rectangle.storage().wide()) + across + 1);
                int into = ((row + down) * storage.wide()) + column + across + 1;
                storage.setColourAt(into, pixel[0], pixel[1], pixel[2]);
                storage.setAlphaAt(into, pixel[3]);
            }
        }
        return image.standingAt(oneImageIsOneThing(image, howManyTimes));
    }

    private static int oneImageIsOneThing(ImageValue image, long howManyTimes) {
        return image.index() + (int) howManyTimes;
    }

    private static boolean aCountWhereAShapeBelongs(Value shapeOfTheRectangle) {
        return !(shapeOfTheRectangle instanceof NoneValue);
    }

    static Value rectangleCopiedFrom(ImageValue image, int wanted, int tall) {
        ImageStorage storage = image.storage();
        int from = Math.min(image.index() - 1, storage.length());
        int column = storage.wide() == 0 ? 0 : from % storage.wide();
        int row = storage.wide() == 0 ? 0 : from / storage.wide();
        int columns = clippedToWhatIsLeftOfTheRow(wanted, storage.wide() - column);
        int rows = clippedToWhatIsLeftOfTheRow(tall, storage.high() - row);
        ImageStorage into = ImageStorage.of(columns, Math.max(rows, 0));
        for (int down = 0; down < rows; down++) {
            for (int across = 0; across < columns; across++) {
                int[] pixel = storage.pixelAt(
                        ((row + down) * storage.wide()) + column + across + 1);
                int at = (down * columns) + across + 1;
                into.setColourAt(at, pixel[0], pixel[1], pixel[2]);
                into.setAlphaAt(at, pixel[3]);
            }
        }
        return new ImageValue(into, 1);
    }

    private static int clippedToWhatIsLeftOfTheRow(int wanted, int room) {
        return Math.min(Math.max(wanted, 0), room);
    }

    static boolean pixelMatches(ImageValue image, int oneBasedPixel,
            Value wanted, boolean colourOnly) {

        if (oneBasedPixel < 1 || oneBasedPixel > image.storage().length()) {
            return false;
        }
        int[] there = image.storage().pixelAt(oneBasedPixel);
        if (wanted instanceof IntegerValue alpha) {
            return there[3] == ((int) alpha.magnitude() & 0xFF);
        }
        if (!(wanted instanceof TupleValue colour)) {
            return false;
        }
        int[] parts = colour.segments();
        for (int channel = 0; channel < 3; channel++) {
            if (there[channel] != part(parts, channel)) {
                return false;
            }
        }
        return colourOnly || parts.length <= 3 || there[3] == parts[3];
    }

    static int positionOf(ImageValue image, Value wanted,
            boolean neverLooksPastWhereItStarted, boolean colourOnly) {

        if (neverLooksPastWhereItStarted) {
            return pixelMatches(image, image.index(), wanted, colourOnly)
                    ? image.index()
                    : 0;
        }
        for (int at = image.index(); at <= image.storage().length(); at++) {
            if (pixelMatches(image, at, wanted, colourOnly)) {
                return at;
            }
        }
        return 0;
    }

    static boolean couldBeAPixel(Value wanted) {
        return wanted instanceof TupleValue || wanted instanceof IntegerValue;
    }
}
