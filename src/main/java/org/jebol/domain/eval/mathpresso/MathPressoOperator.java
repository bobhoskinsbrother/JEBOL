package org.jebol.domain.eval.mathpresso;

import java.util.Arrays;
import java.util.List;

enum MathPressoOperator {

    NONE("<none>", 1, 0, false, false, Neutral.NEVER),
    NEG("-", 1, 3, true, false, Neutral.NEVER),
    NOT("!", 1, 3, true, false, Neutral.NEVER),
    IS_NAN("isnan", 1, 0, false, true, Neutral.NEVER),
    IS_INF("isinf", 1, 0, false, true, Neutral.NEVER),
    IS_FINITE("isfinite", 1, 0, false, true, Neutral.NEVER),
    SIGN_BIT("signbit", 1, 0, false, true, Neutral.NEVER),
    ROUND("round", 1, 0, false, true, Neutral.NEVER),
    ROUND_EVEN("roundeven", 1, 0, false, true, Neutral.NEVER),
    TRUNC("trunc", 1, 0, false, true, Neutral.NEVER),
    FLOOR("floor", 1, 0, false, true, Neutral.NEVER),
    CEIL("ceil", 1, 0, false, true, Neutral.NEVER),
    ABS("abs", 1, 0, false, true, Neutral.NEVER),
    EXP("exp", 1, 0, false, true, Neutral.NEVER),
    LOG("log", 1, 0, false, true, Neutral.NEVER),
    LOG2("log2", 1, 0, false, true, Neutral.NEVER),
    LOG10("log10", 1, 0, false, true, Neutral.NEVER),
    SQRT("sqrt", 1, 0, false, true, Neutral.NEVER),
    FRAC("frac", 1, 0, false, true, Neutral.NEVER),
    RECIP("recip", 1, 0, false, true, Neutral.NEVER),
    SIN("sin", 1, 0, false, true, Neutral.NEVER),
    COS("cos", 1, 0, false, true, Neutral.NEVER),
    TAN("tan", 1, 0, false, true, Neutral.NEVER),
    SINH("sinh", 1, 0, false, true, Neutral.NEVER),
    COSH("cosh", 1, 0, false, true, Neutral.NEVER),
    TANH("tanh", 1, 0, false, true, Neutral.NEVER),
    ASIN("asin", 1, 0, false, true, Neutral.NEVER),
    ACOS("acos", 1, 0, false, true, Neutral.NEVER),
    ATAN("atan", 1, 0, false, true, Neutral.NEVER),
    ASSIGN("=", 2, 15, true, false, Neutral.NEVER),
    EQ("==", 2, 9, false, false, Neutral.NEVER),
    NE("!=", 2, 9, false, false, Neutral.NEVER),
    LT("<", 2, 8, false, false, Neutral.NEVER),
    LE("<=", 2, 8, false, false, Neutral.NEVER),
    GT(">", 2, 8, false, false, Neutral.NEVER),
    GE(">=", 2, 8, false, false, Neutral.NEVER),
    ADD("+", 2, 6, false, false, Neutral.ZERO_EITHER_SIDE),
    SUB("-", 2, 6, false, false, Neutral.ZERO_ON_RIGHT),
    MUL("*", 2, 5, false, false, Neutral.ONE_EITHER_SIDE),
    DIV("/", 2, 5, false, false, Neutral.ONE_ON_RIGHT),
    MOD("%", 2, 5, false, false, Neutral.NEVER),
    AVG("avg", 2, 0, false, true, Neutral.NEVER),
    MIN("min", 2, 0, false, true, Neutral.NEVER),
    MAX("max", 2, 0, false, true, Neutral.NEVER),
    POW("pow", 2, 0, false, true, Neutral.ONE_ON_RIGHT),
    ATAN2("atan2", 2, 0, false, true, Neutral.NEVER),
    HYPOT("hypot", 2, 0, false, true, Neutral.NEVER),
    COPY_SIGN("copysign", 2, 0, false, true, Neutral.NEVER);

    enum Neutral {
        NEVER(false, false, false, false),
        ZERO_EITHER_SIDE(true, true, false, false),
        ZERO_ON_RIGHT(false, true, false, false),
        ONE_EITHER_SIDE(false, false, true, true),
        ONE_ON_RIGHT(false, false, false, true);

        private final boolean zeroOnLeft;
        private final boolean zeroOnRight;
        private final boolean oneOnLeft;
        private final boolean oneOnRight;

        Neutral(boolean zeroOnLeft, boolean zeroOnRight, boolean oneOnLeft, boolean oneOnRight) {
            this.zeroOnLeft = zeroOnLeft;
            this.zeroOnRight = zeroOnRight;
            this.oneOnLeft = oneOnLeft;
            this.oneOnRight = oneOnRight;
        }
    }

    private final String spelling;
    private final int operandCount;
    private final int precedence;
    private final boolean rightToLeft;
    private final boolean intrinsic;
    private final Neutral neutral;

    MathPressoOperator(String spelling, int operandCount, int precedence, boolean rightToLeft, boolean intrinsic,
                       Neutral neutral) {
        this.spelling = spelling;
        this.operandCount = operandCount;
        this.precedence = precedence;
        this.rightToLeft = rightToLeft;
        this.intrinsic = intrinsic;
        this.neutral = neutral;
    }

    static List<MathPressoOperator> intrinsics() {
        return Arrays.stream(values()).filter(operator -> operator.intrinsic).toList();
    }

    String spelling() {
        return spelling;
    }

    int operandCount() {
        return operandCount;
    }

    int precedence() {
        return precedence;
    }

    boolean isAssignment() {
        return this == ASSIGN;
    }

    boolean yieldsTo(int precedenceOfNext) {
        return precedence > precedenceOfNext || (precedence == precedenceOfNext && rightToLeft);
    }

    boolean isNeutralOnLeft(double value) {
        return (value == 0.0 && neutral.zeroOnLeft) || (value == 1.0 && neutral.oneOnLeft);
    }

    boolean isNeutralOnRight(double value) {
        return (value == 0.0 && neutral.zeroOnRight) || (value == 1.0 && neutral.oneOnRight);
    }

    boolean isCommutativeForSwap() {
        return this == ADD || this == MUL || this == AVG || this == MIN || this == MAX;
    }
}
