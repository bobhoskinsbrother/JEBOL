package org.jebol.application;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.jebol.domain.value.BlockValue;
import org.jebol.domain.value.DecimalValue;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.JavaObjectValue;
import org.jebol.domain.value.LogicValue;
import org.jebol.domain.value.MoneyValue;
import org.jebol.domain.value.Molder;
import org.jebol.domain.value.NoneValue;
import org.jebol.domain.value.StringValue;
import org.jebol.domain.value.UnsetValue;
import org.jebol.domain.value.Value;

/**
 * Converting between REBOL values and host values.
 *
 * <p>The mappings are the obvious ones and nothing more: {@code integer!} to
 * {@code long}, {@code block!} to {@code List}, {@code string!} to
 * {@code String}, {@code logic!} to {@code boolean}. Anything without an
 * obvious counterpart stays what it is, held rather than guessed at, because
 * a wrong conversion is worse than no conversion.
 *
 * <p>A host null and REBOL's {@code none} are kept apart in both directions.
 * {@code none} is a value meaning nothing; a Java null is the host's absence.
 * Conflating them would make a round trip through the host lossy, and a host
 * asking "did the script return nothing, or did it return the value nothing?"
 * would have no way to find out.
 */
public final class HostValues {

    private HostValues() {
    }

    /** A host value as REBOL sees it. */
    public static Value fromHost(Object supplied) {
        return switch (supplied) {
            case null -> JavaObjectValue.hostNull("java.lang.Object");
            case Value alreadyRebol -> alreadyRebol;
            case Boolean flag -> LogicValue.of(flag);
            case Long number -> IntegerValue.of(number);
            case Integer number -> IntegerValue.of(number.longValue());
            case Short number -> IntegerValue.of(number.longValue());
            case Byte number -> IntegerValue.of(number.longValue());
            case Double number -> DecimalValue.of(number);
            case Float number -> DecimalValue.of(number.doubleValue());
            case BigDecimal amount -> MoneyValue.of(amount);
            case String text -> StringValue.of(text);
            case Character character -> StringValue.of(character.toString());
            case List<?> items -> BlockValue.block(
                    items.stream().map(HostValues::fromHost).toList());
            default -> JavaObjectValue.of(supplied);
        };
    }

    /**
     * A REBOL value as the host sees it, where there is an obvious
     * counterpart. Everything else comes back molded, so a host always gets
     * something rather than a surprise.
     */
    public static Object toHost(Value produced) {
        return switch (produced) {
            case IntegerValue integer -> integer.magnitude();
            case DecimalValue decimal -> decimal.quantity();
            case MoneyValue money -> money.amount();
            case LogicValue logic -> logic.truth();
            case StringValue text -> text.text();
            case BlockValue block -> {
                List<Object> items = new ArrayList<>(block.lengthFromHere());
                block.remaining().forEach(item -> items.add(toHost(item)));
                yield List.copyOf(items);
            }
            case JavaObjectValue host -> host.held().orElse(null);
            case NoneValue ignored -> null;
            case UnsetValue ignored -> null;
            default -> Molder.mold(produced);
        };
    }

    /**
     * The same, but absence is empty rather than null.
     *
     * <p>{@code none} and {@code unset!} both mean "nothing came back", and a
     * host asking for a value should be handed an empty optional rather than
     * a null it might dereference.
     */
    public static Optional<Object> toOptionalHost(Value produced) {
        return switch (produced) {
            case NoneValue ignored -> Optional.empty();
            case UnsetValue ignored -> Optional.empty();
            case JavaObjectValue host -> host.held();
            default -> Optional.ofNullable(toHost(produced));
        };
    }
}
