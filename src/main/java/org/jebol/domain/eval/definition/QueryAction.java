package org.jebol.domain.eval.definition;

import org.jebol.domain.date.part.DatePart;
import org.jebol.domain.eval.Evaluator;
import org.jebol.domain.eval.FileInformation;
import org.jebol.domain.eval.FilePort;
import org.jebol.domain.eval.GrantedServices;
import org.jebol.domain.eval.Ports;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.eval.VectorQuery;
import org.jebol.domain.host.HostService;
import org.jebol.domain.value.BlockValue;
import org.jebol.domain.value.Context;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.DatatypeValue;
import org.jebol.domain.value.DateValue;
import org.jebol.domain.value.EvaluationFailure;
import org.jebol.domain.value.HandleValue;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.NoneValue;
import org.jebol.domain.value.ObjectValue;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.PortValue;
import org.jebol.domain.value.Raised;
import org.jebol.domain.value.StringValue;
import org.jebol.domain.value.TimeValue;
import org.jebol.domain.value.UnsetValue;
import org.jebol.domain.value.Value;
import org.jebol.domain.value.VectorValue;
import org.jebol.domain.value.WordValue;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class QueryAction extends PortAction {

    private static final List<String> CONSOLE_MEASUREMENTS =
            List.of("buffer-cols", "buffer-rows", "window-cols", "window-rows", "length");

    private static final int COLUMNS_A_TERMINAL_IS_ASSUMED_TO_HAVE = 80;

    private static final int SECONDS_A_MINUTE = 60;

    private static final String THE_NAMES_THEMSELVES = "words";

    public QueryAction(GrantedServices granted, Ports ports) {
        super(granted, ports);
    }

    @Override
    public String nativeName() {
        return "query";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(
                Parameter.required("target", Set.of(Datatype.FILE, Datatype.DATE,
                        Datatype.HANDLE, Datatype.PORT, Datatype.URL,
                        Datatype.BLOCK, Datatype.WORD, Datatype.VECTOR)),
                Parameter.required("field",
                        Set.of(Datatype.WORD, Datatype.BLOCK, Datatype.NONE, Datatype.DATATYPE)));
    }

    @Override
    public Set<String> refinementsDeclaredApart() {
        return Set.of("mode");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            Optional<Value> itsOwn = evaluator.theRebolActorsAnswer(nativeName(), arguments, refinements);
            if (itsOwn.isPresent()) {
                return itsOwn.get();
            }
            Value target = arguments.getFirst();
            Value field = arguments.get(1);
            return switch (target) {
                case VectorValue vector -> new TheFieldsOfAVector(vector).answering(field, evaluator);
                case DateValue date -> new TheFieldsOfADate(date).answering(field, evaluator);
                case HandleValue handle -> new TheFieldsOfAHandle(handle).answering(field, evaluator);
                case PortValue console when console.schemeName().equals("console") ->
                        new TheMeasurementsOfTheConsole().answering(field, evaluator);
                default -> field instanceof NoneValue
                        ? theNamesThatPortMayBeAskedFor(target, evaluator, context)
                        : aFileOrARoutedPortAsked(target, field, evaluator, context);
            };
        };
    }

    private Value aFileOrARoutedPortAsked(Value target, Value field, Evaluator evaluator, Context context) {
        if (target.datatype() == Datatype.FILE) {
            return aFileAsked(((StringValue) target).text(), field, evaluator);
        }
        PortValue port = ports.portMadeFor(target, evaluator, context);
        return evaluator.theRebolActorsAnswer(nativeName(), List.of(port, field), Set.of())
                .orElseGet(() -> aNativelyServedPortAsked(port, field, evaluator));
    }

    private Value aNativelyServedPortAsked(PortValue port, Value field, Evaluator evaluator) {
        if (!port.isAFile()) {
            throw ports.noActionFor(nativeName());
        }
        return aFileAsked(ports.pathOf(port), field, evaluator);
    }

    private Value aFileAsked(String path, Value field, Evaluator evaluator) {
        granted.require(HostService.FILES);
        if (path.isEmpty()) {
            return NoneValue.none();
        }
        FilePort files = evaluator.files();
        return ports.throughTheFileSystem(() -> files.informationAbout(path)
                .<Value>map(about -> new TheFieldsOfAFile(about,
                                Optional.ofNullable(files.canonicalPathOf(path)).orElse(path))
                        .answering(field, evaluator))
                .orElseGet(NoneValue::none));
    }

    private Value theNamesThatPortMayBeAskedFor(Value target, Evaluator evaluator, Context context) {
        Value described = ports.portMadeFor(target, evaluator, context).context().valueAt("scheme", "info");
        if (!(described instanceof ObjectValue(Context info))) {
            return BlockValue.block(List.of());
        }
        return BlockValue.block(info.slots().stream()
                .filter(slot -> !slot.canonical().equals("self"))
                .<Value>map(slot -> WordValue.of(slot.spelling()))
                .toList());
    }

    private abstract static class TheFields {

        abstract List<String> names();

        abstract Optional<Value> fieldNamed(String asked);

        abstract Value unknownAlone(WordValue asked);

        Value unknownInABlock(WordValue asked) {
            throw Raised.of(EvaluationFailure.INVALID_ARG, asked);
        }

        boolean answersItsNames() {
            return false;
        }

        Value answering(Value field, Evaluator evaluator) {
            return switch (field) {
                case WordValue asked when asked.canonical().equals(THE_NAMES_THEMSELVES)
                        && answersItsNames() -> theNames();
                case WordValue asked -> fieldNamed(asked.canonical()).orElseGet(() -> unknownAlone(asked));
                case BlockValue several -> eachOf(several);
                case NoneValue nothing -> theNames();
                default -> everyFieldAsAnObject(evaluator);
            };
        }

        private Value theNames() {
            return BlockValue.block(names().stream().<Value>map(WordValue::of).toList());
        }

        private Value eachOf(BlockValue several) {
            List<Value> answer = new ArrayList<>();
            for (Value item : several.remaining()) {
                if (!(item instanceof WordValue asked)) {
                    throw Raised.of(EvaluationFailure.INVALID_ARG, item);
                }
                if (asked.datatype() != Datatype.GET_WORD) {
                    answer.add(asked.as(Datatype.SET_WORD));
                }
                answer.add(fieldNamed(asked.canonical()).orElseGet(() -> unknownInABlock(asked)));
            }
            return BlockValue.block(answer);
        }

        Value everyFieldAsAnObject(Evaluator evaluator) {
            ObjectValue described = new ObjectValue(Context.childOf(evaluator.systemContext()));
            described.context().register("self", described);
            return withEveryFieldIn(described);
        }

        ObjectValue withEveryFieldIn(ObjectValue described) {
            for (String part : names()) {
                described.context().register(part, fieldInTheObject(part));
            }
            return described;
        }

        Value fieldInTheObject(String part) {
            return fieldNamed(part).orElseGet(NoneValue::none);
        }

        Raised cannotUse(WordValue asked, Datatype datatype) {
            return Raised.of(EvaluationFailure.CANNOT_USE, asked, DatatypeValue.of(datatype));
        }
    }

    private abstract static class TheFieldsOfAnObjectWithoutASelf extends TheFields {

        @Override
        Value everyFieldAsAnObject(Evaluator evaluator) {
            return withEveryFieldIn(new ObjectValue(Context.root()));
        }
    }

    private static final class TheFieldsOfAVector extends TheFields {

        private final VectorValue vector;

        TheFieldsOfAVector(VectorValue vector) {
            this.vector = vector;
        }

        @Override
        List<String> names() {
            return VectorQuery.FIELDS;
        }

        @Override
        Optional<Value> fieldNamed(String asked) {
            return VectorQuery.field(vector, asked);
        }

        @Override
        Value unknownAlone(WordValue asked) {
            throw cannotUse(asked, Datatype.VECTOR);
        }
    }

    private static final class TheFieldsOfADate extends TheFields {

        private final DateValue date;

        TheFieldsOfADate(DateValue date) {
            this.date = date;
        }

        @Override
        List<String> names() {
            return DatePart.partNames();
        }

        @Override
        Optional<Value> fieldNamed(String asked) {
            return names().contains(asked)
                    ? Optional.of(DatePart.readFrom(date, WordValue.of(asked)))
                    : Optional.empty();
        }

        @Override
        Value unknownAlone(WordValue asked) {
            return UnsetValue.unset();
        }

        @Override
        Value unknownInABlock(WordValue asked) {
            return NoneValue.none();
        }

        @Override
        boolean answersItsNames() {
            return true;
        }
    }

    private static final class TheFieldsOfAHandle extends TheFields {

        private final HandleValue handle;

        TheFieldsOfAHandle(HandleValue handle) {
            this.handle = handle;
        }

        @Override
        List<String> names() {
            return List.of("type");
        }

        @Override
        Optional<Value> fieldNamed(String asked) {
            return names().contains(asked) ? Optional.of(WordValue.of(handle.typeName())) : Optional.empty();
        }

        @Override
        Value unknownAlone(WordValue asked) {
            throw cannotUse(asked, Datatype.HANDLE);
        }

        @Override
        boolean answersItsNames() {
            return true;
        }
    }

    private static final class TheMeasurementsOfTheConsole extends TheFieldsOfAnObjectWithoutASelf {

        private static final int A_MEASURE_THE_HOST_DOES_NOT_REPORT = 0;

        @Override
        List<String> names() {
            return CONSOLE_MEASUREMENTS;
        }

        @Override
        Optional<Value> fieldNamed(String asked) {
            if (!names().contains(asked)) {
                return Optional.empty();
            }
            return Optional.of(IntegerValue.of(asked.equals("window-cols")
                    ? COLUMNS_A_TERMINAL_IS_ASSUMED_TO_HAVE
                    : A_MEASURE_THE_HOST_DOES_NOT_REPORT));
        }

        @Override
        Value fieldInTheObject(String part) {
            return IntegerValue.of(A_MEASURE_THE_HOST_DOES_NOT_REPORT);
        }

        @Override
        Value unknownAlone(WordValue asked) {
            throw Raised.of(EvaluationFailure.INVALID_ARG, asked);
        }
    }

    private static final class TheFieldsOfAFile extends TheFieldsOfAnObjectWithoutASelf {

        private final FileInformation about;
        private final String wholePath;

        TheFieldsOfAFile(FileInformation about, String wholePath) {
            this.about = about;
            this.wholePath = wholePath;
        }

        @Override
        List<String> names() {
            return List.of("name", "size", "type", "date", "modified", "accessed", "created");
        }

        @Override
        Optional<Value> fieldNamed(String asked) {
            return switch (asked) {
                case "name" -> Optional.of(StringValue.of(wholePath, Datatype.FILE));
                case "size" -> Optional.of(about.size().<Value>map(IntegerValue::of).orElseGet(NoneValue::none));
                case "type" -> Optional.of(WordValue.of(about.isDirectory() ? "dir" : "file"));
                case "date", "modified" -> Optional.of(asDateValue(about.modified()));
                case "accessed" -> Optional.of(asDateValue(about.accessed()));
                case "created" -> Optional.of(asDateValue(about.created()));
                default -> Optional.empty();
            };
        }

        @Override
        Value unknownAlone(WordValue asked) {
            throw Raised.of(EvaluationFailure.INVALID_ARG, asked);
        }

        @Override
        Value fieldInTheObject(String part) {
            return part.equals("date") ? NoneValue.none() : super.fieldInTheObject(part);
        }

        private Value asDateValue(Optional<Instant> moment) {
            return moment.<Value>map(when -> {
                ZonedDateTime here = ZonedDateTime.ofInstant(when, ZoneId.systemDefault());
                return new DateValue(here.getYear(), here.getMonthValue(), here.getDayOfMonth(),
                        Optional.of(TimeValue.ofNanoseconds(here.toLocalTime().toNanoOfDay())),
                        Optional.of(here.getOffset().getTotalSeconds() / SECONDS_A_MINUTE));
            }).orElseGet(NoneValue::none);
        }
    }
}
