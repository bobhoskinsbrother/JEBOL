package org.jebol.domain.value;

public final class FileValue extends AnyStringValue {

    public static final AnyStringDatatype TYPE = new AnyStringDatatype("file") {

        @Override
        public AnyStringValue holding(StringStorage storage, int index) {
            return new FileValue(storage, index);
        }
    };

    private static final String DELIMITERS_WRITTEN_AS_HEX = ";\"()[]{}<>\\^%:";

    FileValue(StringStorage storage, int index) {
        super(storage, index);
    }

    public static FileValue of(String path) {
        return new FileValue(StringStorage.of(path), 1);
    }

    @Override
    public Datatype datatype() {
        return TYPE;
    }

    @Override
    FileValue sameKindOver(StringStorage storage, int index) {
        return new FileValue(storage, index);
    }

    @Override
    public String mold() {
        String text = text();
        if (text.isEmpty()) {
            return "%\"\"";
        }
        StringBuilder written = new StringBuilder("%");
        text.codePoints().forEach(codepoint -> {
            if (isWrittenAsHex(codepoint)) {
                written.append("%").append("%02X".formatted(codepoint));
            } else {
                written.appendCodePoint(codepoint);
            }
        });
        return written.toString();
    }

    @Override
    public boolean isALocation() {
        return true;
    }

    private boolean isWrittenAsHex(int codepoint) {
        return codepoint <= 0x20 || codepoint == 0x7F
                || DELIMITERS_WRITTEN_AS_HEX.indexOf(codepoint) >= 0;
    }
}
