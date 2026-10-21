package org.jebol.domain.eval.actions;

import org.jebol.domain.date.part.DatePart;
import org.jebol.domain.eval.Evaluator;
import org.jebol.domain.eval.QueriedFields;
import org.jebol.domain.eval.ports.ServesPortActions;
import org.jebol.domain.host.FileInformation;
import org.jebol.domain.host.FilePort;
import org.jebol.domain.eval.GrantedServices;
import org.jebol.domain.eval.ports.Ports;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.eval.security.PolicyAccess;
import org.jebol.domain.eval.VectorQuery;
import org.jebol.domain.host.HostService;
import org.jebol.domain.value.BlockValue;
import org.jebol.domain.value.Context;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.UrlValue;
import org.jebol.domain.value.DateValue;
import org.jebol.domain.value.EvaluationFailure;
import org.jebol.domain.value.HandleValue;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.NoneValue;
import org.jebol.domain.value.ObjectValue;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.PortValue;
import org.jebol.domain.value.Raised;
import org.jebol.domain.value.FileValue;
import org.jebol.domain.value.TimeValue;
import org.jebol.domain.value.UnsetValue;
import org.jebol.domain.value.Value;
import org.jebol.domain.value.VectorValue;
import org.jebol.domain.value.AnyWordValue;
import org.jebol.domain.value.WordValue;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class QueryAction extends PortAction {

    private static final List<String> CONSOLE_MEASUREMENTS =
            List.of("buffer-cols", "buffer-rows", "window-cols", "window-rows", "length");

    private static final int COLUMNS_A_TERMINAL_IS_ASSUMED_TO_HAVE = 80;

    private static final int SECONDS_A_MINUTE = 60;

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
                Parameter.of("target", Set.of(FileValue.TYPE, DateValue.TYPE,
                        HandleValue.TYPE, PortValue.TYPE, UrlValue.TYPE,
                        BlockValue.TYPE, WordValue.TYPE, VectorValue.TYPE)),
                Parameter.of("field",
                        Set.of(WordValue.TYPE, BlockValue.TYPE, NoneValue.TYPE, Datatype.TYPE)));
    }

    @Override
    public Set<String> refinementsDeclaredApart() {
        return Set.of("mode");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            Optional<Value> itsOwn = evaluator.askTheRebolActor(nativeName(), arguments, refinements);
            if (itsOwn.isPresent()) {
                return itsOwn.get();
            }
            Value target = arguments.getFirst();
            Value field = arguments.get(1);
            return switch (target) {
                case VectorValue vector -> new TheFieldsOfAVector(vector).answer(field, evaluator);
                case DateValue date -> new TheFieldsOfADate(date).answer(field, evaluator);
                case HandleValue handle -> new TheFieldsOfAHandle(handle).answer(field, evaluator);
                case PortValue console when console.schemeName().equals("console") ->
                        new TheMeasurementsOfTheConsole().answer(field, evaluator);
                default -> field instanceof NoneValue
                        ? listTheNamesThatPortMayBeAskedFor(target, evaluator, context)
                        : askAFileOrARoutedPort(target, field, evaluator, context);
            };
        };
    }

    private Value askAFileOrARoutedPort(Value target, Value field, Evaluator evaluator, Context context) {
        if (target instanceof FileValue file) {
            return askAFile(file.text(), field, evaluator);
        }
        PortValue port = ports.makeAPortFor(target, evaluator, context);
        return evaluator.askTheRebolActor(nativeName(), List.of(port, field), Set.of())
                .orElseGet(() -> askANativelyServedPort(port, field, evaluator));
    }

    private Value askANativelyServedPort(PortValue port, Value field, Evaluator evaluator) {
        if (!port.isAFile()) {
            throw ports.refuseWithNoActionFor(nativeName());
        }
        return askAFile(ports.readThePathOf(port), field, evaluator);
    }

    private Value askAFile(String path, Value field, Evaluator evaluator) {
        granted.require(HostService.FILES);
        if (path.isEmpty()) {
            return NoneValue.none();
        }
        if (ports.namesADirectory(path, evaluator)) {
            ports.requireTheFilePolicy(path, evaluator, Set.of(PolicyAccess.READ));
        }
        FilePort files = evaluator.ports().files();
        return ports.runThroughTheFileSystem(() -> files.describe(path)
                .<Value>map(about -> new TheFieldsOfAFile(about,
                                files.locationFor(path))
                        .answer(field, evaluator))
                .orElseGet(NoneValue::none));
    }

    private Value listTheNamesThatPortMayBeAskedFor(Value target, Evaluator evaluator, Context context) {
        PortValue port = ports.makeAPortFor(target, evaluator, context);
        return listTheNamesItsJavaActorGives(port, evaluator)
                .orElseGet(() -> listTheNamesInTheSchemesInfo(port));
    }

    private Optional<Value> listTheNamesItsJavaActorGives(PortValue port, Evaluator evaluator) {
        if (!(port.readFieldValue("actor") instanceof ServesPortActions served)) {
            return Optional.empty();
        }
        return served.answers(nativeName(), List.of(port, NoneValue.none()), Set.of(), evaluator);
    }

    private Value listTheNamesInTheSchemesInfo(PortValue port) {
        Value described = port.context().readValueAt("scheme", "info");
        if (!(described instanceof ObjectValue(Context info))) {
            return BlockValue.of(List.of());
        }
        return BlockValue.of(info.slots().stream()
                .<Value>map(slot -> WordValue.of(slot.spelling()))
                .toList());
    }

    private abstract static class TheFieldsOfAnObjectWithoutASelf extends QueriedFields {

        @Override
        protected Value gatherEveryFieldIntoAnObject(Evaluator evaluator) {
            return withEveryFieldIn(new ObjectValue(Context.root()));
        }
    }

    private static final class TheFieldsOfAVector extends QueriedFields {

        private final VectorValue vector;

        TheFieldsOfAVector(VectorValue vector) {
            this.vector = vector;
        }

        @Override
        protected List<String> names() {
            return VectorQuery.FIELDS;
        }

        @Override
        protected Optional<Value> findFieldNamed(String asked) {
            return VectorQuery.readTheField(vector, asked);
        }

        @Override
        protected Value answerAnUnknownAlone(AnyWordValue asked) {
            throw cannotUse(asked, VectorValue.TYPE);
        }
    }

    private static final class TheFieldsOfADate extends QueriedFields {

        private final DateValue date;

        TheFieldsOfADate(DateValue date) {
            this.date = date;
        }

        @Override
        protected List<String> names() {
            return DatePart.partNames();
        }

        @Override
        protected Optional<Value> findFieldNamed(String asked) {
            return names().contains(asked)
                    ? Optional.of(DatePart.readFrom(date, WordValue.of(asked)))
                    : Optional.empty();
        }

        @Override
        protected Value answerAnUnknownAlone(AnyWordValue asked) {
            return UnsetValue.unset();
        }

        @Override
        protected Value answerAnUnknownInABlock(AnyWordValue asked) {
            return NoneValue.none();
        }

        @Override
        protected boolean answersItsNames() {
            return true;
        }
    }

    private static final class TheFieldsOfAHandle extends QueriedFields {

        private final HandleValue handle;

        TheFieldsOfAHandle(HandleValue handle) {
            this.handle = handle;
        }

        @Override
        protected List<String> names() {
            return List.of("type");
        }

        @Override
        protected Optional<Value> findFieldNamed(String asked) {
            return names().contains(asked) ? Optional.of(WordValue.of(handle.typeName())) : Optional.empty();
        }

        @Override
        protected Value answerAnUnknownAlone(AnyWordValue asked) {
            throw cannotUse(asked, HandleValue.TYPE);
        }

        @Override
        protected boolean answersItsNames() {
            return true;
        }
    }

    private static final class TheMeasurementsOfTheConsole extends TheFieldsOfAnObjectWithoutASelf {

        private static final int A_MEASURE_THE_HOST_DOES_NOT_REPORT = 0;

        @Override
        protected List<String> names() {
            return CONSOLE_MEASUREMENTS;
        }

        @Override
        protected Optional<Value> findFieldNamed(String asked) {
            if (!names().contains(asked)) {
                return Optional.empty();
            }
            return Optional.of(IntegerValue.of(asked.equals("window-cols")
                    ? COLUMNS_A_TERMINAL_IS_ASSUMED_TO_HAVE
                    : A_MEASURE_THE_HOST_DOES_NOT_REPORT));
        }

        @Override
        protected Value readFieldInObject(String part) {
            return IntegerValue.of(A_MEASURE_THE_HOST_DOES_NOT_REPORT);
        }

        @Override
        protected Value answerAnUnknownAlone(AnyWordValue asked) {
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
        protected List<String> names() {
            return List.of("name", "size", "type", "date", "modified", "accessed", "created");
        }

        @Override
        protected Optional<Value> findFieldNamed(String asked) {
            return switch (asked) {
                case "name" -> Optional.of(FileValue.of(wholePath));
                case "size" -> Optional.of(about.size().<Value>map(IntegerValue::of).orElseGet(NoneValue::none));
                case "type" -> Optional.of(WordValue.of(about.isDirectory() ? "dir" : "file"));
                case "date", "modified" -> Optional.of(asDateValue(about.modified()));
                case "accessed" -> Optional.of(asDateValue(about.accessed()));
                case "created" -> Optional.of(asDateValue(about.created()));
                default -> Optional.empty();
            };
        }

        @Override
        protected Value answerAnUnknownAlone(AnyWordValue asked) {
            throw Raised.of(EvaluationFailure.INVALID_ARG, asked);
        }

        @Override
        protected Value readFieldInObject(String part) {
            return part.equals("date") ? NoneValue.none() : super.readFieldInObject(part);
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
