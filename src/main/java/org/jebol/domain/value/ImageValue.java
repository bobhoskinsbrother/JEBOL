package org.jebol.domain.value;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public record ImageValue(ImageStorage storage, int index) implements RebolSeries {

    private static final int CHANNELS_PER_PIXEL = 4;

    private static final int WHOLLY = 0xFF;

    private static final boolean WRITES_ALPHA_TOO = false;

    private static final boolean WRITTEN = true;

    private static final boolean LEFT_ALONE = false;

    private record PixelWrite(int red, int green, int blue, int alpha,
                              boolean writesColour, boolean writesAlpha) {

        void writeInto(ImageStorage storage, int pixel) {
            if (writesColour) {
                storage.setColourAt(pixel, red, green, blue);
            }
            if (writesAlpha) {
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
    public long findPositionNamedBy(Value given, boolean countingFromOne) {
        if (!(given instanceof PairValue(double x, double y))) {
            return RebolSeries.super.findPositionNamedBy(given, countingFromOne);
        }
        return (roundHalfUp(y) - (countingFromOne ? 1 : 0)) * storage.wide() + roundHalfUp(x);
    }

    private static long roundHalfUp(double coordinate) {
        return (long) Math.floor(coordinate + 0.5);
    }

    @Override
    public Value pickBy(Value selector) {
        return switch (selector) {
            case PairValue pair -> pick((int) findPositionNamedBy(pair, true));
            case PercentValue ignored -> throw Raised.cannotUseAction(this, "pick");
            default -> RebolSeries.super.pickBy(selector);
        };
    }

    @Override
    public Value readItemAt(int positionFromHead) {
        int[] channels = storage.readPixelAt(positionFromHead);
        return TupleValue.of(channels[0], channels[1], channels[2], channels[3]);
    }

    @Override
    public Value copyFront(int howMany, boolean deeply, Set<Datatype> kinds) {
        return copyFirstWholeRows(howMany);
    }

    @Override
    public RebolSeries reverseFront(int howMany) {
        for (int at = 0; at < howMany / 2; at++) {
            int[] near = readPixelAt(at + 1);
            int[] far = readPixelAt(howMany - at);
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

    public Value findWhereItStands(int countingFrom) {
        int across = storage.wide();
        if (across <= 0) {
            return IntegerValue.of(index - 1 + countingFrom);
        }
        int stepsIn = index - 1;
        return PairValue.of(stepsIn % across + countingFrom, stepsIn / across + countingFrom);
    }

    @Override
    public List<Value> listItems() {
        List<Value> read = new ArrayList<>(lengthFromHere());
        for (int at = index; at <= storageLength(); at++) {
            int[] channels = storage.readPixelAt(at);
            read.add(TupleValue.of(channels[0], channels[1], channels[2], channels[3]));
        }
        return List.copyOf(read);
    }

    @Override
    public Datatype datatype() {
        return TYPE;
    }

    public static final Datatype TYPE = new ImageDatatype();

    private static final class ImageDatatype extends SeriesDatatype {

        private static final int WIDEST_ROW_OF_ITS_OWN_LENGTH = 100;

        private static final int WIDEST_HUNDRED_WIDE_PICTURE = 10000;

        private static final int BIG_PICTURE_ROW_WIDTH = 500;

        private static final int BYTES_PIXEL = 4;

        private static final int OCTET_MASK = 0xFF;

        ImageDatatype() {
            super("image");
        }

        @Override
        public Value makeFrom(Value spec, Maker maker) {
            if (spec instanceof ImageValue original) {
                return new ImageValue(original.storage().copy(), 1);
            }
            if (spec instanceof PairValue(double x, double y)) {
                return ImageValue.of(clampSide(x),
                        clampSide(y));
            }
            if (spec instanceof AnyBlockValue parts && !parts.remaining().isEmpty()) {
                return makeImageFromParts(parts);
            }
            throw refuseAsMalconstructed(spec);
        }

        @Override
        public Value convert(Value value, Maker maker) {
            if (value instanceof ImageValue already) {
                return new ImageValue(already.storage().copy(), 1);
            }
            if (value instanceof GobValue gob) {
                return maker.paintGob(gob).orElseThrow(() -> refuse(value));
            }
            if (!(value instanceof BinaryValue bytes)) {
                throw Raised.of(EvaluationFailure.INVALID_TYPE, value.datatype());
            }
            int pixels = bytes.lengthFromHere() / BYTES_PIXEL;
            if (pixels == 0) {
                throw refuse(value);
            }
            int across = pixels < WIDEST_ROW_OF_ITS_OWN_LENGTH
                    ? pixels
                    : pixels < WIDEST_HUNDRED_WIDE_PICTURE
                            ? WIDEST_ROW_OF_ITS_OWN_LENGTH
                            : BIG_PICTURE_ROW_WIDTH;
            int down = pixels / across;
            if (across * down < pixels) {
                down++;
            }
            ImageValue made = ImageValue.of(across, down);
            for (int pixel = 1; pixel <= pixels; pixel++) {
                int at = bytes.index() + (pixel - 1) * BYTES_PIXEL;
                made.storage().setColourAt(pixel,
                        bytes.storage().at(at),
                        bytes.storage().at(at + 1),
                        bytes.storage().at(at + 2));
                made.storage().setAlphaAt(pixel, bytes.storage().at(at + 3));
            }
            return made;
        }

        @Override
        public Value construct(List<Value> contents, Construction construction) {
            return construction.make(this, BlockValue.of(contents));
        }

        private int clampSide(double given) {
            int side = (int) given;
            if (side > ImageStorage.LONGEST_SIDE) {
                throw Raised.of(EvaluationFailure.SIZE_LIMIT, this);
            }
            return Math.max(side, 0);
        }

        private Value makeImageFromParts(AnyBlockValue specification) {
            List<Value> parts = specification.remaining();
            if (!(parts.getFirst() instanceof PairValue(double x, double y))) {
                throw refuseAsMalconstructed(specification);
            }
            ImageValue made = ImageValue.of(
                    limitSide(x, specification),
                    limitSide(y, specification));
            int at = 1;
            if (at < parts.size() && parts.get(at) instanceof BinaryValue colours) {
                fillColoursFrom(made, colours);
                at++;
                if (at < parts.size() && parts.get(at) instanceof BinaryValue alphas) {
                    fillAlphasFrom(made, alphas);
                    at++;
                }
                if (at < parts.size() && parts.get(at) instanceof IntegerValue start) {
                    made = made.standAt(raiseToAtLeastOne(start));
                    at++;
                }
            } else if (at < parts.size() && parts.get(at) instanceof TupleValue colour) {
                fillWith(made, colour);
                at++;
                if (at < parts.size() && parts.get(at) instanceof IntegerValue(long magnitude)) {
                    for (int pixel = 1; pixel <= made.storageLength(); pixel++) {
                        made.storage().setAlphaAt(pixel, (int) magnitude & OCTET_MASK);
                    }
                    at++;
                }
            }
            if (at != parts.size()) {
                throw refuseAsMalconstructed(specification);
            }
            return made;
        }

        private int limitSide(double given, AnyBlockValue specification) {
            if (given < 0 || given > ImageStorage.LONGEST_SIDE) {
                throw refuseAsMalconstructed(specification);
            }
            return (int) given;
        }

        private int raiseToAtLeastOne(IntegerValue start) {
            if (start.magnitude() < 1) {
                throw Raised.of(EvaluationFailure.OUT_OF_RANGE, start);
            }
            return (int) Math.min(start.magnitude(), Integer.MAX_VALUE);
        }

        private void fillColoursFrom(ImageValue made, BinaryValue colours) {
            int pixels = Math.min(made.storageLength(), colours.lengthFromHere() / 3);
            for (int pixel = 1; pixel <= pixels; pixel++) {
                int at = colours.index() + (pixel - 1) * 3;
                made.storage().setColourAt(pixel,
                        colours.storage().at(at),
                        colours.storage().at(at + 1),
                        colours.storage().at(at + 2));
            }
        }

        private void fillAlphasFrom(ImageValue made, BinaryValue alphas) {
            int pixels = Math.min(made.storageLength(), alphas.lengthFromHere());
            for (int pixel = 1; pixel <= pixels; pixel++) {
                made.storage().setAlphaAt(pixel, alphas.storage().at(alphas.index() + pixel - 1));
            }
        }

        private void fillWith(ImageValue made, TupleValue colour) {
            int[] parts = colour.segments();
            for (int pixel = 1; pixel <= made.storageLength(); pixel++) {
                made.storage().setColourAt(pixel,
                        parts.length > 0 ? parts[0] : 0,
                        parts.length > 1 ? parts[1] : 0,
                        parts.length > 2 ? parts[2] : 0);
                if (parts.length > 3) {
                    made.storage().setAlphaAt(pixel, parts[3]);
                }
            }
        }

        private Raised refuseAsMalconstructed(Value from) {
            return Raised.of(EvaluationFailure.MALCONSTRUCT, from);
        }
    }

    @Override
    public int storageLength() {
        return storage.length();
    }

    @Override
    public ImageValue atIndex(int oneBasedIndex) {
        return new ImageValue(storage, oneBasedIndex);
    }

    public ImageValue standAt(int oneBasedIndex) {
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

    public int[] readPixelAt(int offsetFromHere) {
        return storage.readPixelAt(index + offsetFromHere - 1);
    }

    @Override
    public Value copy(boolean deeply, Set<Datatype> kinds) {
        return copyFirstWholeRows(lengthFromHere());
    }

    public ImageValue copyFirstWholeRows(int howMany) {
        int taking = Math.max(0, Math.min(howMany, lengthFromHere()));
        int wideEnoughForRow = Math.max(1, storage.wide());
        int wide = Math.min(taking, wideEnoughForRow);
        int high = wide == 0 ? 0
                : taking <= wideEnoughForRow ? 1 : taking / wideEnoughForRow;
        ImageStorage into = ImageStorage.of(wide, high);
        for (int at = 1; at <= wide * high; at++) {
            int[] channels = readPixelAt(at);
            into.setColourAt(at, channels[0], channels[1], channels[2]);
            into.setAlphaAt(at, channels[3]);
        }
        return new ImageValue(into, 1);
    }

    public Value append(Value given, long howManyTimes) {
        List<PixelWrite> pixels = collectEveryPixelOrNone(given, WRITES_ALPHA_TOO);
        for (long again = 0; again < howManyTimes; again++) {
            for (PixelWrite pixel : pixels) {
                growWhiteAndWrite(storage.length() + 1, pixel);
            }
        }
        return head();
    }

    public Value insert(Value given, long howManyTimes) {
        List<PixelWrite> pixels = collectEveryPixelOrNone(given, WRITES_ALPHA_TOO);
        int at = index;
        for (long again = 0; again < howManyTimes; again++) {
            for (PixelWrite pixel : pixels) {
                growWhiteAndWrite(at, pixel);
                at++;
            }
        }
        return atIndex(at);
    }

    public Value change(Value given, long howManyTimes, boolean colourOnly,
                        Value rectangleShape) {
        if (given instanceof ImageValue rectangle) {
            return writeRectangle(rectangle, howManyTimes, rectangleShape);
        }
        List<PixelWrite> pixels = collectEveryPixelOrNone(given, colourOnly);
        int at = index;
        for (long again = 0; again < howManyTimes; again++) {
            for (PixelWrite pixel : pixels) {
                if (at > storage.length()) {
                    return atIndex(at);
                }
                pixel.writeInto(storage, at);
                at++;
            }
        }
        return atIndex(at);
    }

    public ImageValue copyRectangle(int wanted, int tall) {
        int from = Math.min(index - 1, storage.length());
        int column = storage.wide() == 0 ? 0 : from % storage.wide();
        int row = storage.wide() == 0 ? 0 : from / storage.wide();
        int columns = clipToWhatIsLeft(wanted, storage.wide() - column);
        int rows = clipToWhatIsLeft(tall, storage.high() - row);
        ImageStorage into = ImageStorage.of(columns, Math.max(rows, 0));
        for (int down = 0; down < rows; down++) {
            for (int across = 0; across < columns; across++) {
                int[] pixel = storage.readPixelAt(
                        ((row + down) * storage.wide()) + column + across + 1);
                int at = (down * columns) + across + 1;
                into.setColourAt(at, pixel[0], pixel[1], pixel[2]);
                into.setAlphaAt(at, pixel[3]);
            }
        }
        return new ImageValue(into, 1);
    }

    public Value findPixel(Value wanted, boolean onlyWhereItStands, boolean colourOnly,
                           boolean landingPastIt) {
        if (!(wanted instanceof TupleValue || wanted instanceof IntegerValue)) {
            return NoneValue.none();
        }
        int at = findPixelPosition(wanted, onlyWhereItStands, colourOnly);
        if (at == 0) {
            return NoneValue.none();
        }
        return atIndex(landingPastIt ? at + 1 : at);
    }

    private int findPixelPosition(Value wanted, boolean onlyWhereItStands,
                                  boolean colourOnly) {
        if (onlyWhereItStands) {
            return matchesPixel(index, wanted, colourOnly) ? index : 0;
        }
        for (int at = index; at <= storage.length(); at++) {
            if (matchesPixel(at, wanted, colourOnly)) {
                return at;
            }
        }
        return 0;
    }

    private boolean matchesPixel(int oneBasedPixel, Value wanted, boolean colourOnly) {
        if (oneBasedPixel < 1 || oneBasedPixel > storage.length()) {
            return false;
        }
        int[] there = storage.readPixelAt(oneBasedPixel);
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

    private Value writeRectangle(ImageValue rectangle, long howManyTimes,
                                 Value rectangleShape) {
        if (storage.wide() == 0 || howManyTimes == 0) {
            return this;
        }
        int column = (index - 1) % storage.wide();
        int row = (index - 1) / storage.wide();
        int wanted = rectangle.storage.wide();
        int tall = rectangle.storage.high();
        if (rectangleShape instanceof PairValue(double x, double y)) {
            wanted = Math.max(0, (int) x);
            tall = Math.max(0, (int) y);
        } else if (!(rectangleShape instanceof NoneValue)) {
            wanted = 0;
            tall = 0;
        }
        int columns = Math.min(wanted, storage.wide() - column);
        int rows = Math.min(tall, storage.high() - row);
        for (int down = 0; down < rows; down++) {
            for (int across = 0; across < columns; across++) {
                int[] pixel = rectangle.storage
                        .readPixelAt((down * rectangle.storage.wide()) + across + 1);
                int into = ((row + down) * storage.wide()) + column + across + 1;
                storage.setColourAt(into, pixel[0], pixel[1], pixel[2]);
                storage.setAlphaAt(into, pixel[3]);
            }
        }
        return standAt(index + (int) howManyTimes);
    }

    private List<PixelWrite> collectEveryPixelOrNone(Value given, boolean colourOnly) {
        if (given instanceof BinaryValue bytes) {
            return readBytesFourAtTime(bytes, colourOnly);
        }
        List<Value> named = given instanceof BlockValue block
                ? block.remaining()
                : List.of(given);
        List<PixelWrite> pixels = new ArrayList<>();
        for (Value one : named) {
            pixels.add(asPixel(one, colourOnly).orElseThrow(() -> Raised.of(
                    EvaluationFailure.INVALID_TYPE, one.datatype())));
        }
        return pixels;
    }

    private Optional<PixelWrite> asPixel(Value written, boolean colourOnly) {
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

    private List<PixelWrite> readBytesFourAtTime(BinaryValue bytes, boolean colourOnly) {
        List<PixelWrite> pixels = new ArrayList<>();
        int howMany = bytes.lengthFromHere() / CHANNELS_PER_PIXEL;
        for (int pixel = 0; pixel < howMany; pixel++) {
            int at = bytes.index() + (pixel * CHANNELS_PER_PIXEL);
            pixels.add(new PixelWrite(bytes.storage().at(at), bytes.storage().at(at + 1),
                    bytes.storage().at(at + 2),
                    colourOnly ? 0 : bytes.storage().at(at + 3),
                    WRITTEN, colourOnly ? LEFT_ALONE : WRITTEN));
        }
        return pixels;
    }

    private void growWhiteAndWrite(int at, PixelWrite pixel) {
        storage.insertAt(at, WHOLLY, WHOLLY, WHOLLY, WHOLLY);
        pixel.writeInto(storage, at);
    }

    private int partOr(int[] parts, int which, int missing) {
        return which < parts.length ? parts[which] : missing;
    }

    private int clipToWhatIsLeft(int wanted, int room) {
        return Math.min(Math.max(wanted, 0), room);
    }

    public byte[] copyEveryPixel() {
        byte[] octets = new byte[storageLength() * CHANNELS_PER_PIXEL];
        for (int pixel = 0; pixel < storageLength(); pixel++) {
            int[] parts = readPixelAt(pixel + 1);
            for (int part = 0; part < CHANNELS_PER_PIXEL; part++) {
                octets[pixel * CHANNELS_PER_PIXEL + part] = (byte) parts[part];
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
            if (!java.util.Arrays.equals(readPixelAt(offset), image.readPixelAt(offset))) {
                return false;
            }
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = storage.wide() * 31 + storage.high();
        for (int offset = 1; offset <= lengthFromHere(); offset++) {
            hash = hash * 31 + java.util.Arrays.hashCode(readPixelAt(offset));
        }
        return hash;
    }

    @Override
    public String toString() {
        return "image " + storage.wide() + "x" + storage.high() + " @" + index;
    }
}
