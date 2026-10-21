package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.*;

import java.util.List;
import java.util.Set;

public class FormOidNative extends DefaultNative {

    private static final int ARCS_PACKED_INTO_FIRST_BYTE = 40;
    private static final int GROUP_BITS = 7;
    private static final int GROUP_MASK = 0x7F;
    private static final int MORE_GROUPS_FOLLOW = 0x80;

    @Override
    public String nativeName() {
        return "form-oid";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(Parameter.of("oid", Set.of(BinaryValue.TYPE)));
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> StringValue.of(
                writeObjectIdentifier(((BinaryValue) arguments.getFirst()).octetsFromHere()));
    }

    private String writeObjectIdentifier(byte[] encoded) {
        if (encoded.length == 0) {
            return "";
        }
        StringBuilder written = new StringBuilder();
        int first = encoded[0] & 0xFF;
        written.append(first / ARCS_PACKED_INTO_FIRST_BYTE)
                .append('.')
                .append(first % ARCS_PACKED_INTO_FIRST_BYTE);
        long group = 0;
        for (int at = 1; at < encoded.length; at++) {
            int octet = encoded[at] & 0xFF;
            group = (group << GROUP_BITS) + (octet & GROUP_MASK);
            if ((octet & MORE_GROUPS_FOLLOW) == 0) {
                written.append('.').append(group);
                group = 0;
            }
        }
        return written.toString();
    }
}
