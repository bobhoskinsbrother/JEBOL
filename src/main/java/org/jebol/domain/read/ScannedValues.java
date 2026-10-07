package org.jebol.domain.read;

import org.jebol.domain.value.DateValue;
import org.jebol.domain.value.Deci;
import org.jebol.domain.value.DeciReading;
import org.jebol.domain.value.DecimalValue;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.MoneyValue;
import org.jebol.domain.value.PairValue;
import org.jebol.domain.value.TimeValue;
import org.jebol.domain.value.TupleValue;
import org.jebol.domain.value.Value;

import java.math.BigInteger;
import java.time.Year;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ScannedValues {

    private static final int MAX_NUM_LEN = 64;

    private static final int MOST_INTEGER_DIGITS = 19;

    private static final int MAX_TUPLE = 12;

    private static final int MAX_YEAR = 0x3FFF;

    private static final long MAX_HOUR = 9223372036L / 3600;

    private static final long NANOSECONDS_IN_A_SECOND = 1_000_000_000L;

    private static final long NANOSECONDS_IN_A_DAY = 24L * 60 * 60 * NANOSECONDS_IN_A_SECOND;

    private static final int ZONE_MINUTES = 15;

    private static final int FRACTION_DIGITS = 9;

    private static final int[] MONTH_LENGTHS = {31, 29, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31};

    private static final String[] MONTH_NAMES = {
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December"
    };

    private static final Pattern WHAT_STRTOD_READS =
            Pattern.compile("[+-]?(?:\\d+\\.?\\d*|\\.\\d+)(?:[eE][+-]?\\d+)?");

    private final byte[] in;

    private final LexicalClasses lex;

    private int scannedTo;

    ScannedValues(byte[] in, LexicalClasses lex) {
        this.in = in;
        this.lex = lex;
    }

    int scannedTo() {
        return scannedTo;
    }

    private int at(int index) {
        return in[index] & 0xFF;
    }

    private boolean isDigit(int octet) {
        return octet >= '0' && octet <= '9';
    }

    int grabbedIntegerEnd(int from) {
        int cp = from;
        if (at(cp) == '-' || at(cp) == '+') {
            cp++;
        }
        while (isDigit(at(cp))) {
            cp++;
        }
        return cp;
    }

    int grabbedInteger(int from) {
        int cp = from;
        boolean negative = false;
        if (at(cp) == '-') {
            cp++;
            negative = true;
        } else if (at(cp) == '+') {
            cp++;
        }
        int value = 0;
        while (isDigit(at(cp))) {
            value = value * 10 + (at(cp) - '0');
            cp++;
        }
        return negative ? -value : value;
    }

    Optional<Value> integer(int from, int length) {
        int cp = from;
        int num = length;
        if (num == 1) {
            if (at(cp) == '0') {
                return Optional.of(IntegerValue.of(0));
            }
            if (at(cp) == '1') {
                return Optional.of(IntegerValue.of(1));
            }
        }
        if (length > MAX_NUM_LEN) {
            return Optional.empty();
        }
        StringBuilder digits = new StringBuilder();
        boolean negative = false;
        if (at(cp) == '-') {
            digits.append('-');
            cp++;
            num--;
            negative = true;
        } else if (at(cp) == '+') {
            cp++;
            num--;
        }
        for (; num > 0; num--) {
            if (at(cp) == '0' || at(cp) == '\'') {
                cp++;
            } else {
                break;
            }
        }
        for (; num > 0; num--) {
            if (isDigit(at(cp))) {
                digits.append((char) at(cp));
                cp++;
            } else if (at(cp) == '\'') {
                cp++;
            } else {
                return Optional.empty();
            }
        }
        int counted = digits.length() - (negative ? 1 : 0);
        if (counted == 0) {
            return Optional.of(IntegerValue.of(0));
        }
        if (counted > MOST_INTEGER_DIGITS) {
            return Optional.empty();
        }
        BigInteger read = new BigInteger(digits.toString());
        if (read.bitLength() >= Long.SIZE) {
            return Optional.empty();
        }
        return Optional.of(IntegerValue.of(read.longValue()));
    }

    Optional<Double> decimalBuffer(int from, StringBuilder written) {
        int cp = from;
        boolean digit = false;
        if (at(cp) == '+' || at(cp) == '-') {
            written.append((char) at(cp++));
        }
        while (lex.isNumber(at(cp)) || at(cp) == '\'') {
            if (at(cp) != '\'') {
                written.append((char) at(cp++));
                if (written.length() >= MAX_NUM_LEN - 1) {
                    return Optional.empty();
                }
                digit = true;
            } else {
                cp++;
            }
        }
        if (at(cp) == ',' || at(cp) == '.') {
            cp++;
        }
        written.append('.');
        while (lex.isNumber(at(cp)) || at(cp) == '\'') {
            if (at(cp) != '\'') {
                written.append((char) at(cp++));
                if (written.length() >= MAX_NUM_LEN - 1) {
                    return Optional.empty();
                }
                digit = true;
            } else {
                cp++;
            }
        }
        if (!digit) {
            return Optional.empty();
        }
        if (at(cp) == 'E' || at(cp) == 'e') {
            written.append((char) at(cp++));
            digit = false;
            if (at(cp) == '-' || at(cp) == '+') {
                written.append((char) at(cp++));
            }
            while (lex.isNumber(at(cp))) {
                written.append((char) at(cp++));
                digit = true;
            }
            if (!digit) {
                return Optional.empty();
            }
        }
        scannedTo = cp;
        return Optional.of(whatStrtodReads(written.toString()));
    }

    double whatStrtodReads(String written) {
        Matcher read = WHAT_STRTOD_READS.matcher(written);
        return read.lookingAt() ? Double.parseDouble(read.group()) : 0.0;
    }

    Optional<Value> decimal(int from, int length) {
        int cp = from;
        if (length > MAX_NUM_LEN) {
            return Optional.empty();
        }
        StringBuilder written = new StringBuilder();
        boolean digit = false;
        if (at(cp) == '+' || at(cp) == '-') {
            written.append((char) at(cp++));
        }
        while (lex.isNumber(at(cp)) || at(cp) == '\'') {
            if (at(cp) != '\'') {
                written.append((char) at(cp++));
                digit = true;
            } else {
                cp++;
            }
        }
        if (at(cp) == ',' || at(cp) == '.') {
            cp++;
        }
        written.append('.');
        if (at(cp) == '#') {
            return infinityOrNaN(from, cp, length);
        }
        while (lex.isNumber(at(cp)) || at(cp) == '\'') {
            if (at(cp) != '\'') {
                written.append((char) at(cp++));
                digit = true;
            } else {
                cp++;
            }
        }
        if (!digit) {
            return Optional.empty();
        }
        if (at(cp) == 'E' || at(cp) == 'e') {
            written.append((char) at(cp++));
            if (at(cp) == '-' || at(cp) == '+') {
                written.append((char) at(cp++));
            }
            while (lex.isNumber(at(cp))) {
                written.append((char) at(cp++));
            }
        }
        if (at(cp) == '%') {
            cp++;
        }
        if (cp - from != length) {
            return Optional.empty();
        }
        double read = whatStrtodReads(written.toString());
        if (Double.isInfinite(read)) {
            throw new ScanFailure(SyntaxFailure.OVERFLOW);
        }
        scannedTo = cp;
        return Optional.of(DecimalValue.of(read));
    }

    private Optional<Value> infinityOrNaN(int from, int hash, int length) {
        int cp = hash;
        if (spells(cp + 1, "INF")) {
            cp += 4;
            if (cp - from != length) {
                return Optional.empty();
            }
            return Optional.of(DecimalValue.of(at(from) == '-' ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY));
        }
        if (spells(cp + 1, "NAN")) {
            cp += 4;
            if (cp - from != length) {
                return Optional.empty();
            }
            return Optional.of(DecimalValue.of(Double.NaN));
        }
        return Optional.empty();
    }

    boolean spells(int from, String upperCase) {
        for (int offset = 0; offset < upperCase.length(); offset++) {
            if (Character.toUpperCase(at(from + offset)) != upperCase.charAt(offset)) {
                return false;
            }
        }
        return true;
    }

    Optional<Value> money(int from, int length) {
        int cp = from;
        int remaining = length;
        if (at(cp) == '$') {
            cp++;
            remaining--;
        }
        if (remaining == 0) {
            return Optional.empty();
        }
        StringBuilder token = new StringBuilder();
        for (int offset = 0; offset < remaining; offset++) {
            token.append((char) at(cp + offset));
        }
        DeciReading reading = new DeciReading(token.toString());
        Deci read = reading.readFrom(0);
        if (reading.readTo() != remaining) {
            return Optional.empty();
        }
        scannedTo = cp + remaining;
        return Optional.of(new MoneyValue(read));
    }

    Optional<TimeValue> scannedTimeOrNot(int from) {
        int cp = from;
        boolean negative = false;
        if (at(cp) == '-') {
            cp++;
            negative = true;
        } else if (at(cp) == '+') {
            cp++;
        }
        if (at(cp) == '-' || at(cp) == '+') {
            return Optional.empty();
        }
        long part1 = grabbedInteger(cp);
        cp = grabbedIntegerEnd(cp);
        if (part1 > MAX_HOUR) {
            return Optional.empty();
        }
        if (at(cp++) != ':') {
            return Optional.empty();
        }
        int sp = grabbedIntegerEnd(cp);
        long part2 = grabbedInteger(cp);
        if (part2 < 0 || sp == cp) {
            return Optional.empty();
        }
        cp = sp;
        long part3 = -1;
        long part4 = -1;
        if (at(cp) == ':') {
            sp = cp + 1;
            cp = grabbedIntegerEnd(sp);
            part3 = grabbedInteger(sp);
            if (part3 < 0 || cp == sp) {
                return Optional.empty();
            }
        }
        if (at(cp) == '.' || at(cp) == ',') {
            ++cp;
            long scaled = 0;
            int scale = FRACTION_DIGITS;
            for (; scale > 0 && isDigit(at(cp)); scale--) {
                scaled = scaled * 10 + (at(cp) - '0');
                cp++;
            }
            if (at(cp) >= '5' && at(cp) <= '9') {
                scaled++;
            }
            while (isDigit(at(cp))) {
                cp++;
            }
            for (; scale > 0; scale--) {
                scaled *= 10;
            }
            part4 = (int) scaled == 0 ? -1 : (int) scaled;
        }
        int meridian = 0;
        if ((Character.toUpperCase(at(cp)) == 'A' || Character.toUpperCase(at(cp)) == 'P')
                && Character.toUpperCase(at(cp + 1)) == 'M') {
            meridian = Character.toUpperCase(at(cp));
            cp += 2;
        }
        long nanoseconds;
        if (part3 >= 0 || part4 < 0) {
            if (meridian != 0) {
                if (part1 > 12) {
                    return Optional.empty();
                }
                if (part1 == 12) {
                    part1 = 0;
                }
                if (meridian == 'P') {
                    part1 += 12;
                }
            }
            if (part3 < 0) {
                part3 = 0;
            }
            nanoseconds = ((part1 * 60 + part2) * 60 + part3) * NANOSECONDS_IN_A_SECOND;
        } else {
            if (meridian != 0) {
                return Optional.empty();
            }
            nanoseconds = (part1 * 60 + part2) * NANOSECONDS_IN_A_SECOND;
        }
        if (part4 > 0) {
            nanoseconds += part4;
        }
        scannedTo = cp;
        return Optional.of(TimeValue.ofNanoseconds(negative ? -nanoseconds : nanoseconds));
    }

    Optional<Value> date(int from, int length) {
        int cp = from;
        int end = from + length;
        int day = 0;
        int year = 0;
        int month;
        int zone = 0;
        while (at(cp) == ' ' && cp != end) {
            cp++;
        }
        int ep = cp;
        while (at(ep) != ',' && ep != end) {
            ep++;
        }
        if (ep != end) {
            cp = ep + 1;
            while (at(cp) == ' ' && cp != end) {
                cp++;
            }
        }
        if (cp == end) {
            return Optional.empty();
        }
        ep = grabbedIntegerEnd(cp);
        int num = grabbedInteger(cp);
        if (num < 0) {
            return Optional.empty();
        }
        int size = ep - cp;
        if (size >= 4) {
            year = num;
        } else if (size > 0) {
            day = num;
        } else {
            return Optional.empty();
        }
        cp = ep;
        if (at(cp) != '/' && at(cp) != '-' && at(cp) != '.' && at(cp) != ' ') {
            return Optional.empty();
        }
        int separator = at(cp++);
        ep = grabbedIntegerEnd(cp);
        num = grabbedInteger(cp);
        if (num < 0) {
            return Optional.empty();
        }
        size = ep - cp;
        if (size > 0) {
            month = num;
        } else {
            ep = cp;
            while (lex.isWord(at(ep))) {
                ep++;
            }
            size = ep - cp;
            if (size < 3) {
                return Optional.empty();
            }
            int named = 0;
            while (named < 12 && !namesTheMonth(named, cp, size)) {
                named++;
            }
            month = named + 1;
        }
        if (month < 1 || month > 12) {
            return Optional.empty();
        }
        cp = ep;
        if (at(cp++) != separator) {
            return Optional.empty();
        }
        ep = grabbedIntegerEnd(cp);
        num = grabbedInteger(cp);
        if (at(cp) == '-' || num < 0) {
            return Optional.empty();
        }
        size = ep - cp;
        if (size == 0) {
            return Optional.empty();
        }
        if (day == 0) {
            day = num;
        } else if (size >= 3) {
            year = num;
        } else {
            int current = Year.now().getValue();
            year = (current / 100) * 100 + num;
            if (year - current > 50) {
                year -= 100;
            } else if (year - current < -50) {
                year += 100;
            }
        }
        if (year > MAX_YEAR || day < 1 || day > MONTH_LENGTHS[month - 1]) {
            return Optional.empty();
        }
        if (month == 2 && day == 29 && (year % 4 != 0 || (year % 100 == 0 && year % 400 != 0))) {
            return Optional.empty();
        }
        cp = ep;
        Optional<TimeValue> time = Optional.empty();
        if (cp < end) {
            if (at(cp) == '/' || at(cp) == 'T' || at(cp) == ' ') {
                separator = at(cp++);
                if (cp >= end) {
                    scannedTo = cp;
                    return Optional.of(dated(year, month, day, time, zone));
                }
                time = scannedTimeOrNot(cp);
                if (time.isEmpty() || time.get().nanoseconds() < 0
                        || time.get().nanoseconds() >= NANOSECONDS_IN_A_DAY) {
                    return Optional.empty();
                }
                cp = scannedTo;
            }
            if (at(cp) == separator) {
                cp++;
            }
            if (at(cp) == 'Z') {
                if (++cp != end) {
                    return Optional.empty();
                }
                scannedTo = cp;
                return Optional.of(dated(year, month, day, time, zone));
            }
            if (at(cp) == '-' || at(cp) == '+') {
                ep = grabbedIntegerEnd(cp + 1);
                num = grabbedInteger(cp + 1);
                if (ep - cp == 0) {
                    return Optional.empty();
                }
                if (at(ep) != ':') {
                    if (num < -1500 || num > 1500) {
                        return Optional.empty();
                    }
                    int hours = num / 100;
                    int minutes = num - hours * 100;
                    zone = (hours * 60 + minutes) / ZONE_MINUTES;
                } else {
                    if (num < -15 || num > 15) {
                        return Optional.empty();
                    }
                    zone = num * (60 / ZONE_MINUTES);
                    int minutesFrom = ep + 1;
                    ep = grabbedIntegerEnd(minutesFrom);
                    num = grabbedInteger(minutesFrom);
                    if (num % ZONE_MINUTES != 0) {
                        return Optional.empty();
                    }
                    zone += num / ZONE_MINUTES;
                }
                if (ep != end) {
                    return Optional.empty();
                }
                if (at(cp) == '-') {
                    zone = -zone;
                }
                cp = ep;
            }
        }
        scannedTo = cp;
        return Optional.of(dated(year, month, day, time, zone));
    }

    private boolean namesTheMonth(int month, int from, int size) {
        String name = MONTH_NAMES[month];
        for (int offset = 0; offset < size; offset++) {
            int wanted = offset < name.length() ? Character.toLowerCase(name.charAt(offset)) : 0;
            if (wanted != Character.toLowerCase(at(from + offset))) {
                return false;
            }
        }
        return true;
    }

    private Value dated(int year, int month, int day, Optional<TimeValue> time, int zone) {
        if (time.isEmpty() && zone == 0) {
            return DateValue.of(year, month, day);
        }
        if (time.isEmpty()) {
            return new DateValue(year, month, day, Optional.of(TimeValue.ofNanoseconds(0)), Optional.of(0));
        }
        return new DateValue(year, month, day, time, Optional.of(zone * ZONE_MINUTES));
    }

    Optional<Value> pair(int from, int length) {
        StringBuilder written = new StringBuilder();
        Optional<Double> x = decimalBuffer(from, written);
        if (x.isEmpty()) {
            return Optional.empty();
        }
        int ep = scannedTo;
        Optional<Double> readX = pairPart(written, ep, x.get());
        if (readX.isEmpty()) {
            return Optional.empty();
        }
        ep = scannedTo;
        if (at(ep) != 'x' && at(ep) != 'X') {
            return Optional.empty();
        }
        ep++;
        written.setLength(0);
        Optional<Double> y = decimalBuffer(ep, written);
        if (y.isEmpty()) {
            return Optional.empty();
        }
        int xp = scannedTo;
        Optional<Double> readY = pairPart(written, xp, y.get());
        if (readY.isEmpty()) {
            return Optional.empty();
        }
        xp = scannedTo;
        if (length > xp - from) {
            return Optional.empty();
        }
        return Optional.of(PairValue.of((float) readX.get().doubleValue(), (float) readY.get().doubleValue()));
    }

    private Optional<Double> pairPart(StringBuilder written, int ep, double read) {
        scannedTo = ep;
        if (at(ep) != '#') {
            return Optional.of(read);
        }
        String buffer = written.toString();
        if (!(buffer.startsWith("-1.") || buffer.startsWith("1."))) {
            return Optional.empty();
        }
        if (spells(ep + 1, "INF")) {
            scannedTo = ep + 4;
            return Optional.of(buffer.charAt(0) == '-' ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY);
        }
        if (spells(ep + 1, "NAN")) {
            scannedTo = ep + 4;
            return Optional.of(Double.NaN);
        }
        return Optional.empty();
    }

    Optional<Value> tuple(int from, int length) {
        if (length == 0) {
            return Optional.empty();
        }
        int size = 1;
        for (int ep = from; ep < from + length; ep++) {
            if (at(ep) == '.') {
                size++;
            }
        }
        if (size > MAX_TUPLE) {
            return Optional.empty();
        }
        int[] segments = new int[MAX_TUPLE];
        int written = 0;
        int ep = from;
        for (; length > ep - from; ep++) {
            int n = grabbedInteger(ep);
            ep = grabbedIntegerEnd(ep);
            if (n < 0 || n > 255) {
                return Optional.empty();
            }
            segments[written++] = n;
            if (at(ep) != '.') {
                break;
            }
        }
        if (length > ep - from) {
            return Optional.empty();
        }
        int[] kept = new int[Math.max(size, TupleValue.MINIMUM_SHOWN_SEGMENTS)];
        System.arraycopy(segments, 0, kept, 0, Math.min(written, kept.length));
        return Optional.of(TupleValue.of(kept));
    }

    Value specInteger(int from, int length) {
        int cp = from;
        int remaining = length;
        long accumulated = 0;
        if (at(cp) == '0') {
            cp += 2;
            remaining -= 2;
            accumulated = hexadecimal(cp, remaining);
        } else if (at(cp) == '2') {
            cp += 2;
            remaining -= 2;
            while (remaining-- > 0) {
                accumulated = accumulated * 2 + (at(cp) == '1' ? 1 : 0);
                cp++;
            }
        } else if (at(cp) == '8') {
            cp += 2;
            remaining -= 2;
            while (remaining-- > 0) {
                accumulated = accumulated * 8 + (at(cp) - '0');
                cp++;
            }
        } else if (at(cp + 1) == '6') {
            cp += 3;
            remaining -= 3;
            accumulated = hexadecimal(cp, remaining);
        } else if (at(cp + 1) == '0') {
            cp += 3;
            remaining -= 3;
            while (remaining-- > 0) {
                accumulated = accumulated * 10 + (at(cp) - '0');
                cp++;
            }
        }
        return IntegerValue.of(accumulated);
    }

    private long hexadecimal(int from, int length) {
        long accumulated = 0;
        for (int cp = from; cp < from + length; cp++) {
            accumulated = (accumulated << 4) + lex.valueOf(at(cp));
        }
        return accumulated;
    }

    int binaryBase(int from, int length) {
        int cp = from;
        int remaining = length;
        int base = 16;
        if (at(cp) != '#') {
            int ep = grabbedIntegerEnd(cp);
            base = grabbedInteger(cp);
            if (cp == ep || at(ep) != '#') {
                return 0;
            }
            remaining -= ep - cp;
            cp = ep;
        }
        cp++;
        if (at(cp) != '{' || remaining - 2 < 1) {
            return 0;
        }
        return base;
    }
}
