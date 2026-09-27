package org.jebol.domain.eval.bit.bitType;

import org.jebol.domain.eval.Arithmetic;
import org.jebol.domain.eval.arithmetic.BitwiseOperation;
import org.jebol.domain.value.BinaryValue;
import org.jebol.domain.value.BitsetValue;
import org.jebol.domain.value.Value;

public class Bitsets implements BitType {

    @Override
    public boolean shouldHandle(Value left, Value right) {
        return left instanceof BitsetValue;
    }

    @Override
    public Value combine(Value left, Value right, BitwiseOperation operation) {
        BitsetValue ours = (BitsetValue) left;
        return BitsetValue.of(withoutTheTrailingZeros(
                octetByOctet(ours.octets(), whatItCanTake(left, right), operation)));
    }

    private byte[] whatItCanTake(Value left, Value right) {
        return switch (right) {
            case BitsetValue members -> members.octets();
            case BinaryValue octets -> theBytesOf(octets);
            default -> throw Arithmetic.notRelated(left, right);
        };
    }

    private byte[] theBytesOf(BinaryValue octets) {
        byte[] read = new byte[octets.lengthFromHere()];
        for (int at = 0; at < read.length; at++) {
            read[at] = (byte) octets.storage().at(octets.index() + at);
        }
        return read;
    }

    private byte[] octetByOctet(byte[] ours, byte[] theirs, BitwiseOperation operation) {
        byte[] both = new byte[Math.max(ours.length, theirs.length)];
        for (int at = 0; at < both.length; at++) {
            long mine = at < ours.length ? ours[at] & 0xFF : 0;
            long yours = at < theirs.length ? theirs[at] & 0xFF : 0;
            both[at] = (byte) combinedBits(mine, yours, operation);
        }
        return both;
    }

    private byte[] withoutTheTrailingZeros(byte[] octets) {
        int kept = octets.length;
        while (kept > 0 && octets[kept - 1] == 0) {
            kept--;
        }
        byte[] trimmed = new byte[kept];
        System.arraycopy(octets, 0, trimmed, 0, kept);
        return trimmed;
    }

}
