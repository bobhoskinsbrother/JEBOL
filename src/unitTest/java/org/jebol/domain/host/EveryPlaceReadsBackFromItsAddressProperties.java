package org.jebol.domain.host;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.StringLength;
import org.jebol.domain.value.BlockValue;
import org.jebol.domain.value.DecimalValue;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.LogicValue;
import org.jebol.domain.value.StringValue;
import org.jebol.domain.value.Value;
import org.jebol.domain.value.WordValue;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EveryPlaceReadsBackFromItsAddressProperties {

    @Property
    void aPlaceReadsBackFromItsAddress(@ForAll("places") Value given) {
        Place place = Place.of(given);

        assertThat(Place.readAddress(place.address())).contains(place);
    }

    @Property
    void readingAnyTextNeverThrows(@ForAll @StringLength(max = 60) String address) {
        assertThat(Place.readAddress(address)).isNotNull();
    }

    @Provide
    Arbitrary<Value> places() {
        return Arbitraries.recursive(this::item, this::wrappedInABlock, 0, 2);
    }

    private Arbitrary<Value> item() {
        return Arbitraries.oneOf(
                Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(8).map(WordValue::of),
                Arbitraries.strings().ofMaxLength(5).map(StringValue::of),
                Arbitraries.longs().map(IntegerValue::of),
                Arbitraries.doubles().between(-1e9, 1e9).ofScale(3).map(DecimalValue::of),
                Arbitraries.of(true, false).map(LogicValue::of));
    }

    private Arbitrary<Value> wrappedInABlock(Arbitrary<Value> inner) {
        return inner.list().ofMaxSize(3)
                .map(List::copyOf)
                .map(BlockValue::of);
    }
}
