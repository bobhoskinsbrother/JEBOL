package org.jebol.domain.value;

import java.lang.ref.WeakReference;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Optional;

public final class LineMarks {

    private static final int FIRST_CAPACITY = 1 << 10;

    private static final ThreadLocal<Remembered> ON_THIS_THREAD = ThreadLocal.withInitial(Remembered::new);

    private LineMarks() {
    }

    public static boolean isMarked(Value value) {
        if (value instanceof CopiesItsCell copying) {
            return copying.isLineMarked();
        }
        return value instanceof Record && !(value instanceof UtypeValue) && ON_THIS_THREAD.get().holds(value);
    }

    public static Value marked(Value value) {
        if (value instanceof CopiesItsCell copying) {
            return copying.isLineMarked() ? value : copying.copyCell(true);
        }
        if (!(value instanceof Record) || value instanceof UtypeValue) {
            return value;
        }
        Remembered remembered = ON_THIS_THREAD.get();
        if (remembered.holds(value)) {
            return value;
        }
        Value copy = copyRecord(value);
        if (copy != value) {
            remembered.add(copy);
        }
        return copy;
    }

    public static Value unmarked(Value value) {
        if (value instanceof CopiesItsCell copying) {
            return copying.isLineMarked() ? copying.copyCell(false) : value;
        }
        return isMarked(value) ? copyRecord(value) : value;
    }

    public static Value withMark(Value value, boolean mark) {
        return mark ? marked(value) : unmarked(value);
    }

    private static final class Remembered {

        private WeakReference<?>[] table = new WeakReference<?>[FIRST_CAPACITY];
        private int occupied;

        private boolean holds(Value value) {
            WeakReference<?>[] searched = table;
            int mask = searched.length - 1;
            for (int at = System.identityHashCode(value) & mask; ; at = (at + 1) & mask) {
                WeakReference<?> entry = searched[at];
                if (entry == null) {
                    return false;
                }
                if (entry.get() == value) {
                    return true;
                }
            }
        }

        private void add(Value marked) {
            if ((occupied + 1) * 2 > table.length) {
                rehash();
            }
            place(new WeakReference<>(marked), marked);
            occupied++;
        }

        private void place(WeakReference<?> entry, Object referent) {
            int mask = table.length - 1;
            int at = System.identityHashCode(referent) & mask;
            while (table[at] != null) {
                at = (at + 1) & mask;
            }
            table[at] = entry;
        }

        private void rehash() {
            WeakReference<?>[] alive = Arrays.stream(table)
                    .filter(entry -> entry != null && entry.get() != null)
                    .toArray(WeakReference<?>[]::new);
            int capacity = FIRST_CAPACITY;
            while (capacity < alive.length * 4) {
                capacity <<= 1;
            }
            table = new WeakReference<?>[capacity];
            occupied = 0;
            for (WeakReference<?> entry : alive) {
                Object referent = entry.get();
                if (referent != null) {
                    place(entry, referent);
                    occupied++;
                }
            }
        }
    }

    private static Value copyRecord(Value value) {
        return switch (value) {
            case BinaryValue(BinaryStorage storage, int index) -> new BinaryValue(storage, index);
            case CharacterValue(int codepoint) -> new CharacterValue(codepoint);
            case DateValue(int year, int month, int day, Optional<TimeValue> time, Optional<Integer> zone) ->
                    new DateValue(year, month, day, time, zone);
            case GobValue(GobStorage storage, int index) -> new GobValue(storage, index);
            case ImageValue(ImageStorage storage, int index) -> new ImageValue(storage, index);
            case IntegerValue(long magnitude) -> new IntegerValue(magnitude);
            case LogicValue(boolean truth) -> new LogicValue(truth);
            case MoneyValue(BigDecimal amount, Optional<String> currency, boolean negative) ->
                    new MoneyValue(amount, currency, negative);
            case NoneValue() -> new NoneValue();
            case ObjectValue(Context context) -> new ObjectValue(context);
            case PairValue(double x, double y) -> new PairValue(x, y);
            case PortValue(Context context) -> new PortValue(context);
            case TimeValue(long nanoseconds) -> new TimeValue(nanoseconds);
            case TupleValue(int[] segments) -> new TupleValue(segments);
            case UnsetValue() -> new UnsetValue();
            case VectorValue(VectorStorage storage, int index) -> new VectorValue(storage, index);
            default -> value;
        };
    }
}
