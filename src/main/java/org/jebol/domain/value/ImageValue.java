package org.jebol.domain.value;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public record ImageValue(ImageStorage storage, int index) implements RebolSeries {

    private static final int PARTS_OF_A_PIXEL = 4;

    private static final int WHOLLY = 0xFF;

    private static final boolean WRITES_THE_ALPHA_TOO = false;

    private static final boolean WRITTEN = true;

    private static final boolean LEFT_ALONE = false;

    private record PixelWrite(int red, int green, int blue, int alpha,
            boolean writesTheColour, boolean writesTheAlpha) {

        void into(ImageStorage storage, int pixel) {
            if (writesTheColour) {
                storage.setColourAt(pixel, red, green, blue);
            }
            if (writesTheAlpha) {
                storage.setAlphaAt(pixel, alpha);
            }
        }
    }

    public ImageValue {
        if (storage == null) {
            throw new IllegalArgumentException("an image value needs storage");
        }
        if (index < 1 || index > storage.length() + 1) {
            throw new IllegalArgumentException(
                    "index " + index + " is outside 1.." + (storage.length() + 1));
        }
    }

    public static ImageValue of(int wide, int high) {
        return new ImageValue(ImageStorage.of(wide, high), 1);
    }

    @Override
    public boolean isProtected() {
        return storage.isProtected();
    }

    @Override
    public long positionNamedBy(Value given, boolean countingFromOne) {
        if (!(given instanceof PairValue(double x, double y))) {
            return RebolSeries.super.positionNamedBy(given, countingFromOne);
        }
        return ((long) y - (countingFromOne ? 1 : 0)) * storage.wide() + (long) x;
    }

    @Override
    public Value itemAt(int positionFromTheHead) {
        int[] channels = storage.pixelAt(positionFromTheHead);
        return TupleValue.of(channels[0], channels[1], channels[2], channels[3]);
    }

    @Override
    public Value frontCopied(int howMany, boolean deeply, Set<Datatype> kinds) {
        return copyOfTheFirstWholeRows(howMany);
    }

    @Override
    public RebolSeries reversedFront(int howMany) {
        for (int at = 0; at < howMany / 2; at++) {
            int[] near = pixelAt(at + 1);
            int[] far = pixelAt(howMany - at);
            writePixel(at + 1, far);
            writePixel(howMany - at, near);
        }
        return this;
    }

    @Override
    public RebolSeries reversedFromHere() {
        throw Raised.of(EvaluationFailure.EXPECT_ARG,
                "reverse wanted a series, not a " + datatype().literalSpelling());
    }

    public void writePixel(int pixel, int[] channels) {
        storage.setColourAt(pixel, channels[0], channels[1], channels[2]);
        storage.setAlphaAt(pixel, channels[3]);
    }

    public Value whereItStands(int countingFrom) {
        int across = storage.wide();
        if (across <= 0) {
            return IntegerValue.of(index - 1 + countingFrom);
        }
        int stepsIn = index - 1;
        return PairValue.of(stepsIn % across + countingFrom, stepsIn / across + countingFrom);
    }

    @Override
    public List<Value> items() {
        List<Value> read = new ArrayList<>(lengthFromHere());
        for (int at = index; at <= storageLength(); at++) {
            int[] channels = storage.pixelAt(at);
            read.add(TupleValue.of(channels[0], channels[1], channels[2], channels[3]));
        }
        return List.copyOf(read);
    }

    @Override
    public Datatype datatype() {
        return Datatype.IMAGE;
    }

    @Override
    public int storageLength() {
        return storage.length();
    }

    @Override
    public ImageValue atIndex(int oneBasedIndex) {
        return new ImageValue(storage, oneBasedIndex);
    }

    public ImageValue standingAt(int oneBasedIndex) {
        return atIndex(Math.min(oneBasedIndex, storage.length() + 1));
    }

    @Override
    public ImageValue head() {
        return atIndex(1);
    }

    @Override
    public ImageValue tail() {
        return atIndex(storage.length() + 1);
    }

    @Override
    public boolean sharesStorageWith(RebolSeries other) {
        return other instanceof ImageValue image && image.storage == storage;
    }

    public int[] pixelAt(int offsetFromHere) {
        return storage.pixelAt(index + offsetFromHere - 1);
    }

    @Override
    public Value copied(boolean deeply, Set<Datatype> kinds) {
        return copyOfTheFirstWholeRows(lengthFromHere());
    }

    public ImageValue copyOfTheFirstWholeRows(int howMany) {
        int taking = Math.max(0, Math.min(howMany, lengthFromHere()));
        int wideEnoughForARow = Math.max(1, storage.wide());
        int wide = Math.min(taking, wideEnoughForARow);
        int high = wide == 0 ? 0
                : taking <= wideEnoughForARow ? 1 : taking / wideEnoughForARow;
        ImageStorage into = ImageStorage.of(wide, high);
        for (int at = 1; at <= wide * high; at++) {
            int[] channels = pixelAt(at);
            into.setColourAt(at, channels[0], channels[1], channels[2]);
            into.setAlphaAt(at, channels[3]);
        }
        return new ImageValue(into, 1);
    }

    public Value appended(Value given, long howManyTimes) {
        List<PixelWrite> pixels = everyPixelOrNoneOfThem(given, WRITES_THE_ALPHA_TOO);
        for (long again = 0; again < howManyTimes; again++) {
            for (PixelWrite pixel : pixels) {
                grownWhiteAndThenWritten(storage.length() + 1, pixel);
            }
        }
        return head();
    }

    public Value inserted(Value given, long howManyTimes) {
        List<PixelWrite> pixels = everyPixelOrNoneOfThem(given, WRITES_THE_ALPHA_TOO);
        int at = index;
        for (long again = 0; again < howManyTimes; again++) {
            for (PixelWrite pixel : pixels) {
                grownWhiteAndThenWritten(at, pixel);
                at++;
            }
        }
        return atIndex(at);
    }

    public Value changed(Value given, long howManyTimes, boolean colourOnly,
            Value shapeOfTheRectangle) {
        if (given instanceof ImageValue rectangle) {
            return rectangleWritten(rectangle, howManyTimes, shapeOfTheRectangle);
        }
        List<PixelWrite> pixels = everyPixelOrNoneOfThem(given, colourOnly);
        int at = index;
        for (long again = 0; again < howManyTimes; again++) {
            for (PixelWrite pixel : pixels) {
                if (at > storage.length()) {
                    return atIndex(at);
                }
                pixel.into(storage, at);
                at++;
            }
        }
        return atIndex(at);
    }

    public ImageValue rectangleCopied(int wanted, int tall) {
        int from = Math.min(index - 1, storage.length());
        int column = storage.wide() == 0 ? 0 : from % storage.wide();
        int row = storage.wide() == 0 ? 0 : from / storage.wide();
        int columns = clippedToWhatIsLeft(wanted, storage.wide() - column);
        int rows = clippedToWhatIsLeft(tall, storage.high() - row);
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

    public Value thePixelFound(Value wanted, boolean onlyWhereItStands, boolean colourOnly,
            boolean landingPastIt) {
        if (!(wanted instanceof TupleValue || wanted instanceof IntegerValue)) {
            return NoneValue.none();
        }
        int at = positionOfThePixel(wanted, onlyWhereItStands, colourOnly);
        if (at == 0) {
            return NoneValue.none();
        }
        return atIndex(landingPastIt ? at + 1 : at);
    }

    private int positionOfThePixel(Value wanted, boolean onlyWhereItStands,
            boolean colourOnly) {
        if (onlyWhereItStands) {
            return pixelMatches(index, wanted, colourOnly) ? index : 0;
        }
        for (int at = index; at <= storage.length(); at++) {
            if (pixelMatches(at, wanted, colourOnly)) {
                return at;
            }
        }
        return 0;
    }

    private boolean pixelMatches(int oneBasedPixel, Value wanted, boolean colourOnly) {
        if (oneBasedPixel < 1 || oneBasedPixel > storage.length()) {
            return false;
        }
        int[] there = storage.pixelAt(oneBasedPixel);
        if (wanted instanceof IntegerValue(long magnitude)) {
            return there[3] == ((int) magnitude & 0xFF);
        }
        if (!(wanted instanceof TupleValue colour)) {
            return false;
        }
        int[] parts = colour.segments();
        for (int channel = 0; channel < 3; channel++) {
            if (there[channel] != partOr(parts, channel, 0)) {
                return false;
            }
        }
        return colourOnly || parts.length <= 3 || there[3] == parts[3];
    }

    private Value rectangleWritten(ImageValue rectangle, long howManyTimes,
            Value shapeOfTheRectangle) {
        if (storage.wide() == 0 || howManyTimes == 0) {
            return this;
        }
        int column = (index - 1) % storage.wide();
        int row = (index - 1) / storage.wide();
        int wanted = rectangle.storage.wide();
        int tall = rectangle.storage.high();
        if (shapeOfTheRectangle instanceof PairValue(double x, double y)) {
            wanted = Math.max(0, (int) x);
            tall = Math.max(0, (int) y);
        } else if (!(shapeOfTheRectangle instanceof NoneValue)) {
            wanted = 0;
            tall = 0;
        }
        int columns = Math.min(wanted, storage.wide() - column);
        int rows = Math.min(tall, storage.high() - row);
        for (int down = 0; down < rows; down++) {
            for (int across = 0; across < columns; across++) {
                int[] pixel = rectangle.storage
                        .pixelAt((down * rectangle.storage.wide()) + across + 1);
                int into = ((row + down) * storage.wide()) + column + across + 1;
                storage.setColourAt(into, pixel[0], pixel[1], pixel[2]);
                storage.setAlphaAt(into, pixel[3]);
            }
        }
        return standingAt(index + (int) howManyTimes);
    }

    private List<PixelWrite> everyPixelOrNoneOfThem(Value given, boolean colourOnly) {
        if (given instanceof BinaryValue bytes) {
            return theBytesReadFourAtATime(bytes, colourOnly);
        }
        List<Value> named = given instanceof BlockValue block
                && block.datatype() == Datatype.BLOCK
                ? block.remaining()
                : List.of(given);
        List<PixelWrite> pixels = new ArrayList<>();
        for (Value one : named) {
            pixels.add(asAPixel(one, colourOnly).orElseThrow(() -> Raised.of(
                    EvaluationFailure.INVALID_TYPE, DatatypeValue.of(one.datatype()))));
        }
        return pixels;
    }

    private Optional<PixelWrite> asAPixel(Value written, boolean colourOnly) {
        if (written instanceof TupleValue colour) {
            int[] parts = colour.segments();
            return Optional.of(new PixelWrite(partOr(parts, 0, 0), partOr(parts, 1, 0),
                    partOr(parts, 2, 0), partOr(parts, 3, WHOLLY), WRITTEN,
                    colourOnly ? LEFT_ALONE : WRITTEN));
        }
        if (written instanceof IntegerValue(long magnitude)) {
            return Optional.of(new PixelWrite(0, 0, 0, (int) magnitude & 0xFF,
                    LEFT_ALONE, WRITTEN));
        }
        return Optional.empty();
    }

    private List<PixelWrite> theBytesReadFourAtATime(BinaryValue bytes, boolean colourOnly) {
        List<PixelWrite> pixels = new ArrayList<>();
        int howMany = bytes.lengthFromHere() / PARTS_OF_A_PIXEL;
        for (int pixel = 0; pixel < howMany; pixel++) {
            int at = bytes.index() + (pixel * PARTS_OF_A_PIXEL);
            pixels.add(new PixelWrite(bytes.storage().at(at), bytes.storage().at(at + 1),
                    bytes.storage().at(at + 2),
                    colourOnly ? 0 : bytes.storage().at(at + 3),
                    WRITTEN, colourOnly ? LEFT_ALONE : WRITTEN));
        }
        return pixels;
    }

    private void grownWhiteAndThenWritten(int at, PixelWrite pixel) {
        storage.insertAt(at, WHOLLY, WHOLLY, WHOLLY, WHOLLY);
        pixel.into(storage, at);
    }

    private int partOr(int[] parts, int which, int missing) {
        return which < parts.length ? parts[which] : missing;
    }

    private int clippedToWhatIsLeft(int wanted, int room) {
        return Math.min(Math.max(wanted, 0), room);
    }

    public byte[] everyPixel() {
        byte[] octets = new byte[storageLength() * PARTS_OF_A_PIXEL];
        for (int pixel = 0; pixel < storageLength(); pixel++) {
            int[] parts = pixelAt(pixel + 1);
            for (int part = 0; part < PARTS_OF_A_PIXEL; part++) {
                octets[pixel * PARTS_OF_A_PIXEL + part] = (byte) parts[part];
            }
        }
        return octets;
    }

    public PairValue size() {
        return PairValue.of(storage.wide(), storage.high());
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof ImageValue image)) {
            return false;
        }
        if (storage.wide() != image.storage.wide()
                || storage.high() != image.storage.high()
                || lengthFromHere() != image.lengthFromHere()) {
            return false;
        }
        for (int offset = 1; offset <= lengthFromHere(); offset++) {
            if (!java.util.Arrays.equals(pixelAt(offset), image.pixelAt(offset))) {
                return false;
            }
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = storage.wide() * 31 + storage.high();
        for (int offset = 1; offset <= lengthFromHere(); offset++) {
            hash = hash * 31 + java.util.Arrays.hashCode(pixelAt(offset));
        }
        return hash;
    }

    @Override
    public String toString() {
        return "image " + storage.wide() + "x" + storage.high() + " @" + index;
    }
}
