package org.jebol.domain.value;

import org.jebol.domain.value.DeciSignificands.Scaled;
import org.jebol.domain.value.DeciSignificands.Shifted;

import java.math.BigInteger;
import java.util.Optional;

import static org.jebol.domain.value.DeciSignificands.EXACT;
import static org.jebol.domain.value.DeciSignificands.EXACTLY_HALF;
import static org.jebol.domain.value.DeciSignificands.LESS_THAN_HALF;
import static org.jebol.domain.value.DeciSignificands.MORE_THAN_HALF;
import static org.jebol.domain.value.DeciSignificands.THE_SMALLEST_EXPONENT;

public final class DeciReading {

    private static final DeciSignificands SIGNIFICANDS = new DeciSignificands();

    private static final BigInteger TEN_TO_THE_25 = BigInteger.TEN.pow(25);

    private static final BigInteger THE_LARGEST_SIGNIFICAND = BigInteger.TEN.pow(26).subtract(BigInteger.ONE);

    private static final int THE_LARGEST_EXPONENT_WRITTEN = 200_000_000;

    private final String text;

    private int at;

    public DeciReading(String text) {
        this.text = text;
    }

    public Optional<Deci> theWholeOf() {
        Deci read = readFrom(0);
        return at == 0 || at != text.length() ? Optional.empty() : Optional.of(read);
    }

    public int readTo() {
        return at;
    }

    private char charAt(int position) {
        return position < text.length() ? text.charAt(position) : '\0';
    }

    private boolean isDigit(char character) {
        return character >= '0' && character <= '9';
    }

    public Deci readFrom(int start) {
        at = start;
        boolean negative = false;
        if (charAt(at) == '+') {
            at++;
        } else if (charAt(at) == '-') {
            negative = true;
            at++;
        }
        if (charAt(at) == '$') {
            at++;
        }
        BigInteger significand = BigInteger.ZERO;
        int shifted = 0;
        boolean fullPrecision = false;
        boolean pointSeen = false;
        int truncation = EXACT;
        while (true) {
            char here = charAt(at);
            if (isDigit(here)) {
                int digit = here - '0';
                if (significand.compareTo(TEN_TO_THE_25) < 0) {
                    significand = significand.multiply(BigInteger.TEN).add(BigInteger.valueOf(digit));
                    if (pointSeen) {
                        shifted--;
                    }
                } else {
                    if (fullPrecision) {
                        if (truncation == EXACT && digit != 0) {
                            truncation = LESS_THAN_HALF;
                        } else if (truncation == EXACTLY_HALF && digit != 0) {
                            truncation = MORE_THAN_HALF;
                        }
                    } else {
                        fullPrecision = true;
                        if (digit > 0) {
                            truncation = digit < 5 ? LESS_THAN_HALF : (digit == 5 ? EXACTLY_HALF : MORE_THAN_HALF);
                        }
                    }
                    if (!pointSeen) {
                        shifted++;
                    }
                }
            } else if (here == '.' || here == ',') {
                if (pointSeen) {
                    at = start;
                    return Deci.ZERO;
                }
                pointSeen = true;
            } else if (here != '\'') {
                break;
            }
            at++;
        }
        int exponent = theExponentWritten();
        exponent += shifted;
        if (SIGNIFICANDS.theTruncationRoundsUp(truncation, significand) && exponent >= THE_SMALLEST_EXPONENT) {
            if (significand.compareTo(THE_LARGEST_SIGNIFICAND) < 0) {
                significand = significand.add(BigInteger.ONE);
            } else {
                Shifted oneOff = SIGNIFICANDS.shiftedRight(significand, 1, truncation);
                truncation = oneOff.truncation();
                exponent++;
                significand = SIGNIFICANDS.roundedUpWhenTheTruncationSays(oneOff);
            }
        }
        Scaled scaled = SIGNIFICANDS.timesTenToThe(significand, 0, exponent, truncation);
        return new Deci(scaled.significand(), scaled.exponent(), negative);
    }

    private int theExponentWritten() {
        if (charAt(at) != 'e' && charAt(at) != 'E') {
            return 0;
        }
        at++;
        int sign = 1;
        if (charAt(at) == '+') {
            at++;
        } else if (charAt(at) == '-') {
            at++;
            sign = -1;
        }
        int exponent = 0;
        while (isDigit(charAt(at))) {
            exponent = exponent * 10 + (charAt(at) - '0');
            if (exponent > THE_LARGEST_EXPONENT_WRITTEN) {
                if (sign == 1) {
                    throw Raised.of(EvaluationFailure.OVERFLOW);
                }
                exponent = THE_LARGEST_EXPONENT_WRITTEN;
            }
            at++;
        }
        return exponent * sign;
    }
}
