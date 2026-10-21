package org.jebol.domain.value;

import java.util.List;

abstract class PositionedSeriesDatatype extends SeriesDatatype {

    PositionedSeriesDatatype(String spelling) {
        super(spelling);
    }

    protected abstract Value withRoomFor(int asked);

    @Override
    public Value makeFrom(Value spec, Maker maker) {
        refuseToBuildSomethingOutOfNothing(spec);
        return switch (spec) {
            case IntegerValue(long magnitude) -> withRoomForAsMuchAs(spec, magnitude);
            case DecimalValue number -> withRoomForAsMuchAs(spec, number.quantity());
            default -> build(Conversion.MAKE, spec, maker);
        };
    }

    private Value withRoomForAsMuchAs(Value spec, double asked) {
        if (asked < 0) {
            throw Raised.of(EvaluationFailure.OUT_OF_RANGE, spec.toString());
        }
        refuseMoreRoomThanFits(asked, bytesItemTakes());
        int room = (int) Math.min(Integer.MAX_VALUE, (long) asked);
        return allocateWithinHost(() -> withRoomFor(room));
    }

    protected int bytesItemTakes() {
        return 1;
    }

    @Override
    public Value construct(List<Value> contents, Construction construction) {
        if (contents.size() == 1) {
            return constructFromOne(contents.getFirst(), construction);
        }
        if (contents.size() == 2 && contents.get(1) instanceof IntegerValue) {
            return standWhereItWasTold(
                    constructFromOne(contents.getFirst(), construction), contents.get(1));
        }
        return constructFromMore(contents, construction);
    }

    protected Value constructFromOne(Value only, Construction construction) {
        return construction.make(this, only);
    }

    protected Value constructFromMore(List<Value> contents, Construction construction) {
        throw refuseConstruction(contents);
    }

    protected Value standWhereItWasTold(Value whole, Value position) {
        if (!(whole instanceof RebolSeries series)) {
            throw refuseConstruction(List.of(whole, position));
        }
        if (!(position instanceof IntegerValue(long magnitude))) {
            return series;
        }
        long tail = series.storageLength() + 1L;
        long counted = magnitude - 1;
        return series.atIndex((int) (counted < 0 || counted > tail - 1 ? tail : counted + 1));
    }
}
