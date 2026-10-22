package org.jebol.domain.host;

import org.jebol.domain.value.BlockValue;
import org.jebol.domain.value.EvaluationFailure;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.Raised;
import org.jebol.domain.value.Value;

import java.util.List;
import java.util.Optional;

public final class Place {

    public static final int LONGEST_ADDRESS = 2048;

    private final List<Value> items;
    private final String address;

    private Place(List<Value> items, String address) {
        this.items = items;
        this.address = address;
    }

    public static Place of(Value given) {
        PlaceItems checked = new PlaceItems();
        List<Value> items = given instanceof BlockValue block
                ? checked.copyEach(block.listItems())
                : List.of(checked.copy(given));
        String address = new PlaceAddress().write(items);
        if (address.length() > LONGEST_ADDRESS) {
            throw Raised.of(EvaluationFailure.TOO_LONG, IntegerValue.of(address.length()));
        }
        return new Place(items, address);
    }

    public static Place start() {
        return new Place(List.of(), "");
    }

    public static Optional<Place> readAddress(String address) {
        if (address.length() > LONGEST_ADDRESS) {
            return Optional.empty();
        }
        return new PlaceAddress().read(address).flatMap(read -> {
            try {
                return Optional.of(of(read));
            } catch (Raised notAPlace) {
                return Optional.empty();
            }
        });
    }

    public String address() {
        return address;
    }

    public BlockValue asBlock() {
        return BlockValue.of(new PlaceItems().copyEach(items));
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Place place && place.address.equals(address);
    }

    @Override
    public int hashCode() {
        return address.hashCode();
    }

    @Override
    public String toString() {
        return "#" + address;
    }
}
