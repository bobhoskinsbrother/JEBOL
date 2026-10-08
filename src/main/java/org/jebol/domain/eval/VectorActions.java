package org.jebol.domain.eval;

import org.jebol.domain.value.*;

import java.util.ArrayList;
import java.util.List;

public final class VectorActions extends SeriesActions {

    private final VectorValue vector;

    public VectorActions(VectorValue vector) {
        this.vector = vector;
    }

    @Override
    VectorValue held() {
        return vector;
    }

    @Override
    public Value poked(Value position, Value written) {
        VectorPath.write(vector, position, written);
        return written;
    }

    @Override
    void takeOneOutAt(int oneBasedIndex) {
        vector.storage().removeAt(oneBasedIndex);
    }

    @Override
    Value ofTheSameKindHolding(List<Value> items) {
        VectorStorage made = new VectorStorage(vector.kind(), 0);
        items.forEach(number ->
                made.append(VectorPath.storedFormOf(vector.kind(), number)));
        return new VectorValue(made, 1);
    }

    @Override
    public Value cleared() {
        vector.storage().clearFrom(vector.index());
        return vector;
    }

    @Override
    public Value append(Asked asked) {
        for (Value number : numbersAddedBy(asked)) {
            vector.storage().append(VectorPath.storedFormOf(vector.kind(), number));
        }
        return vector.head();
    }

    @Override
    public Value insert(Asked asked) {
        VectorValue held = (VectorValue) vector.clampedToTail();
        List<Value> numbers = numbersAddedBy(asked);
        for (int at = numbers.size(); at > 0; at--) {
            held.storage().insertAt(held.index(),
                    VectorPath.storedFormOf(held.kind(), numbers.get(at - 1)));
        }
        return held.atIndex(held.index() + numbers.size());
    }

    private List<Value> numbersAddedBy(Asked asked) {
        List<Value> once = asked.refinementsAsked().contains("part")
                ? numbersOfferedTo(vector.kind(), asked.given(),
                        asked.howManyOctetsWanted())
                : numbersContributedTo(vector.kind(), asked.given());
        List<Value> added = new ArrayList<>();
        for (long round = 0; round < asked.howManyTimes(); round++) {
            added.addAll(once);
        }
        return added;
    }

    private List<Value> numbersContributedTo(VectorKind kind, Value value) {
        if (value instanceof VectorValue source) {
            return source.remaining();
        }
        if (value instanceof BlockValue block) {
            return block.remaining();
        }
        if (value instanceof BinaryValue bytes) {
            return numbersSpeltByWithTheOddBytesDropped(kind, bytes, bytes.lengthFromHere());
        }
        return List.of(value);
    }

    private List<Value> numbersSpeltByWithTheOddBytesDropped(VectorKind kind, BinaryValue bytes, int taking) {

        int wholeNumbers = Math.max(0, taking) / kind.bytes();
        if (wholeNumbers == 0) {
            throw Raised.of(EvaluationFailure.INVALID_DATA, bytes);
        }
        byte[] octets = bytes.octetsFromHere();
        List<Value> numbers = new ArrayList<>();
        for (int number = 0; number < wholeNumbers; number++) {
            numbers.add(kind.read(kind.fromOctets(octets, number * kind.bytes())));
        }
        return numbers;
    }


    private List<Value> numbersOfferedTo(VectorKind kind, Value value, int limit) {
        if (!(value instanceof RebolSeries source)) {
            return numbersContributedTo(kind, value);
        }
        RebolSeries run = source.reachingBackIfNegative(limit);
        long wanted = limit >= 0 ? limit : source.index() - run.index();
        if (run instanceof BinaryValue bytes) {
            return numbersSpeltByWithTheOddBytesDropped(kind, bytes, (int) Math.min(wanted, bytes.lengthFromHere()));
        }
        List<Value> offered = numbersContributedTo(kind, run);
        return offered.subList(0, (int) Math.min(wanted, offered.size()));
    }


}
