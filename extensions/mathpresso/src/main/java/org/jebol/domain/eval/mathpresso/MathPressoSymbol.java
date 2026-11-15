package org.jebol.domain.eval.mathpresso;

final class MathPressoSymbol {

    enum Kind { INTRINSIC, VARIABLE }

    private final String name;
    private final Kind kind;
    private final MathPressoOperator operator;
    private final boolean global;
    private boolean declared;
    private boolean assigned;
    private boolean readOnly;
    private boolean altered;
    private long valueBits;
    private int slot = -1;
    private int offset;
    private MathPressoNode declaration;
    private int writeCount;

    private MathPressoSymbol(String name, Kind kind, MathPressoOperator operator, boolean global) {
        this.name = name;
        this.kind = kind;
        this.operator = operator;
        this.global = global;
    }

    static MathPressoSymbol intrinsic(MathPressoOperator operator) {
        MathPressoSymbol made = new MathPressoSymbol(operator.spelling(), Kind.INTRINSIC, operator, true);
        made.declared = true;
        return made;
    }

    static MathPressoSymbol constant(String name, long bits) {
        MathPressoSymbol made = new MathPressoSymbol(name, Kind.VARIABLE, null, true);
        made.declared = true;
        made.assigned = true;
        made.readOnly = true;
        made.valueBits = bits;
        return made;
    }

    static MathPressoSymbol contextVariable(String name, int offset) {
        MathPressoSymbol made = new MathPressoSymbol(name, Kind.VARIABLE, null, true);
        made.declared = true;
        made.offset = offset;
        return made;
    }

    static MathPressoSymbol declaredVariable(String name) {
        return new MathPressoSymbol(name, Kind.VARIABLE, null, false);
    }

    MathPressoSymbol shadow() {
        MathPressoSymbol copy = new MathPressoSymbol(name, kind, operator, global);
        copy.declared = declared;
        copy.assigned = assigned;
        copy.readOnly = readOnly;
        copy.altered = altered;
        if (kind == Kind.VARIABLE) {
            copy.slot = slot;
            copy.offset = offset;
            copy.valueBits = valueBits;
        }
        return copy;
    }

    String name() {
        return name;
    }

    boolean isIntrinsic() {
        return kind == Kind.INTRINSIC;
    }

    MathPressoOperator operator() {
        return operator;
    }

    boolean isGlobal() {
        return global;
    }

    boolean isDeclared() {
        return declared;
    }

    void markDeclared() {
        declared = true;
    }

    boolean isAssigned() {
        return assigned;
    }

    void markAssigned() {
        assigned = true;
    }

    boolean isReadOnly() {
        return readOnly;
    }

    boolean isAltered() {
        return altered;
    }

    void markAltered() {
        altered = true;
    }

    long valueBits() {
        return valueBits;
    }

    void setValueBits(long bits) {
        valueBits = bits;
    }

    int slot() {
        return slot;
    }

    void setSlot(int slot) {
        this.slot = slot;
    }

    int offset() {
        return offset;
    }

    MathPressoNode declaration() {
        return declaration;
    }

    void setDeclaration(MathPressoNode declaration) {
        this.declaration = declaration;
    }

    int writeCount() {
        return writeCount;
    }

    void countWrite() {
        writeCount++;
    }
}
