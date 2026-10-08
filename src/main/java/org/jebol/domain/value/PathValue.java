package org.jebol.domain.value;

import java.util.List;

public final class PathValue extends AnyPathValue {

    PathValue(BlockStorage storage, int index) {
        super(storage, index);
    }

    public static PathValue of(List<Value> segments) {
        return new PathValue(new BlockStorage(segments), 1);
    }

    @Override
    public Datatype datatype() {
        return Datatype.PATH;
    }

    @Override
    PathValue sameKindOver(BlockStorage storage, int index) {
        return new PathValue(storage, index);
    }

    @Override
    public boolean looksUpItsDeclaration() {
        return true;
    }
}
