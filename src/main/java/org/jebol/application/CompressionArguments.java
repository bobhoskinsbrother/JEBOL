package org.jebol.application;

import org.jebol.domain.value.IntegerValue;

public final class CompressionArguments {

    private CompressionArguments() {
    }

    public static String readMethod(Command call) {
        if (call.refinements().contains("gzip")) {
            return "gzip";
        }
        return call.refinements().contains("zlib") ? "zlib" : "deflate";
    }

    public static long magnitudeAt(Command call, int at) {
        return ((IntegerValue) call.arguments().get(at)).magnitude();
    }

    public static long readLengthWithinAnyPart(Command call) {
        long length = StoredOctets.countUnitsFromIndex(call.arguments().getFirst());
        if (call.refinements().contains("part")) {
            length = Math.max(0, Math.min(length, magnitudeAt(call, 1)));
        }
        return length;
    }

    public static int countArgumentsBefore(Command call, String refinement) {
        return call.refinements().contains("part") && !"part".equals(refinement) ? 2 : 1;
    }
}
