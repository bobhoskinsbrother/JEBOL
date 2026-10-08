package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.GrantedServices;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.host.HostService;
import org.jebol.domain.value.*;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class NowNative extends DefaultNative {

    private static final int SECONDS_A_MINUTE = 60;

    private final GrantedServices grantedServices;

    public NowNative(GrantedServices grantedServices) {
        this.grantedServices = grantedServices;
    }

    @Override
    public String nativeName() {
        return "now";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of();
    }

    @Override
    public Set<String> refinementsDeclaredApart() {
        return Set.of("year", "month", "day", "time", "zone", "date",
                "weekday", "yearday", "precise", "utc");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            grantedServices.require(HostService.CLOCK);
            refuseMoreThanOneQuestion(refinements);
            return thePartAskedFor(theClockReadAsAsked(refinements), refinements);
        };
    }

    private void refuseMoreThanOneQuestion(Set<String> refinements) {
        long questions = refinements.size() - (refinements.contains("precise") ? 1 : 0);
        if (questions > 1) {
            throw Raised.of(EvaluationFailure.BAD_REFINES);
        }
    }

    private ZonedDateTime theClockReadAsAsked(Set<String> refinements) {
        ZonedDateTime here = ZonedDateTime.now();
        return refinements.contains("precise") ? here : here.withNano(0);
    }

    private Value thePartAskedFor(ZonedDateTime here, Set<String> refinements) {
        if (refinements.contains("utc")) {
            return dateWithZone(here.withZoneSameInstant(ZoneOffset.UTC), 0);
        }
        int offsetMinutes = here.getOffset().getTotalSeconds() / SECONDS_A_MINUTE;
        if (refinements.contains("date")) {
            return DateValue.of(here.getYear(), here.getMonthValue(), here.getDayOfMonth());
        }
        if (refinements.contains("time")) {
            return TimeValue.ofNanoseconds(here.toLocalTime().toNanoOfDay());
        }
        if (refinements.contains("zone")) {
            return TimeValue.ofNanoseconds(
                    offsetMinutes * SECONDS_A_MINUTE * TimeValue.NANOSECONDS_PER_SECOND);
        }
        if (refinements.contains("weekday")) {
            return IntegerValue.of(here.getDayOfWeek().getValue());
        }
        if (refinements.contains("yearday")) {
            return IntegerValue.of(here.getDayOfYear());
        }
        if (refinements.contains("year")) {
            return IntegerValue.of(here.getYear());
        }
        if (refinements.contains("month")) {
            return IntegerValue.of(here.getMonthValue());
        }
        if (refinements.contains("day")) {
            return IntegerValue.of(here.getDayOfMonth());
        }
        return dateWithZone(here, offsetMinutes);
    }

    private DateValue dateWithZone(ZonedDateTime moment, int offsetMinutes) {
        return new DateValue(moment.getYear(), moment.getMonthValue(), moment.getDayOfMonth(),
                Optional.of(TimeValue.ofNanoseconds(moment.toLocalTime().toNanoOfDay())),
                Optional.of(offsetMinutes));
    }
}
