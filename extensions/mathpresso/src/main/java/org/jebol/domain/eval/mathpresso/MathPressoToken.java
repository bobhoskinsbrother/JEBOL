package org.jebol.domain.eval.mathpresso;

record MathPressoToken(Type type, int position, int size, double value) {

    enum Type {
        INVALID, SYMBOL, NUMBER, VAR,
        DOT, COMMA, SEMICOLON, QUESTION_MARK, COLON, LEFT_CURLY, RIGHT_CURLY, LEFT_BRACKET, RIGHT_BRACKET,
        LEFT_PAREN, RIGHT_PAREN, ADD, SUB, MUL, DIV, MOD, NOT, BIT_AND, BIT_OR, BIT_XOR, BIT_NEG, ASSIGN, LT, GT,
        PLUS_PLUS, MINUS_MINUS, EQ, NE, LE, GE, LOGICAL_AND, LOGICAL_OR, SHIFT_RIGHT_ARITHMETIC, SHIFT_RIGHT,
        SHIFT_LEFT, ASSIGN_ADD, ASSIGN_SUB, ASSIGN_MUL, ASSIGN_DIV, ASSIGN_MOD, ASSIGN_BIT_AND, ASSIGN_BIT_OR,
        ASSIGN_BIT_XOR, ASSIGN_SHIFT_RIGHT_ARITHMETIC, ASSIGN_SHIFT_RIGHT, ASSIGN_SHIFT_LEFT, END
    }

    static MathPressoToken of(Type type, int position, int size) {
        return new MathPressoToken(type, position, size, 0.0);
    }
}
