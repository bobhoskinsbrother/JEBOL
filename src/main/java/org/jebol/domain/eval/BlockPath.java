package org.jebol.domain.eval;

import org.jebol.domain.value.*;

import java.util.Optional;

final class BlockPath {

    private BlockPath() {
    }

    static Value read(AnyBlockValue block, Value selector) {
        return findThePositionOf(block, selector)
                .map(at -> block.storage().at(at))
                .orElseGet(NoneValue::none);
    }

    static Optional<Integer> findThePositionOf(AnyBlockValue block, Value selector) {
        int at = switch (selector) {
            case IntegerValue position -> countPositionFrom(position.magnitude(), block.index());
            case AnyDecimalValue fraction ->
                    countPositionFrom((long) fraction.quantity(), block.index());
            case AnyWordValue name -> findAfterTheFirstMatch(
                    block, item ->
                    item instanceof AnyWordValue held
                            && held.canonical().equals(name.canonical()));
            default -> findAfterTheFirstMatch(
                    block, item ->
                    Comparison.areLooselyEqual(item, selector));
        };
        return at >= 1 && at <= block.storageLength() ? Optional.of(at) : Optional.empty();
    }

    static boolean isNowhereAtAllSoAWriteQuietlyDoesNothing(Value selector) {
        return switch (selector) {
            case IntegerValue position -> position.magnitude() == 0;
            case AnyDecimalValue fraction -> (long) fraction.quantity() == 0;
            default -> false;
        };
    }

    private static int countPositionFrom(long wanted, int here) {
        if (wanted == 0) {
            return 0;
        }
        long counted = wanted < 0 ? wanted + 1 : wanted;
        return (int) (counted + here - 1);
    }

    private static int findAfterTheFirstMatch(
            AnyBlockValue block, java.util.function.Predicate<Value> matches) {

        for (int at = block.index(); at <= block.storageLength(); at++) {
            if (matches.test(block.storage().at(at))) {
                return at + 1;
            }
        }
        return 0;
    }
}
