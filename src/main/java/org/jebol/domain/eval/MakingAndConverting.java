package org.jebol.domain.eval;

import org.jebol.domain.date.DateMaking;
import org.jebol.domain.read.Construction;
import org.jebol.domain.read.TranscodeResult;
import org.jebol.domain.read.Transcoder;
import org.jebol.domain.value.*;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalLong;
import java.util.Set;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class MakingAndConverting implements Construction {

    private static final Set<Datatype> CAN_NAME_A_SCHEME = Set.of(
            Datatype.FILE, Datatype.URL, Datatype.BLOCK,
            Datatype.OBJECT, Datatype.WORD, Datatype.PORT);

    private static final int BYTES_A_SLOT_TAKES = 32;

    private static final int BYTES_A_VECTORS_NUMBER_TAKES = 4;

    private static final long MICROSECONDS_A_SECOND = 1_000_000L;

    private static final int WIDEST_ROW_OF_ITS_OWN_LENGTH = 100;

    private static final int WIDEST_HUNDRED_WIDE_PICTURE = 10000;

    private static final int A_ROW_OF_A_BIG_PICTURE = 500;

    private static final int BYTES_A_PIXEL = 4;

    private static final int THE_LARGEST_OCTET = 255;

    private static final int MOST_HEX_DIGITS = 16;

    private static final int MOST_HEX_DIGITS_SCANNED = 16;

    private static final String THE_PUNCTUATION_THAT_SPELLS_A_WORD_ALONE = "!%&*+-./<=>?^`|~";

    private static final int THE_FIRST_CODE_POINT_THE_SCANNER_TAKES_FOR_A_LETTER = 128;

    private static final char MOST_LETTERS_ARE_ONE_BYTE = 127;

    private static final int MOST_WHOLE_NUMBER_CHARACTERS = 25;

    private static final int MOST_FRACTION_CHARACTERS = 24;

    private static final int MOST_MONEY_CHARACTERS = 36;

    private static final Pattern WRITTEN_DECIMAL = Pattern.compile(
            "[+-]?(?:[0-9]+(?:[.][0-9]*)?|[.][0-9]+)(?:[eE][+-]?[0-9]*)?");

    private static final Pattern EMPTY_EXPONENT = Pattern.compile("[eE][+-]?$");

    private static final double TOO_LARGE_FOR_A_WHOLE_NUMBER = 9.223372036854776E18;

    private static final double MOST_SECONDS_A_DURATION_HOLDS = 9_223_372_036.0;

    private static final long MOST_SECONDS_A_TIME_HOLDS = 9_223_372_036L;

    private static final int LONGEST_TIME_A_STRING_MAY_SPELL = 30;

    private static final char ASCII_ENDS_AT = 127;

    private final MapValue registeredStructLayouts;

    public MakingAndConverting(MapValue registeredStructLayouts) {
        this.registeredStructLayouts = registeredStructLayouts;
    }

    @Override
    public Value madeOf(Datatype datatype, Value specification) {
        if (datatype == Datatype.DATE
                && !(specification instanceof BlockValue
                        || specification instanceof DateValue)) {
            throw Raised.badMakeArg(specification, "date!");
        }
        return made(DatatypeValue.of(datatype), specification,
                null, Context.root(), UnaryOperator.identity());
    }

    @Override
    public Value functionMadeFrom(BlockValue spec, BlockValue body) {
        return Binder.functionWithItsBodyBound(spec, body, Context.root());
    }

    public Value made(
            DatatypeValue wanted, Value source, Evaluator evaluator, Context context,
            UnaryOperator<Value> lookedUp) {
        return switch (wanted.represents()) {
            case MAP -> mapFrom(source);
            case BITSET -> BitsetActions.madeFrom(source);
            case PAIR -> asPair(source);
            case STRUCT -> structMadeFrom(source);
            case IMAGE -> madeImage(source);
            case GOB -> madeGob(source, lookedUp);
            case EVENT -> EventPath.made(wanted, source, lookedUp);
            case VECTOR -> {
                refuseMoreRoomThanASeriesCounts(Datatype.VECTOR, source);
                yield whatTheHostHadRoomFor(() -> madeVector(source, lookedUp));
            }
            case PORT -> portMadeFrom(source, evaluator, context);
            case DATE -> aDateMadeFrom(wanted, source);
            case TIME -> source instanceof BlockValue parts
                    ? timeFromParts(parts.remaining())
                    : madeOtherwise(wanted, source);
            default -> madeOtherwise(wanted, source);
        };
    }

    private Value aDateMadeFrom(DatatypeValue wanted, Value from) {
        return switch (from) {
            case BlockValue parts -> DateMaking.fromParts(parts.remaining());
            case DateValue already -> DateMaking.fromParts(List.of(already));
            default -> madeOtherwise(wanted, from);
        };
    }

    private Value madeOtherwise(DatatypeValue wanted, Value from) {
        refuseToBuildSomethingOutOfNothing(wanted.represents(), from);
        refuseRoomForLessThanNothing(wanted.represents(), from);
        refuseMoreRoomThanASeriesCounts(wanted.represents(), from);
        if (wanted.represents().isAnyBlock()) {
            return whatTheHostHadRoomFor(() ->
                    blockTypeBuilt(Conversion.MAKE, wanted.represents(), from));
        }
        if (wanted.represents().isSeries()
                && (from.datatype() == Datatype.INTEGER
                        || from.datatype() == Datatype.DECIMAL)) {
            int asked = (int) Math.max(0,
                    Math.min(Integer.MAX_VALUE, (long) Comparison.asDouble(from)));
            return whatTheHostHadRoomFor(() ->
                    wanted.represents() == Datatype.BINARY
                            ? new BinaryValue(new BinaryStorage(asked), 1)
                            : new StringValue(
                                    StringStorage.withRoomFor(asked), 1,
                                    wanted.represents()));
        }
        return converted(Conversion.MAKE, wanted, from);
    }

    public Value converted(Conversion asking, DatatypeValue wanted, Value value) {
        refuseToBuildSomethingOutOfNothing(wanted.represents(), value);
        return switch (wanted.represents()) {
            case UNSET -> UnsetValue.unset();
            case NONE -> NoneValue.none();
            case VECTOR -> switch (value) {
                case VectorValue already -> already;
                case BinaryValue octets -> VectorSpec.ofOctets(octets);
                case BlockValue block -> VectorSpec.readMakeSpec(
                                block.remaining(), UnaryOperator.identity())
                        .<Value>map(made -> made)
                        .orElseThrow(() -> Raised.cannotUse(value, "to vector!"));
                default -> throw Raised.cannotUse(value, "to vector!");
            };
            case INTEGER -> wholeNumberFrom(asking, value);
            case DECIMAL, PERCENT -> decimalBuiltFrom(asking, wanted.represents(), value);
            case STRING -> StringValue.of(textOf(value));
            case EMAIL -> value instanceof BlockValue parts
                    ? addressBuiltFrom(parts)
                    : StringValue.of(textOf(value), Datatype.EMAIL);
            case URL -> value instanceof BlockValue parts
                    ? urlBuiltFrom(parts)
                    : StringValue.of(textOf(value), Datatype.URL);
            case FILE, TAG, REF -> StringValue.of(textOf(value), wanted.represents());
            case BINARY -> binaryBuiltFrom(value);
            case WORD, SET_WORD, GET_WORD, LIT_WORD, REFINEMENT, ISSUE ->
                    wordFrom(value, wanted.represents());
            case BLOCK, PAREN, HASH, PATH, SET_PATH, GET_PATH, LIT_PATH ->
                    blockTypeBuilt(asking, wanted.represents(), value);
            case MAP -> {
                if (value instanceof IntegerValue || value instanceof DecimalValue) {
                    throw Raised.of(EvaluationFailure.INVALID_ARG, value);
                }
                yield mapFrom(value);
            }
            case DATE -> switch (value) {
                case DateValue already -> already;
                case IntegerValue seconds ->
                        DateMaking.atTheTimestamp(seconds.magnitude() * MICROSECONDS_A_SECOND);
                case DecimalValue seconds -> DateMaking.atTheTimestamp(
                        (long) (seconds.quantity() * MICROSECONDS_A_SECOND));
                case BlockValue parts -> DateMaking.fromParts(parts.remaining());
                case StringValue written -> dateReadFrom(written);
                default -> throw Raised.badMakeArg(value, "date!");
            };
            case CHAR -> asCharacter(value);
            case PAIR -> asPair(value);
            case MONEY -> asMoney(asking, value);
            case PORT -> value instanceof ObjectValue(Context fields)
                    ? new PortValue(fields)
                    : badMakeArg(value, "port!");
            case MODULE -> moduleFromHeaderAndWords(value);
            case TASK -> asking.builds() ? aTaskMadeFrom(value) : badMakeArg(value, "task!");
            case BITSET -> BitsetActions.madeFrom(value);
            case TYPESET -> switch (value) {
                case TypesetValue already -> already;
                case BlockValue block when block.datatype() == Datatype.BLOCK ->
                        TypesetValue.of(TypesetActions.datatypesNamedIn(block));
                default -> throw Raised.badMakeArg(value, "typeset!");
            };
            case TIME -> aTimeMadeFrom(value);
            case TUPLE -> tupleFrom(value);
            case LOGIC -> LogicValue.of(countsAsTrue(asking, value));
            case DATATYPE -> value instanceof WordValue word
                    ? datatypeNamed(word, value)
                    : badMakeArg(value, "datatype!");
            case IMAGE -> imageConvertedFrom(value);
            default -> throw Raised.cannotUse(value, "to " + wanted.represents().literalSpelling());
        };
    }

    public String textDecodedFrom(BinaryValue octets) {
        byte[] bytes = octets.octetsFromHere();
        int marked = octets.byteOrderMark();
        if (marked != 0) {
            return textBehindTheMark(bytes, marked);
        }
        CharsetDecoder strictly = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        bytes = withSurrogatePairsJoined(bytes);
        ByteBuffer reading = ByteBuffer.wrap(bytes);
        CharBuffer written = CharBuffer.allocate(bytes.length + 1);
        CoderResult stopped = strictly.decode(reading, written, true);
        if (stopped.isError()) {
            throw Raised.of(EvaluationFailure.INVALID_UTF, BinaryValue.ofBytes(
                    Arrays.copyOfRange(bytes, reading.position(), bytes.length)));
        }
        strictly.flush(written);
        return written.flip().toString();
    }

    public Value aTimeMadeFrom(Value value) {
        return switch (value) {
            case TimeValue already -> already;
            case StringValue written when value.datatype() == Datatype.STRING ->
                    theTimeScannedFrom(written.text(), written);
            case BlockValue parts when value.datatype() == Datatype.BLOCK
                    || value.datatype() == Datatype.PAREN ->
                    aTimeOfHoursMinutesAndSeconds(parts);
            case Value number when number.datatype() == Datatype.INTEGER
                    || number.datatype() == Datatype.DECIMAL ->
                    aDurationOfSeconds(number);
            default -> throw Raised.badMakeArg(value, "time!");
        };
    }

    public Value madeGob(Value from, UnaryOperator<Value> lookedUp) {
        if (from instanceof GobValue cloned) {
            return new GobValue(cloned.storage().copyWithoutPane(), 1);
        }
        GobValue made = GobValue.empty();
        if (from instanceof PairValue size) {
            made.storage().size(size);
            return made;
        }
        if (from instanceof BlockValue spec && spec.datatype() == Datatype.BLOCK) {
            fillGobFromSpec(made, spec.remaining(), lookedUp);
            return made;
        }
        throw Raised.of(EvaluationFailure.BAD_MAKE_ARG,
                "a gob is made from a block, a gob or a pair, not "
                        + from.datatype().literalSpelling());
    }

    private Value badMakeArg(Value value, String wanted) {
        throw Raised.badMakeArg(value, wanted);
    }

    private String textOf(Value value) {
        return switch (value) {
            case BinaryValue octets -> textDecodedFrom(octets);
            case StringValue already -> already.text();
            default -> value.runTogether();
        };
    }

    private void fillGobFromSpec(GobValue gob, List<Value> spec, UnaryOperator<Value> lookedUp) {
        for (int at = 0; at < spec.size(); at += 2) {
            Value name = spec.get(at);
            if (!(name instanceof WordValue field)
                    || field.datatype() != Datatype.SET_WORD) {
                throw Raised.of(EvaluationFailure.EXPECT_VAL,
                        DatatypeValue.of(Datatype.SET_WORD),
                        DatatypeValue.of(name.datatype()));
            }
            Value given = at + 1 < spec.size() ? spec.get(at + 1) : UnsetValue.unset();
            if (given.datatype() == Datatype.UNSET
                    || given.datatype() == Datatype.SET_WORD) {
                throw Raised.of(EvaluationFailure.NEED_VALUE, field);
            }
            Value written = lookedUp.apply(given);
            if (!GobPath.accepted(gob.storage(), field.canonical(), written)) {
                throw Raised.of(EvaluationFailure.BAD_FIELD_SET,
                        field, DatatypeValue.of(written.datatype()));
            }
        }
    }

    private Value portMadeFrom(Value from, Evaluator evaluator, Context context) {
        if (!CAN_NAME_A_SCHEME.contains(from.datatype())) {
            throw Raised.of(EvaluationFailure.INVALID_SPEC, from);
        }
        Value built = evaluator.applyFunction(
                context.systemFunctionNamed("make-port*"), List.of(from));
        if (!(built instanceof PortValue port)) {
            throw Raised.of(EvaluationFailure.INVALID_SPEC, from);
        }
        return port;
    }

    private Value madeImage(Value from) {
        if (from instanceof ImageValue original) {
            return new ImageValue(original.storage().copy(), 1);
        }
        if (from instanceof PairValue(double x, double y)) {
            return ImageValue.of(sideOfClampedBelowAndRefusedAbove(x),
                    sideOfClampedBelowAndRefusedAbove(y));
        }
        if (from instanceof BlockValue parts && !parts.remaining().isEmpty()) {
            return imageFromParts(parts);
        }
        throw malconstructed(from);
    }

    private int sideOfClampedBelowAndRefusedAbove(double given) {
        int side = (int) given;
        if (side > ImageStorage.LONGEST_SIDE) {
            throw Raised.of(EvaluationFailure.SIZE_LIMIT, DatatypeValue.of(Datatype.IMAGE));
        }
        return Math.max(side, 0);
    }

    private Value imageFromParts(BlockValue specification) {
        List<Value> parts = specification.remaining();
        if (!(parts.getFirst() instanceof PairValue(double x, double y))) {
            throw malconstructed(specification);
        }
        ImageValue made = ImageValue.of(
                sideThatCanExist(x, specification),
                sideThatCanExist(y, specification));
        int at = 1;
        if (at < parts.size() && parts.get(at) instanceof BinaryValue colours) {
            fillColoursFrom(made, colours);
            at++;
            if (at < parts.size() && parts.get(at) instanceof BinaryValue alphas) {
                fillAlphasFrom(made, alphas);
                at++;
            }
            if (at < parts.size() && parts.get(at) instanceof IntegerValue start) {
                made = made.standingAt(aPositionOfAtLeastOne(start));
                at++;
            }
        } else if (at < parts.size() && parts.get(at) instanceof TupleValue colour) {
            fillWith(made, colour);
            at++;
            if (at < parts.size() && parts.get(at) instanceof IntegerValue(long magnitude)) {
                for (int pixel = 1; pixel <= made.storageLength(); pixel++) {
                    made.storage().setAlphaAt(pixel, (int) magnitude & 0xFF);
                }
                at++;
            }
        }
        if (at != parts.size()) {
            throw malconstructed(specification);
        }
        return made;
    }

    private int sideThatCanExist(double given, BlockValue specification) {
        if (given < 0 || given > ImageStorage.LONGEST_SIDE) {
            throw malconstructed(specification);
        }
        return (int) given;
    }

    private int aPositionOfAtLeastOne(IntegerValue start) {
        if (start.magnitude() < 1) {
            throw Raised.of(EvaluationFailure.OUT_OF_RANGE, start);
        }
        return (int) Math.min(start.magnitude(), Integer.MAX_VALUE);
    }

    private Value imageConvertedFrom(Value value) {
        if (value instanceof ImageValue already) {
            return new ImageValue(already.storage().copy(), 1);
        }
        if (!(value instanceof BinaryValue bytes)) {
            throw Raised.of(EvaluationFailure.INVALID_TYPE, value.datatype().literalSpelling());
        }
        int pixels = bytes.lengthFromHere() / BYTES_A_PIXEL;
        if (pixels == 0) {
            throw Raised.badMakeArg(value, "image!");
        }
        int across = pixels < WIDEST_ROW_OF_ITS_OWN_LENGTH
                ? pixels
                : pixels < WIDEST_HUNDRED_WIDE_PICTURE
                        ? WIDEST_ROW_OF_ITS_OWN_LENGTH
                        : A_ROW_OF_A_BIG_PICTURE;
        int down = pixels / across;
        if (across * down < pixels) {
            down++;
        }
        ImageValue made = ImageValue.of(across, down);
        for (int pixel = 1; pixel <= pixels; pixel++) {
            int at = bytes.index() + (pixel - 1) * BYTES_A_PIXEL;
            made.storage().setColourAt(pixel,
                    bytes.storage().at(at),
                    bytes.storage().at(at + 1),
                    bytes.storage().at(at + 2));
            made.storage().setAlphaAt(pixel, bytes.storage().at(at + 3));
        }
        return made;
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

    private Raised malconstructed(Value from) {
        return Raised.of(EvaluationFailure.MALCONSTRUCT, Molder.mold(from));
    }

    private Value madeVector(Value from, UnaryOperator<Value> lookedUp) {
        if (from instanceof IntegerValue || from instanceof DecimalValue) {
            long howMany = from instanceof IntegerValue(long magnitude)
                    ? magnitude
                    : (long) ((DecimalValue) from).quantity();
            if (howMany < 0) {
                throw Raised.of(EvaluationFailure.OUT_OF_RANGE, Molder.mold(from));
            }
            return VectorSpec.ofSize((int) howMany);
        }
        if (from instanceof BinaryValue bytes) {
            return VectorSpec.ofOctets(bytes);
        }
        if (from instanceof VectorValue already) {
            return already.copyOfTheFirst(already.lengthFromHere());
        }
        if (from instanceof BlockValue spec) {
            return VectorSpec.readMakeSpec(spec.remaining(), lookedUp)
                    .orElseThrow(() -> Raised.of(EvaluationFailure.BAD_MAKE_ARG,
                            Datatype.VECTOR.literalSpelling()));
        }
        throw Raised.of(EvaluationFailure.BAD_MAKE_ARG, Datatype.VECTOR.literalSpelling());
    }

    private Value dateReadFrom(StringValue written) {
        return Transcoder.transcode(written.text()).values()
                .map(BlockValue::remaining)
                .filter(read -> read.size() == 1 && read.getFirst() instanceof DateValue)
                .map(List::getFirst)
                .orElseThrow(() -> Raised.badMakeArg(written, "date!"));
    }

    private Value blockTypeBuilt(Conversion asking, Datatype wanted, Value from) {
        if (from instanceof BlockValue given) {
            BlockStorage built = new BlockStorage(given.remaining());
            built.takeLineBreaksFrom(given.storage(), given.index());
            return new BlockValue(built, 1, given.datatype()).as(wanted);
        }
        if (from instanceof MapValue pairs) {
            return pairs.pairsOnLines().as(wanted);
        }
        if (from.isAnyObject()) {
            return from.fieldsAsAContext().orElseThrow().setWordsAndValuesOnLines().as(wanted);
        }
        if (from instanceof VectorValue numbers) {
            return BlockValue.block(numbers.remaining()).as(wanted);
        }
        if (asking.builds()) {
            if (from.datatype() == Datatype.INTEGER || from.datatype() == Datatype.DECIMAL) {
                return BlockValue.block(List.of()).as(wanted);
            }
        } else if (wrapsIntoWhatTheCallerAskedFor(wanted)) {
            return from instanceof TypesetValue kinds
                    && (wanted == Datatype.BLOCK || wanted == Datatype.PAREN)
                    ? BlockValue.block(kinds.members().stream()
                            .sorted().<Value>map(DatatypeValue::of).toList()).as(wanted)
                    : BlockValue.block(from).as(wanted);
        }
        if (from.datatype() == Datatype.STRING && from instanceof StringValue text) {
            return sourceReadFromStoppingAtANoughtByte(text.text(), wanted);
        }
        if (from instanceof BinaryValue octets) {
            return sourceReadFromStoppingAtANoughtByte(textDecodedFrom(octets), wanted);
        }
        if (from.datatype() == Datatype.PAIR) {
            return BlockValue.block(List.of()).as(wanted);
        }
        throw Raised.of(EvaluationFailure.INVALID_ARG, Molder.mold(from));
    }

    private boolean wrapsIntoWhatTheCallerAskedFor(Datatype wanted) {
        return wanted == Datatype.BLOCK || wanted == Datatype.PAREN || wanted.isAnyPath();
    }

    private Value sourceReadFromStoppingAtANoughtByte(String source, Datatype wanted) {
        int endsAt = source.indexOf('\0');
        TranscodeResult read = Transcoder.transcode(
                endsAt < 0 ? source : source.substring(0, endsAt), this);
        if (!read.succeeded()) {
            throw new Raised(read.error().orElseThrow());
        }
        return read.values().orElseThrow().as(wanted);
    }

    private Value timeFromParts(List<Value> parts) {
        if (parts.isEmpty() || parts.size() > 3
                || !(parts.get(0) instanceof IntegerValue(long hours))) {
            throw Raised.badMakeArg(BlockValue.block(parts), "time!");
        }
        boolean negative = hours < 0;
        long seconds = Math.abs(hours) * 3600;
        long nanoseconds = 0;
        if (parts.size() > 1) {
            if (!(parts.get(1) instanceof IntegerValue(long minutes)) || minutes < 0) {
                throw Raised.badMakeArg(BlockValue.block(parts), "time!");
            }
            seconds += minutes * 60;
        }
        if (parts.size() > 2) {
            switch (parts.get(2)) {
                case IntegerValue whole when whole.magnitude() >= 0 ->
                        seconds += whole.magnitude();
                case DecimalValue fraction -> {
                    seconds += (long) fraction.quantity();
                    nanoseconds = Math.round(
                            (fraction.quantity() - (long) fraction.quantity()) * 1_000_000_000L);
                }
                default -> throw Raised.badMakeArg(BlockValue.block(parts), "time!");
            }
        }
        long total = seconds * 1_000_000_000L + nanoseconds;
        return TimeValue.ofNanoseconds(negative ? -total : total);
    }

    private StructSpec.LayoutRegistry structLayoutsKnown() {
        return layoutName -> registeredStructLayouts.select(WordValue.of(layoutName))
                instanceof BlockValue layout
                ? Optional.of(layout)
                : Optional.empty();
    }

    private Value structMadeFrom(Value from) {
        if (!(from instanceof BlockValue given)) {
            throw Raised.badMakeArg(from, "struct!");
        }
        List<Value> written = given.remaining();
        boolean carriesInitialValues = written.size() == 2
                && written.get(0) instanceof BlockValue
                && written.get(1) instanceof BlockValue;
        BlockValue layout = carriesInitialValues ? (BlockValue) written.getFirst() : given;
        StructValue made = StructValue.of(structLaidOutBy(layout));
        if (carriesInitialValues) {
            made.startedWith(written.get(1));
        }
        return made;
    }

    private StructSpec structLaidOutBy(BlockValue layout) {
        try {
            return StructSpec.of(layout, structLayoutsKnown());
        } catch (StructLayoutRefused refused) {
            throw Raised.of(refused.malconstructed()
                    ? EvaluationFailure.MALCONSTRUCT
                    : EvaluationFailure.INVALID_ARG, refused.offending());
        }
    }

    private Value addressBuiltFrom(BlockValue parts) {
        List<Value> written = parts.remaining();
        if (written.isEmpty()) {
            throw Raised.badMakeArg(parts, Datatype.EMAIL.literalSpelling());
        }
        String user = Molder.form(written.getFirst());
        if (written.size() == 1) {
            return StringValue.of(user, Datatype.EMAIL);
        }
        String host = written.subList(1, written.size()).stream()
                .map(Molder::form)
                .collect(Collectors.joining("."));
        return StringValue.of(user + "@" + host, Datatype.EMAIL);
    }

    private Value urlBuiltFrom(BlockValue parts) {
        List<Value> written = parts.remaining();
        if (written.isEmpty()) {
            throw Raised.badMakeArg(parts, Datatype.URL.literalSpelling());
        }
        String scheme = Molder.form(written.getFirst());
        String rest = written.subList(1, written.size()).stream()
                .map(Molder::form)
                .collect(Collectors.joining("/"));
        return StringValue.of(scheme + "://" + rest, Datatype.URL);
    }

    private Value bytesOfEach(BlockValue block) {
        List<Value> items = block.remaining();
        int[] octets = new int[items.size()];
        for (int at = 0; at < items.size(); at++) {
            octets[at] = anOctetIn(items.get(at));
        }
        return BinaryValue.of(octets);
    }

    private int anOctetIn(Value item) {
        if (!(item instanceof IntegerValue(long magnitude))) {
            throw Raised.of(EvaluationFailure.INVALID_ARG, item);
        }
        if (magnitude < 0 || magnitude > THE_LARGEST_OCTET) {
            throw Raised.of(EvaluationFailure.OUT_OF_RANGE, item);
        }
        return (int) magnitude;
    }

    private void refuseToBuildSomethingOutOfNothing(Datatype wanted, Value from) {
        if (from.datatype() != Datatype.NONE
                || wanted == Datatype.UNSET
                || wanted == Datatype.NONE
                || wanted == Datatype.LOGIC
                || wanted.isAnyBlock()) {
            return;
        }
        throw Raised.badMakeArg(from, wanted.literalSpelling());
    }

    private void refuseRoomForLessThanNothing(Datatype wanted, Value from) {
        if (!wanted.isSeries()
                || from.datatype() != Datatype.INTEGER && from.datatype() != Datatype.DECIMAL) {
            return;
        }
        if (Comparison.asDouble(from) < 0) {
            throw Raised.of(EvaluationFailure.OUT_OF_RANGE, from.toString());
        }
    }

    private void refuseMoreRoomThanASeriesCounts(Datatype wanted, Value from) {
        if (!wanted.isSeries() && wanted != Datatype.MAP) {
            return;
        }
        if (from.datatype() != Datatype.INTEGER && from.datatype() != Datatype.DECIMAL) {
            return;
        }
        if (Comparison.asDouble(from) > theMostItemsThatFitIn(wanted)) {
            throw Raised.of(EvaluationFailure.NO_MEMORY);
        }
    }

    private long theMostItemsThatFitIn(Datatype wanted) {
        return Integer.MAX_VALUE / bytesPerItemOf(wanted) - 1;
    }

    private int bytesPerItemOf(Datatype wanted) {
        if (wanted == Datatype.VECTOR) {
            return BYTES_A_VECTORS_NUMBER_TAKES;
        }
        return wanted.isAnyBlock() || wanted == Datatype.MAP ? BYTES_A_SLOT_TAKES : 1;
    }

    private Value whatTheHostHadRoomFor(Supplier<Value> allocating) {
        try {
            return allocating.get();
        } catch (OutOfMemoryError nothingLeftToGive) {
            throw Raised.of(EvaluationFailure.NO_MEMORY);
        }
    }

    private boolean countsAsTrue(Conversion asking, Value value) {
        return value.isTruthy() && !(asking.builds() && isNothingAtAll(value));
    }

    private boolean isNothingAtAll(Value value) {
        return switch (value) {
            case IntegerValue whole -> whole.magnitude() == 0;
            case DecimalValue number -> number.quantity() == 0.0;
            case MoneyValue amount -> amount.amount().signum() == 0;
            default -> false;
        };
    }

    private Value binaryBuiltFrom(Value value) {
        return switch (value) {
            case BinaryValue already -> already;
            case StringValue text when text.datatype() != Datatype.ISSUE ->
                    BinaryValue.ofBytes(text.text().getBytes(StandardCharsets.UTF_8));
            case IntegerValue whole -> BinaryValue.ofBytes(
                    ByteBuffer.allocate(Long.BYTES).putLong(whole.magnitude()).array());
            case DecimalValue fractional when fractional.datatype() == Datatype.DECIMAL ->
                    BinaryValue.ofBytes(ByteBuffer.allocate(Long.BYTES)
                            .putLong(Double.doubleToRawLongBits(fractional.quantity())).array());
            case MoneyValue amount -> BinaryValue.ofBytes(amount.toBytes());
            case BlockValue block when block.datatype() == Datatype.BLOCK -> bytesOfEach(block);
            case VectorValue vector -> BinaryValue.ofBytes(vector.octetsFromHere());
            case StructValue struct -> BinaryValue.ofBytes(struct.octets());
            case TupleValue segments -> BinaryValue.ofBytes(octetsOf(segments));
            case BitsetValue members -> BinaryValue.ofBytes(new BitsetActions(members).asOctets());
            case ImageValue picture -> BinaryValue.ofBytes(picture.everyPixel());
            case CharacterValue letter -> BinaryValue.ofBytes(
                    Character.toString(letter.codepoint()).getBytes(StandardCharsets.UTF_8));
            default -> throw Raised.of(EvaluationFailure.INVALID_ARG, Molder.mold(value));
        };
    }

    private byte[] octetsOf(TupleValue segments) {
        byte[] octets = new byte[segments.segmentCount()];
        for (int at = 0; at < octets.length; at++) {
            octets[at] = (byte) segments.octetAt(at + 1);
        }
        return octets;
    }

    private Value decimalBuiltFrom(Conversion asking, Datatype wanted, Value value) {
        return value.asDecimal(wanted, asking)
                .orElseGet(() -> scannedIntoADecimal(wanted, value));
    }

    private Value scannedIntoADecimal(Datatype wanted, Value value) {
        OptionalDouble scanned = theQuantityScannedFrom(wanted, value);
        if (scanned.isEmpty()) {
            throw Raised.badMakeArg(value, wanted.literalSpelling());
        }
        return value.inHundredths(wanted, scanned.getAsDouble());
    }

    private OptionalDouble theQuantityScannedFrom(Datatype wanted, Value value) {
        if (value instanceof StringValue text && text.datatype() == Datatype.STRING) {
            return decimalScannedFrom(
                    qualifiedNumberIn(text.text(), "a number", MOST_FRACTION_CHARACTERS),
                    wanted == Datatype.PERCENT);
        }
        if (value instanceof BlockValue parts) {
            return OptionalDouble.of(mantissaTimesTenTo(parts, wanted));
        }
        return OptionalDouble.empty();
    }

    private double mantissaTimesTenTo(BlockValue parts, Datatype wanted) {
        List<Value> both = parts.remaining();
        if (both.size() != 2) {
            throw Raised.badMakeArg(parts, wanted.literalSpelling());
        }
        double scaled = numberInTheBlock(both.get(0), wanted);
        double exponent = numberInTheBlock(both.get(1), wanted);
        while (exponent >= 1) {
            exponent--;
            scaled *= 10.0;
        }
        while (exponent <= -1) {
            exponent++;
            scaled /= 10.0;
        }
        return scaled;
    }

    private double numberInTheBlock(Value part, Datatype wanted) {
        return switch (part) {
            case IntegerValue(long magnitude) -> magnitude;
            case DecimalValue number -> number.quantity();
            default -> throw Raised.badMakeArg(part, wanted.literalSpelling());
        };
    }

    private Value wholeNumberFrom(Conversion asking, Value value) {
        return switch (value) {
            case IntegerValue whole -> whole;
            case LogicValue truth -> asking.builds()
                    ? IntegerValue.of(truth.truth() ? 1 : 0)
                    : badMakeArg(value, "integer!");
            case WordValue issue when issue.datatype() == Datatype.ISSUE -> hexNumberIn(issue);
            case StringValue text -> wholeNumberReadFrom(text);
            case CharacterValue character -> IntegerValue.of(character.codepoint());
            case BinaryValue bytes -> IntegerValue.of(bytes.bitsOfTheLastEightOctets());
            case DateValue moment -> IntegerValue.of(moment.wholeSecondsSinceTheEpoch());
            case DecimalValue number -> wholeNumberWithinRange(number.quantity());
            case MoneyValue amount -> IntegerValue.of(amount.asDeci().toLong());
            case TimeValue clock ->
                    IntegerValue.of(clock.nanoseconds() / TimeValue.NANOSECONDS_PER_SECOND);
            default -> throw Raised.badMakeArg(value, "integer!");
        };
    }

    private Value wholeNumberWithinRange(double quantity) {
        if (Double.isNaN(quantity)
                || quantity < -TOO_LARGE_FOR_A_WHOLE_NUMBER
                || quantity >= TOO_LARGE_FOR_A_WHOLE_NUMBER) {
            throw Raised.of(EvaluationFailure.OVERFLOW,
                    "no whole number is what " + quantity + " names");
        }
        return IntegerValue.of((long) quantity);
    }

    private Value hexNumberIn(WordValue issue) {
        String digits = issue.spelling();
        if (digits.isEmpty() || digits.length() > MOST_HEX_DIGITS) {
            throw Raised.badMakeArg(issue, "integer!");
        }
        try {
            return IntegerValue.of(Long.parseUnsignedLong(digits, 16));
        } catch (NumberFormatException notHexAtAll) {
            throw Raised.badMakeArg(issue, "integer!");
        }
    }

    private Value wordFrom(Value value, Datatype kind) {
        return switch (value) {
            case WordValue word -> WordValue.of(word.spelling(), kind);
            case LogicValue(boolean truth) -> WordValue.of(Boolean.toString(truth), kind);
            case CharacterValue letter -> WordValue.of(theWordASingleCharacterSpells(letter), kind);
            case StringValue text -> WordValue.of(spellingReadAs(text.text(), kind), kind);
            case DatatypeValue asked ->
                    WordValue.of(spellingReadAs(asked.represents().literalSpelling(), kind), kind);
            default -> throw Raised.of(EvaluationFailure.EXPECT_ARG,
                    "to " + kind.literalSpelling() + " wanted a string, not a "
                            + value.datatype().literalSpelling());
        };
    }

    private String theWordASingleCharacterSpells(CharacterValue letter) {
        if (!spellsAWordAlone(letter.codepoint())) {
            throw Raised.of(EvaluationFailure.BAD_CHAR, letter);
        }
        return Character.toString(letter.codepoint());
    }

    private boolean spellsAWordAlone(int codepoint) {
        return codepoint >= THE_FIRST_CODE_POINT_THE_SCANNER_TAKES_FOR_A_LETTER
                || Character.isLetter(codepoint)
                || THE_PUNCTUATION_THAT_SPELLS_A_WORD_ALONE.indexOf(codepoint) >= 0;
    }

    private String spellingReadAs(String text, Datatype kind) {
        int from = 0;
        while (from < text.length() && isLexicalSpace(text.charAt(from))) {
            from++;
        }
        int end = from;
        while (end < text.length() && !isLexicalSpace(text.charAt(end))) {
            end++;
        }
        String trimmed = text.substring(from, end);
        if (trimmed.isEmpty()) {
            throw Raised.of(EvaluationFailure.TOO_SHORT);
        }
        for (int after = end; after < text.length(); after++) {
            if (!isSpaceOrTab(text.charAt(after))) {
                throw Raised.of(EvaluationFailure.INVALID_CHARS);
            }
        }
        List<Value> read;
        try {
            read = Transcoder.transcode(kind == Datatype.ISSUE ? "#" + trimmed : trimmed)
                    .values()
                    .map(BlockValue::remaining)
                    .orElse(List.of());
        } catch (RuntimeException unreadable) {
            throw Raised.of(EvaluationFailure.INVALID_CHARS);
        }
        Datatype wanted = kind == Datatype.ISSUE ? Datatype.ISSUE : Datatype.WORD;
        if (read.size() != 1 || !(read.getFirst() instanceof WordValue word)
                || word.datatype() != wanted
                || !word.spelling().equals(trimmed)) {
            throw Raised.of(EvaluationFailure.INVALID_CHARS);
        }
        return word.spelling();
    }

    private Value tupleFrom(Value value) {
        return switch (value) {
            case TupleValue already -> already;
            case StringValue text -> tupleScannedFrom(text.text(), value);
            case BlockValue segments -> tupleOfSegments(segments);
            case BinaryValue octets -> tupleOfOctets(octets);
            case WordValue issue when issue.datatype() == Datatype.ISSUE ->
                    tupleOfHexPairs(issue.spelling(), value);
            default -> throw Raised.badMakeArg(value, "tuple!");
        };
    }

    private Value tupleOfSegments(BlockValue segments) {
        List<Value> items = segments.remaining();
        if (items.size() > TupleValue.MAXIMUM_SEGMENTS) {
            throw Raised.badMakeArg(segments, "tuple!");
        }
        int[] octets = new int[items.size()];
        for (int at = 0; at < items.size(); at++) {
            octets[at] = octetOf(items.get(at), segments);
        }
        return TupleValue.of(octets);
    }

    private int octetOf(Value item, Value whole) {
        long number = switch (item) {
            case IntegerValue wholeNumber -> wholeNumber.magnitude();
            case CharacterValue letter -> letter.codepoint();
            case DecimalValue fractional -> Math.round(Math.abs(fractional.quantity()))
                    * (fractional.quantity() < 0 ? -1 : 1);
            default -> throw Raised.badMakeArg(whole, "tuple!");
        };
        if (number < 0 || number > 255) {
            throw Raised.badMakeArg(whole, "tuple!");
        }
        return (int) number;
    }

    private Value tupleOfOctets(BinaryValue octets) {
        int width = Math.min(octets.lengthFromHere(), TupleValue.MAXIMUM_SEGMENTS);
        int[] kept = new int[width];
        for (int at = 0; at < width; at++) {
            kept[at] = octets.storage().at(octets.index() + at) & 0xFF;
        }
        return TupleValue.of(kept);
    }

    private Value tupleOfHexPairs(String digits, Value original) {
        if (digits.length() % 2 != 0 || digits.length() / 2 > TupleValue.MAXIMUM_SEGMENTS) {
            throw Raised.badMakeArg(original, "tuple!");
        }
        int[] octets = new int[digits.length() / 2];
        for (int at = 0; at < octets.length; at++) {
            try {
                octets[at] = Integer.parseInt(digits.substring(at * 2, at * 2 + 2), 16);
            } catch (NumberFormatException notHexadecimal) {
                throw Raised.badMakeArg(original, "tuple!");
            }
        }
        return TupleValue.of(octets);
    }

    private Value tupleScannedFrom(String text, Value original) {
        String[] parts = text.split("\\.", -1);
        if (text.isEmpty() || parts.length > TupleValue.MAXIMUM_SEGMENTS) {
            throw Raised.badMakeArg(original, "tuple!");
        }
        int width = Math.max(parts.length, TupleValue.MINIMUM_SHOWN_SEGMENTS);
        int[] octets = new int[width];
        for (int at = 0; at < parts.length; at++) {
            if (parts[at].isEmpty() && at == parts.length - 1) {
                break;
            }
            int written;
            try {
                written = Integer.parseInt(parts[at].trim());
            } catch (NumberFormatException notANumber) {
                throw Raised.badMakeArg(original, "tuple!");
            }
            if (written < 0 || written > 255) {
                throw Raised.badMakeArg(original, "tuple!");
            }
            octets[at] = written;
        }
        return TupleValue.of(octets);
    }

    private Value datatypeNamed(WordValue word, Value original) {
        for (Datatype candidate : Datatype.values()) {
            if (candidate.literalSpelling().equalsIgnoreCase(word.spelling())) {
                return DatatypeValue.of(candidate);
            }
        }
        throw Raised.badMakeArg(original, "datatype!");
    }

    private Value asCharacter(Value value) {
        return switch (value) {
            case CharacterValue already -> already;
            case StringValue text when text.text().isEmpty() ->
                    throw Raised.badMakeArg(value, "char!");
            case StringValue text -> CharacterValue.of(text.text().codePointAt(0));
            case BinaryValue octets -> characterLeadingThe(octets);
            case WordValue issue when issue.datatype() == Datatype.ISSUE ->
                    characterSpeltInHexBy(issue);
            case IntegerValue whole -> characterAt(whole.magnitude());
            case DecimalValue number -> characterAt((long) number.quantity());
            default -> throw Raised.badMakeArg(value, "char!");
        };
    }

    private Value characterAt(long asked) {
        if (asked < Integer.MIN_VALUE || asked > Integer.MAX_VALUE) {
            throw Raised.of(EvaluationFailure.OUT_OF_RANGE, IntegerValue.of(asked));
        }
        if (asked < 0 || asked > CharacterValue.MAXIMUM_CODEPOINT || isALoneSurrogate(asked)) {
            throw Raised.of(EvaluationFailure.INVALID_CHAR,
                    IntegerValue.of(Integer.toUnsignedLong((int) asked)));
        }
        return CharacterValue.of((int) asked);
    }

    private boolean isALoneSurrogate(long asked) {
        return asked <= Character.MAX_VALUE && Character.isSurrogate((char) asked);
    }

    private Value characterLeadingThe(BinaryValue octets) {
        byte[] bytes = octets.bytesFromHere();
        if (bytes.length == 0) {
            throw Raised.badMakeArg(octets, "char!");
        }
        int lead = bytes[0] & 0xFF;
        if (lead <= 0x80) {
            return CharacterValue.of(lead);
        }
        int continuations = continuationBytesFollowing(lead);
        if (continuations == 0 || bytes.length <= continuations) {
            throw Raised.badMakeArg(octets, "char!");
        }
        int codepoint = lead & (0x7F >> continuations);
        for (int at = 1; at <= continuations; at++) {
            int following = bytes[at] & 0xFF;
            if ((following & 0xC0) != 0x80) {
                throw Raised.badMakeArg(octets, "char!");
            }
            codepoint = (codepoint << 6) | (following & 0x3F);
        }
        if (codepoint > Character.MAX_CODE_POINT) {
            throw Raised.badMakeArg(octets, "char!");
        }
        return CharacterValue.of(codepoint);
    }

    private int continuationBytesFollowing(int lead) {
        if ((lead & 0xE0) == 0xC0) {
            return 1;
        }
        if ((lead & 0xF0) == 0xE0) {
            return 2;
        }
        if ((lead & 0xF8) == 0xF0) {
            return 3;
        }
        return 0;
    }

    private Value characterSpeltInHexBy(WordValue issue) {
        String spelling = issue.spelling();
        if (spelling.isEmpty() || spelling.length() > MOST_HEX_DIGITS_SCANNED) {
            throw Raised.badMakeArg(issue, "char!");
        }
        long codepoint;
        try {
            codepoint = Long.parseLong(spelling, 16);
        } catch (NumberFormatException notHexadecimal) {
            throw Raised.badMakeArg(issue, "char!");
        }
        if (codepoint < 0 || codepoint > Character.MAX_CODE_POINT) {
            throw Raised.badMakeArg(issue, "char!");
        }
        return CharacterValue.of((int) codepoint);
    }

    private Value asPair(Value value) {
        return switch (value) {
            case PairValue pair -> pair;
            case IntegerValue whole -> PairValue.square(whole.magnitude());
            case DecimalValue quantity when quantity.datatype() != Datatype.PERCENT ->
                    PairValue.square(quantity.quantity());
            case StringValue text -> readPair(text.text());
            case BlockValue block when block.datatype() == Datatype.BLOCK ->
                    pairOf(block.remaining());
            default -> throw Raised.badMakeArg(value, "pair!");
        };
    }

    private Value pairOf(List<Value> halves) {
        if (halves.size() != 2) {
            throw Raised.badMakeArg(BlockValue.block(halves), "pair!");
        }
        return PairValue.of(Comparison.asDouble(halves.get(0)), Comparison.asDouble(halves.get(1)));
    }

    private Value readPair(String text) {
        List<Value> read = Transcoder.transcode(text).values()
                .map(BlockValue::remaining)
                .orElse(List.of());
        if (read.size() != 1 || !(read.getFirst() instanceof PairValue pair)) {
            throw Raised.badMakeArg(StringValue.of(text), "pair!");
        }
        return pair;
    }

    private Value asMoney(Conversion asking, Value value) {
        return switch (value) {
            case MoneyValue already -> already;
            case IntegerValue whole -> new MoneyValue(new Deci(whole.magnitude()));
            case DecimalValue quantity -> new MoneyValue(new Deci(quantity.quantity()));
            case StringValue text -> readMoney(text);
            case BinaryValue bytes -> MoneyValue.fromBytes(bytes.bytesFromHere());
            case LogicValue truth when asking.builds() -> new MoneyValue(new Deci(truth.truth() ? 1 : 0));
            default -> throw Raised.badMakeArg(value, "money!");
        };
    }

    private MoneyValue readMoney(StringValue text) {
        String written = qualifiedNumberIn(text.text(), "a money", MOST_MONEY_CHARACTERS);
        return new DeciReading(written).theWholeOf()
                .map(MoneyValue::new)
                .orElseThrow(() -> Raised.badMakeArg(text, "money!"));
    }

    private boolean isANumberNotPercentage(Value given) {
        return given instanceof IntegerValue
                || (given instanceof DecimalValue && given.datatype() != Datatype.PERCENT);
    }

    private Value mapFrom(Value source) {
        if (isANumberNotPercentage(source)) {
            throwWhenLessThanNoPairs(source);
            refuseMoreRoomThanASeriesCounts(Datatype.MAP, source);
            return MapValue.empty();
        }
        List<Value> pairs = MapActions.pairsOffered(source);
        if (pairs == null) {
            throw Raised.badMakeArg(source, "map!");
        }
        return MapActions.madeFrom(source, pairs);
    }

    private void throwWhenLessThanNoPairs(Value given) {
        if (Comparison.asDouble(given) < 0) {
            throw Raised.of(EvaluationFailure.OUT_OF_RANGE,
                    "a map cannot have room for " + Molder.form(given) + " pairs");
        }
    }

    private String qualifiedNumberIn(String text, String reading, int mostCharacters) {
        int start = 0;
        while (start < text.length() && isLexicalSpace(text.charAt(start))) {
            start++;
        }
        int past = start;
        while (past < text.length() && !isLexicalSpace(text.charAt(past))) {
            if (text.charAt(past) > MOST_LETTERS_ARE_ONE_BYTE) {
                throw Raised.of(EvaluationFailure.INVALID_CHARS,
                        "\"" + text + "\" holds a character a number may not");
            }
            past++;
            if (past - start > mostCharacters) {
                throw Raised.of(EvaluationFailure.TOO_LONG,
                        "\"" + text + "\" is longer than a written number may be");
            }
        }
        if (past == start) {
            throw Raised.of(EvaluationFailure.TOO_SHORT,
                    "there is nothing in \"" + text + "\" to read as " + reading);
        }
        for (int after = past; after < text.length(); after++) {
            if (!isSpaceOrTab(text.charAt(after))) {
                throw Raised.of(EvaluationFailure.INVALID_CHARS,
                        "\"" + text + "\" has more than one value in it");
            }
        }
        return text.substring(start, past);
    }

    private static final char END_OF_FILE = 0;

    private boolean isLexicalSpace(char letter) {
        return (letter <= ' ' || letter == MOST_LETTERS_ARE_ONE_BYTE)
                && letter != '\n' && letter != '\r' && letter != END_OF_FILE;
    }

    private boolean isSpaceOrTab(char letter) {
        return letter == ' ' || letter == '\t';
    }

    private OptionalDouble decimalScannedFrom(String written, boolean percentAllowed) {
        String body = written;
        if (body.endsWith("%")) {
            if (!percentAllowed) {
                return OptionalDouble.empty();
            }
            body = body.substring(0, body.length() - 1);
        }
        OptionalDouble endless = endlessNumberIn(body.replace("'", ""));
        if (endless.isPresent()) {
            return endless;
        }
        return numberRewrittenForTheJvm(body)
                .map(plain -> OptionalDouble.of(Double.parseDouble(plain)))
                .orElseGet(OptionalDouble::empty);
    }

    private Optional<String> numberRewrittenForTheJvm(String written) {
        String body = written.replace("'", "").replaceFirst(",", ".");
        return WRITTEN_DECIMAL.matcher(body).matches()
                ? Optional.of(EMPTY_EXPONENT.matcher(body).replaceFirst(""))
                : Optional.empty();
    }

    private OptionalDouble endlessNumberIn(String body) {
        int hash = body.indexOf('#');
        if (hash < 0) {
            return OptionalDouble.empty();
        }
        boolean negative = body.charAt(0) == '-';
        String afterTheHash = body.substring(hash + 1);
        if (afterTheHash.equalsIgnoreCase("INF")) {
            return OptionalDouble.of(negative ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY);
        }
        return afterTheHash.equalsIgnoreCase("NAN")
                ? OptionalDouble.of(Double.NaN)
                : OptionalDouble.empty();
    }

    private Value wholeNumberReadFrom(StringValue text) {
        String withoutSeparators = qualifiedNumberIn(
                text.text(), "an integer", MOST_WHOLE_NUMBER_CHARACTERS).replace("'", "");
        try {
            return IntegerValue.of(Long.parseLong(withoutSeparators));
        } catch (NumberFormatException notAWholeNumber) {
            return truncatedDecimal(withoutSeparators, text);
        }
    }

    private Value truncatedDecimal(String candidate, StringValue original) {
        if (candidate.indexOf('.') < 0) {
            throw Raised.badMakeArg(original, "integer!");
        }
        double asNumber;
        try {
            asNumber = Double.parseDouble(candidate);
        } catch (NumberFormatException notANumberEither) {
            throw Raised.badMakeArg(original, "integer!");
        }
        if (!(Math.abs(asNumber) < TOO_LARGE_FOR_A_WHOLE_NUMBER)) {
            throw Raised.badMakeArg(original, "integer!");
        }
        return IntegerValue.of((long) asNumber);
    }

    private Value moduleFromHeaderAndWords(Value value) {
        if (!(value instanceof BlockValue parts)) {
            throw Raised.badMakeArg(value, "module!");
        }
        List<Value> given = parts.remaining();
        if (given.size() < 2
                || !(given.get(0) instanceof ObjectValue header)
                || !(given.get(1) instanceof ObjectValue(Context fields))) {
            throw Raised.badMakeArg(value, "module!");
        }
        return new ModuleValue(fields, header);
    }

    private Value aTaskMadeFrom(Value value) {
        if (!(value instanceof BlockValue given) || given.datatype() != Datatype.BLOCK) {
            throw Raised.badMakeArg(value, "task!");
        }
        List<Value> written = given.remaining();
        if (written.isEmpty() || !(written.getFirst() instanceof BlockValue spec)
                || spec.datatype() != Datatype.BLOCK) {
            return TaskValue.running(given);
        }
        if (written.size() < 2 || !(written.get(1) instanceof BlockValue body)
                || body.datatype() != Datatype.BLOCK) {
            throw Raised.badMakeArg(value, "task!");
        }
        TaskValue task = TaskValue.running(body);
        List<Value> fields = spec.remaining();
        for (int at = 0; at + 1 < fields.size(); at++) {
            if (fields.get(at) instanceof WordValue field
                    && field.datatype() == Datatype.SET_WORD
                    && task.context().holds(field.canonical())) {
                task.context().register(field.canonical(), fields.get(at + 1));
            }
        }
        return task;
    }

    private Value aDurationOfSeconds(Value value) {
        double seconds = Comparison.asDouble(value);
        if (seconds < -MOST_SECONDS_A_DURATION_HOLDS || seconds > MOST_SECONDS_A_DURATION_HOLDS) {
            throw Raised.of(EvaluationFailure.OUT_OF_RANGE, value);
        }
        return TimeValue.ofNanoseconds(TimeActions.wholeNanosecondsOf(value));
    }

    private Value aTimeOfHoursMinutesAndSeconds(BlockValue parts) {
        List<Value> given = parts.remaining();
        if (given.isEmpty() || given.size() > 3
                || !(given.getFirst() instanceof IntegerValue(long hours))) {
            throw Raised.badMakeArg(parts, "time!");
        }
        boolean negated = hours < 0;
        long seconds = whatFitsInThirtyTwoBits(Math.abs(hours), parts) * 3600L;
        double fraction = 0.0;
        for (int at = 1; at < given.size(); at++) {
            if (seconds > MOST_SECONDS_A_TIME_HOLDS) {
                throw Raised.badMakeArg(parts, "time!");
            }
            Value part = given.get(at);
            if (at == 2 && part.datatype() == Datatype.DECIMAL) {
                fraction = ((DecimalValue) part).quantity();
                if (seconds + (long) fraction + 1 > MOST_SECONDS_A_TIME_HOLDS) {
                    throw Raised.badMakeArg(parts, "time!");
                }
                break;
            }
            if (!(part instanceof IntegerValue(long magnitude)) || magnitude < 0) {
                throw Raised.badMakeArg(parts, "time!");
            }
            seconds += whatFitsInThirtyTwoBits(magnitude, parts) * (at == 1 ? 60L : 1L);
        }
        if (seconds > MOST_SECONDS_A_TIME_HOLDS) {
            throw Raised.badMakeArg(parts, "time!");
        }
        long nanoseconds = seconds * TimeValue.NANOSECONDS_PER_SECOND
                + Math.round(fraction * TimeValue.NANOSECONDS_PER_SECOND);
        return TimeValue.ofNanoseconds(negated ? -nanoseconds : nanoseconds);
    }

    private long whatFitsInThirtyTwoBits(long magnitude, Value about) {
        if (magnitude > Integer.MAX_VALUE) {
            throw Raised.of(EvaluationFailure.OUT_OF_RANGE, about);
        }
        return magnitude;
    }

    private Value theTimeScannedFrom(String text, Value given) {
        OptionalLong nanoseconds = timeScannedFrom(theRunOfCharactersBetweenTheSpacesOf(text));
        if (nanoseconds.isEmpty()) {
            throw Raised.badMakeArg(given, "time!");
        }
        return TimeValue.ofNanoseconds(nanoseconds.getAsLong());
    }

    private String theRunOfCharactersBetweenTheSpacesOf(String text) {
        int from = 0;
        while (from < text.length() && isSpaceOrTab(text.charAt(from))) {
            from++;
        }
        int to = from;
        while (to < text.length() && !isSpaceOrTab(text.charAt(to))) {
            if (text.charAt(to) > ASCII_ENDS_AT) {
                throw Raised.of(EvaluationFailure.INVALID_CHARS, text);
            }
            to++;
        }
        if (to == from) {
            throw Raised.of(EvaluationFailure.TOO_SHORT, text);
        }
        if (to - from > LONGEST_TIME_A_STRING_MAY_SPELL) {
            throw Raised.of(EvaluationFailure.TOO_LONG, text);
        }
        for (int after = to; after < text.length(); after++) {
            if (!isSpaceOrTab(text.charAt(after))) {
                throw Raised.of(EvaluationFailure.INVALID_CHARS, text);
            }
        }
        return text.substring(from, to);
    }

    private OptionalLong timeScannedFrom(String content) {
        ScanningATime scanning = new ScanningATime(content);
        return scanning.readsATime()
                ? OptionalLong.of(scanning.nanoseconds())
                : OptionalLong.empty();
    }

    private byte[] withSurrogatePairsJoined(byte[] bytes) {
        byte[] joined = new byte[bytes.length];
        int written = 0;
        int at = 0;
        while (at < bytes.length) {
            int high = surrogateAt(bytes, at, 0xA0);
            int low = high < 0 ? -1 : surrogateAt(bytes, at + 3, 0xB0);
            if (low < 0) {
                joined[written] = bytes[at];
                written++;
                at++;
                continue;
            }
            written = fourBytesOf(joined, written,
                    0x10000 + ((high - 0xD800) << 10) + (low - 0xDC00));
            at += 6;
        }
        return Arrays.copyOf(joined, written);
    }

    private int surrogateAt(byte[] bytes, int at, int leadingHalf) {
        if (at + 2 >= bytes.length || (bytes[at] & 0xFF) != 0xED) {
            return -1;
        }
        int second = bytes[at + 1] & 0xFF;
        int third = bytes[at + 2] & 0xFF;
        if (second < leadingHalf || second >= leadingHalf + 0x10
                || third < 0x80 || third > 0xBF) {
            return -1;
        }
        return 0xD000 | (second & 0x3F) << 6 | third & 0x3F;
    }

    private int fourBytesOf(byte[] joined, int written, int codepoint) {
        joined[written] = (byte) (0xF0 | codepoint >> 18);
        joined[written + 1] = (byte) (0x80 | codepoint >> 12 & 0x3F);
        joined[written + 2] = (byte) (0x80 | codepoint >> 6 & 0x3F);
        joined[written + 3] = (byte) (0x80 | codepoint & 0x3F);
        return written + 4;
    }

    private String textBehindTheMark(byte[] bytes, int marked) {
        Charset theMarkAnnounces = switch (marked) {
            case 8 -> StandardCharsets.UTF_8;
            case 16 -> StandardCharsets.UTF_16BE;
            case -16 -> StandardCharsets.UTF_16LE;
            case 32 -> Charset.forName("UTF-32BE");
            default -> Charset.forName("UTF-32LE");
        };
        int width = Math.abs(marked) == 8 ? 3 : Math.abs(marked) / 8;
        try {
            return theMarkAnnounces.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes, width, bytes.length - width))
                    .toString();
        } catch (CharacterCodingException notText) {
            throw Raised.of(EvaluationFailure.INVALID_UTF, "binary");
        }
    }
}
