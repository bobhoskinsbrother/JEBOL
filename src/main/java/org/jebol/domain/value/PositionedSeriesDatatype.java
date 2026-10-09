package org.jebol.domain.value;

import java.util.List;

abstract class PositionedSeriesDatatype extends SeriesDatatype {

    PositionedSeriesDatatype(String spelling) {
        super(spelling);
    }

    protected abstract Value withRoomFor(int asked);

    @Override
    public Value madeFrom(Value spec, Maker maker) {
        refuseToBuildSomethingOutOfNothing(spec);
        return switch (spec) {
            case IntegerValue(long magnitude) -> withRoomForAsMuchAs(spec, magnitude);
            case DecimalValue number -> withRoomForAsMuchAs(spec, number.quantity());
            default -> built(Conversion.MAKE, spec, maker);
        };
    }

    private Value withRoomForAsMuchAs(Value spec, double asked) {
        if (asked < 0) {
            throw Raised.of(EvaluationFailure.OUT_OF_RANGE, spec.toString());
        }
        refuseMoreRoomThanFits(asked, bytesAnItemTakes());
        int room = (int) Math.min(Integer.MAX_VALUE, (long) asked);
        return whatTheHostHadRoomFor(() -> withRoomFor(room));
    }

    protected int bytesAnItemTakes() {
        return 1;
    }

    @Override
    public Value constructedFrom(List<Value> contents, Construction construction) {
        if (contents.size() == 1) {
            return constructedFromOne(contents.getFirst(), construction);
        }
        if (contents.size() == 2 && contents.get(1) instanceof IntegerValue) {
            return standingWhereItWasTold(
                    constructedFromOne(contents.getFirst(), construction), contents.get(1));
        }
        return constructedFromMore(contents, construction);
    }

    protected Value constructedFromOne(Value only, Construction construction) {
        return construction.madeOf(this, only);
    }

    protected Value constructedFromMore(List<Value> contents, Construction construction) {
        throw refusingConstruction(contents);
    }

    protected Value standingWhereItWasTold(Value whole, Value position) {
        if (!(whole instanceof RebolSeries series)) {
            throw refusingConstruction(List.of(whole, position));
        }
        if (!(position instanceof IntegerValue(long magnitude))) {
            return series;
        }
        long tail = series.storageLength() + 1L;
        long counted = magnitude - 1;
        return series.atIndex((int) (counted < 0 || counted > tail - 1 ? tail : counted + 1));
    }
}
