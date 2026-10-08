package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.Encodings;
import org.jebol.domain.value.BinaryValue;
import org.jebol.domain.value.CharacterValue;
import org.jebol.domain.value.AnyStringValue;
import org.jebol.domain.value.Value;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

public abstract class PercentEncodingNative extends EncodingNative {

    private static final char ESCAPE_UNLESS_ASKED = '%';

    protected PercentEncodingNative(Encodings encodings) {
        super(encodings);
    }

    protected char escapeCharacterIn(List<Value> arguments, Set<String> refinements) {
        return argumentOf("escape", 0, arguments, refinements)
                .map(asked -> (char) ((CharacterValue) asked).codepoint())
                .orElse(ESCAPE_UNLESS_ASKED);
    }

    protected Value asTheSameKindAs(Value original, byte[] octets) {
        return original instanceof AnyStringValue text
                ? text.holding(new String(octets, StandardCharsets.UTF_8))
                : BinaryValue.ofBytes(octets);
    }
}
