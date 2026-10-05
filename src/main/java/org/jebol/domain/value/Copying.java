package org.jebol.domain.value;

import java.util.EnumSet;
import java.util.Set;

public final class Copying {

    public static final Set<Datatype> WHAT_A_DEEP_COPY_COPIES = whatADeepCopyCopies();

    public static final Set<Datatype> NOTHING_INSIDE = Set.of();

    private Copying() {
    }

    private static Set<Datatype> whatADeepCopyCopies() {
        Set<Datatype> copies = EnumSet.copyOf(Typeset.ANY_BLOCK.members());
        copies.addAll(Typeset.ANY_STRING.members());
        copies.addAll(Set.of(Datatype.BINARY, Datatype.BITSET, Datatype.MAP, Datatype.FUNCTION));
        return Set.copyOf(copies);
    }
}
