package org.jebol.application;

import org.jebol.domain.value.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class HostValues {

    private HostValues() {
    }

    public static Value fromHost(Object supplied) {
        return switch (supplied) {
            case null -> JavaObjectValue.holdHostNull("java.lang.Object");
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
            case List<?> items -> BlockValue.of(
                    items.stream().map(HostValues::fromHost).toList());
            default -> JavaObjectValue.of(supplied);
        };
    }

    public static Object toHost(Value produced) {
        return switch (produced) {
            case IntegerValue integer -> integer.magnitude();
            case AnyDecimalValue decimal -> decimal.quantity();
            case MoneyValue money -> money.amount();
            case LogicValue logic -> logic.truth();
            case AnyStringValue text -> text.text();
            case AnyBlockValue block -> {
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

    public static Optional<Object> toOptionalHost(Value produced) {
        return switch (produced) {
            case NoneValue ignored -> Optional.empty();
            case UnsetValue ignored -> Optional.empty();
            case JavaObjectValue host -> host.held();
            default -> Optional.ofNullable(toHost(produced));
        };
    }
}
