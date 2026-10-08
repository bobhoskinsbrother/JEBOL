package org.jebol.domain.read;

import org.jebol.domain.value.AnyBlockValue;
import org.jebol.domain.value.Value;
import org.jebol.domain.value.WordValue;

import java.util.List;

public record LibraryFile(LibraryFileHeader header, AnyBlockValue body) {

    private static final String REBOL = "rebol";

    private static final int WHERE_THE_BODY_STARTS_BELOW_A_HEADER = 3;

    public static LibraryFile readFrom(AnyBlockValue transcoded) {
        return startsWithARebolHeader(transcoded)
                ? new LibraryFile(
                        LibraryFileHeader.readFrom(transcoded.remaining().get(1)),
                        transcoded.atIndex(WHERE_THE_BODY_STARTS_BELOW_A_HEADER))
                : new LibraryFile(LibraryFileHeader.none(), transcoded);
    }

    private static boolean startsWithARebolHeader(AnyBlockValue transcoded) {
        List<Value> items = transcoded.remaining();
        return items.size() >= 2
                && items.get(0) instanceof WordValue opening
                && REBOL.equals(opening.canonical())
                && items.get(1) instanceof AnyBlockValue;
    }
}
