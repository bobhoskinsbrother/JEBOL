package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.GrantedServices;
import org.jebol.domain.host.WindowPort;
import org.jebol.domain.host.ServiceRefusal;
import org.jebol.domain.value.ErrorCategory;
import org.jebol.domain.value.ErrorValue;
import org.jebol.domain.value.Raised;
import org.jebol.domain.value.AnyStringValue;
import org.jebol.domain.value.StringValue;
import org.jebol.domain.value.Value;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

public abstract class WindowNative extends HostNative {

    protected WindowNative(GrantedServices granted) {
        super(granted);
    }

    protected Value throughTheWindows(Supplier<Value> operation) {
        try {
            return operation.get();
        } catch (WindowPort.Denied denied) {
            throw refusedByTheHost(denied.errorId(), denied.getMessage());
        }
    }

    protected Raised refusedByTheHost(String errorId, String because) {
        String reason = because + ", which is "
                + ServiceRefusal.NOT_PRESENT.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return new Raised(ErrorValue.about(ErrorCategory.ACCESS, errorId, reason, StringValue.of(reason)));
    }

    protected Optional<String> textGivenFor(String refinement, List<Value> arguments, Set<String> refinements) {
        return argumentOf(refinement, 0, arguments, refinements)
                .filter(AnyStringValue.class::isInstance)
                .map(given -> ((AnyStringValue) given).text());
    }
}
