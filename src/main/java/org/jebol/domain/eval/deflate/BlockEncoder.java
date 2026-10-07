package org.jebol.domain.eval.deflate;

import java.util.Arrays;

import static org.jebol.domain.eval.deflate.DeflateTables.END_OF_BLOCK;
import static org.jebol.domain.eval.deflate.DeflateTables.EXTRA_LENGTH_BITS;
import static org.jebol.domain.eval.deflate.DeflateTables.EXTRA_OFFSET_BITS;
import static org.jebol.domain.eval.deflate.DeflateTables.EXTRA_PRECODE_BITS;
import static org.jebol.domain.eval.deflate.DeflateTables.FIRST_LENGTH_SYMBOL;
import static org.jebol.domain.eval.deflate.DeflateTables.LENGTH_SLOT;
import static org.jebol.domain.eval.deflate.DeflateTables.LENGTH_SLOT_BASE;
import static org.jebol.domain.eval.deflate.DeflateTables.LITLEN_SYMBOL_COUNT;
import static org.jebol.domain.eval.deflate.DeflateTables.LONGEST_LITLEN_CODEWORD;
import static org.jebol.domain.eval.deflate.DeflateTables.LONGEST_OFFSET_CODEWORD;
import static org.jebol.domain.eval.deflate.DeflateTables.LONGEST_PRECODE_CODEWORD;
import static org.jebol.domain.eval.deflate.DeflateTables.LONGEST_STORED_BLOCK;
import static org.jebol.domain.eval.deflate.DeflateTables.MAX_MATCH_LENGTH;
import static org.jebol.domain.eval.deflate.DeflateTables.MIN_MATCH_LENGTH;
import static org.jebol.domain.eval.deflate.DeflateTables.OFFSET_SLOT_BASE;
import static org.jebol.domain.eval.deflate.DeflateTables.OFFSET_SLOT_BELOW_257;
import static org.jebol.domain.eval.deflate.DeflateTables.OFFSET_SYMBOL_COUNT;
import static org.jebol.domain.eval.deflate.DeflateTables.PRECODE_LENGTH_ORDER;
import static org.jebol.domain.eval.deflate.DeflateTables.PRECODE_SYMBOL_COUNT;

final class BlockEncoder {

    private static final int STATIC_HUFFMAN_BLOCK = 1;

    private static final int DYNAMIC_HUFFMAN_BLOCK = 2;

    private static final int BLOCK_HEADER_BITS = 3;

    private static final int CODE_COUNT_FIELD_BITS = 5 + 5 + 4;

    private static final int PRECODE_LENGTH_BITS = 3;

    private static final int STORED_LENGTH_FIELDS_BITS = 32;

    private static final int STORED_BLOCK_OVERHEAD_BITS = 40;

    private static final int STATIC_END_OF_BLOCK_BITS = 7;

    private static final int STATIC_LOW_LITERAL_BITS = 8;

    private static final int STATIC_HIGH_LITERAL_BITS = 9;

    private static final int STATIC_OFFSET_BITS = 5;

    private static final int FIRST_HIGH_LITERAL = 144;

    private static final int FIRST_SEVEN_BIT_LENGTH_SYMBOL = 280;

    private static final int FEWEST_LITLEN_SYMBOLS_SENT = 257;

    private static final int FEWEST_EXPLICIT_PRECODE_LENGTHS = 4;

    private static final int PRECODE_SYMBOL_MASK = 0x1F;

    private static final int PRECODE_EXTRA_SHIFT = 5;

    private static final int REPEAT_PREVIOUS = 16;

    private static final int REPEAT_SHORT_ZERO_RUN = 17;

    private static final int REPEAT_LONG_ZERO_RUN = 18;

    private static final int OFFSET_SLOT_SHIFT_ABOVE_256 = 29;

    final int[] litlenFrequencies = new int[LITLEN_SYMBOL_COUNT];

    final int[] offsetFrequencies = new int[OFFSET_SYMBOL_COUNT];

    final HuffmanCodes codes = new HuffmanCodes();

    final HuffmanCodes staticCodes = new HuffmanCodes();

    private final HuffmanCodeBuilder builder = new HuffmanCodeBuilder();

    private final int[] precodeFrequencies = new int[PRECODE_SYMBOL_COUNT];

    private final int[] precodeCodewords = new int[PRECODE_SYMBOL_COUNT];

    private final int[] precodeLengths = new int[PRECODE_SYMBOL_COUNT];

    private final int[] precodeItems = new int[LITLEN_SYMBOL_COUNT + OFFSET_SYMBOL_COUNT];

    private final int[] fullLengthCodewords = new int[MAX_MATCH_LENGTH + 1];

    private final int[] fullLengthLengths = new int[MAX_MATCH_LENGTH + 1];

    private int litlenSymbolsSent;

    private int offsetSymbolsSent;

    private int explicitPrecodeLengths;

    private int precodeItemCount;

    BlockEncoder() {
        makeTheStaticCodes();
    }

    private void makeTheStaticCodes() {
        for (int symbol = 0; symbol < LITLEN_SYMBOL_COUNT; symbol++) {
            litlenFrequencies[symbol] = staticLitlenFrequencyOf(symbol);
        }
        Arrays.fill(offsetFrequencies, 1);
        makeHuffmanCodes(staticCodes);
    }

    private int staticLitlenFrequencyOf(int symbol) {
        if (symbol < FIRST_HIGH_LITERAL) {
            return 2;
        }
        if (symbol < FIRST_LENGTH_SYMBOL - 1) {
            return 1;
        }
        return symbol < FIRST_SEVEN_BIT_LENGTH_SYMBOL ? 4 : 2;
    }

    void resetFrequencies() {
        Arrays.fill(litlenFrequencies, 0);
        Arrays.fill(offsetFrequencies, 0);
    }

    void makeHuffmanCodes(HuffmanCodes into) {
        builder.build(LITLEN_SYMBOL_COUNT, LONGEST_LITLEN_CODEWORD, litlenFrequencies,
                into.lengths, 0, into.litlenCodewords);
        builder.build(OFFSET_SYMBOL_COUNT, LONGEST_OFFSET_CODEWORD, offsetFrequencies,
                into.lengths, HuffmanCodes.OFFSET_LENGTHS_FROM, into.offsetCodewords);
    }

    int offsetSlotOf(int offset) {
        int shift = (256 - offset) >>> OFFSET_SLOT_SHIFT_ABOVE_256;
        return OFFSET_SLOT_BELOW_257[(offset - 1) >> shift] + (shift << 1);
    }

    void finishBlock(BitWriter out, byte[] input, int begin, int length, BlockContent content, boolean finalBlock) {
        litlenFrequencies[END_OF_BLOCK]++;
        makeHuffmanCodes(codes);
        flushBlock(out, input, begin, length, content, finalBlock);
    }

    void flushBlock(BitWriter out, byte[] input, int begin, int length, BlockContent content, boolean finalBlock) {
        precomputeHuffmanHeader();
        long dynamicCost = dynamicBlockCost();
        long staticCost = staticBlockCost();
        long storedCost = storedBlockCost(out.pendingCount(), length);
        long cheapest = Math.min(dynamicCost, Math.min(staticCost, storedCost));
        if (cheapest == storedCost) {
            writeStoredBlocks(out, input, begin, length, finalBlock);
            return;
        }
        HuffmanCodes chosen;
        if (cheapest == staticCost) {
            chosen = staticCodes;
            out.add(finalBlock ? 1 : 0, 1);
            out.add(STATIC_HUFFMAN_BLOCK, 2);
        } else {
            chosen = codes;
            writeDynamicHeader(out, finalBlock);
        }
        computeFullLengthCodewords(chosen);
        content.writeWith(this, chosen, out, input, begin);
        out.add(chosen.litlenCodewords[END_OF_BLOCK], chosen.litlenLength(END_OF_BLOCK));
    }

    private long dynamicBlockCost() {
        long cost = BLOCK_HEADER_BITS + headerCost();
        for (int symbol = 0; symbol < FIRST_LENGTH_SYMBOL; symbol++) {
            cost += (long) litlenFrequencies[symbol] * codes.litlenLength(symbol);
        }
        for (int slot = 0; slot < EXTRA_LENGTH_BITS.length; slot++) {
            cost += (long) litlenFrequencies[FIRST_LENGTH_SYMBOL + slot]
                    * (EXTRA_LENGTH_BITS[slot] + codes.litlenLength(FIRST_LENGTH_SYMBOL + slot));
        }
        for (int slot = 0; slot < EXTRA_OFFSET_BITS.length; slot++) {
            cost += (long) offsetFrequencies[slot] * (EXTRA_OFFSET_BITS[slot] + codes.offsetLength(slot));
        }
        return cost;
    }

    private long headerCost() {
        long cost = CODE_COUNT_FIELD_BITS + (long) PRECODE_LENGTH_BITS * explicitPrecodeLengths;
        for (int symbol = 0; symbol < PRECODE_SYMBOL_COUNT; symbol++) {
            cost += (long) precodeFrequencies[symbol] * (EXTRA_PRECODE_BITS[symbol] + precodeLengths[symbol]);
        }
        return cost;
    }

    private long staticBlockCost() {
        long cost = BLOCK_HEADER_BITS + STATIC_END_OF_BLOCK_BITS;
        for (int symbol = 0; symbol < END_OF_BLOCK; symbol++) {
            cost += (long) litlenFrequencies[symbol]
                    * (symbol < FIRST_HIGH_LITERAL ? STATIC_LOW_LITERAL_BITS : STATIC_HIGH_LITERAL_BITS);
        }
        for (int slot = 0; slot < EXTRA_LENGTH_BITS.length; slot++) {
            cost += (long) litlenFrequencies[FIRST_LENGTH_SYMBOL + slot]
                    * (EXTRA_LENGTH_BITS[slot] + staticCodes.litlenLength(FIRST_LENGTH_SYMBOL + slot));
        }
        for (int slot = 0; slot < EXTRA_OFFSET_BITS.length; slot++) {
            cost += (long) offsetFrequencies[slot] * (EXTRA_OFFSET_BITS[slot] + STATIC_OFFSET_BITS);
        }
        return cost;
    }

    private long storedBlockCost(int pendingBits, int length) {
        long blocks = (length + (long) LONGEST_STORED_BLOCK - 1) / LONGEST_STORED_BLOCK;
        return BLOCK_HEADER_BITS + (-(pendingBits + BLOCK_HEADER_BITS) & 7) + STORED_LENGTH_FIELDS_BITS
                + STORED_BLOCK_OVERHEAD_BITS * (blocks - 1) + 8L * length;
    }

    private void writeStoredBlocks(BitWriter out, byte[] input, int begin, int length, boolean finalBlock) {
        int at = begin;
        int end = begin + length;
        do {
            boolean last = end - at <= LONGEST_STORED_BLOCK;
            int count = last ? end - at : LONGEST_STORED_BLOCK;
            out.startStoredBlock(last && finalBlock);
            out.writeLittleEndianShort(count);
            out.writeLittleEndianShort(~count);
            out.write(input, at, count);
            at += count;
        } while (at != end);
    }

    private void writeDynamicHeader(BitWriter out, boolean finalBlock) {
        out.add(finalBlock ? 1 : 0, 1);
        out.add(DYNAMIC_HUFFMAN_BLOCK, 2);
        out.add(litlenSymbolsSent - FEWEST_LITLEN_SYMBOLS_SENT, 5);
        out.add(offsetSymbolsSent - 1, 5);
        out.add(explicitPrecodeLengths - FEWEST_EXPLICIT_PRECODE_LENGTHS, 4);
        for (int at = 0; at < explicitPrecodeLengths; at++) {
            out.add(precodeLengths[PRECODE_LENGTH_ORDER[at]], PRECODE_LENGTH_BITS);
        }
        for (int at = 0; at < precodeItemCount; at++) {
            int item = precodeItems[at];
            int symbol = item & PRECODE_SYMBOL_MASK;
            out.add(precodeCodewords[symbol], precodeLengths[symbol]);
            out.add(item >>> PRECODE_EXTRA_SHIFT, EXTRA_PRECODE_BITS[symbol]);
        }
    }

    void writeLiteral(BitWriter out, HuffmanCodes with, int literal) {
        out.add(with.litlenCodewords[literal], with.litlenLength(literal));
    }

    void writeMatch(BitWriter out, HuffmanCodes with, int length, int offset, int offsetSlot) {
        out.add(fullLengthCodewords[length], fullLengthLengths[length]);
        out.add(with.offsetCodewords[offsetSlot], with.offsetLength(offsetSlot));
        out.add(offset - OFFSET_SLOT_BASE[offsetSlot], EXTRA_OFFSET_BITS[offsetSlot]);
    }

    private void computeFullLengthCodewords(HuffmanCodes with) {
        for (int length = MIN_MATCH_LENGTH; length <= MAX_MATCH_LENGTH; length++) {
            int slot = LENGTH_SLOT[length];
            int symbol = FIRST_LENGTH_SYMBOL + slot;
            int extra = length - LENGTH_SLOT_BASE[slot];
            fullLengthCodewords[length] = with.litlenCodewords[symbol] | (extra << with.litlenLength(symbol));
            fullLengthLengths[length] = with.litlenLength(symbol) + EXTRA_LENGTH_BITS[slot];
        }
    }

    long trueCost() {
        precomputeHuffmanHeader();
        Arrays.fill(codes.lengths, litlenSymbolsSent, LITLEN_SYMBOL_COUNT, 0);
        long cost = headerCost();
        for (int symbol = 0; symbol < FIRST_LENGTH_SYMBOL; symbol++) {
            cost += (long) litlenFrequencies[symbol] * codes.litlenLength(symbol);
        }
        for (int slot = 0; slot < EXTRA_LENGTH_BITS.length; slot++) {
            cost += (long) litlenFrequencies[FIRST_LENGTH_SYMBOL + slot]
                    * (codes.litlenLength(FIRST_LENGTH_SYMBOL + slot) + EXTRA_LENGTH_BITS[slot]);
        }
        for (int slot = 0; slot < EXTRA_OFFSET_BITS.length; slot++) {
            cost += (long) offsetFrequencies[slot] * (codes.offsetLength(slot) + EXTRA_OFFSET_BITS[slot]);
        }
        return cost & 0xFFFFFFFFL;
    }

    private void precomputeHuffmanHeader() {
        int[] lengths = codes.lengths;
        litlenSymbolsSent = LITLEN_SYMBOL_COUNT;
        while (litlenSymbolsSent > FEWEST_LITLEN_SYMBOLS_SENT && lengths[litlenSymbolsSent - 1] == 0) {
            litlenSymbolsSent--;
        }
        offsetSymbolsSent = OFFSET_SYMBOL_COUNT;
        while (offsetSymbolsSent > 1 && lengths[HuffmanCodes.OFFSET_LENGTHS_FROM + offsetSymbolsSent - 1] == 0) {
            offsetSymbolsSent--;
        }
        boolean shifted = litlenSymbolsSent != LITLEN_SYMBOL_COUNT;
        if (shifted) {
            System.arraycopy(lengths, HuffmanCodes.OFFSET_LENGTHS_FROM, lengths, litlenSymbolsSent, offsetSymbolsSent);
        }
        precodeItemCount = computePrecodeItems(lengths, litlenSymbolsSent + offsetSymbolsSent);
        builder.build(PRECODE_SYMBOL_COUNT, LONGEST_PRECODE_CODEWORD, precodeFrequencies,
                precodeLengths, 0, precodeCodewords);
        explicitPrecodeLengths = PRECODE_SYMBOL_COUNT;
        while (explicitPrecodeLengths > FEWEST_EXPLICIT_PRECODE_LENGTHS
                && precodeLengths[PRECODE_LENGTH_ORDER[explicitPrecodeLengths - 1]] == 0) {
            explicitPrecodeLengths--;
        }
        if (shifted) {
            System.arraycopy(lengths, litlenSymbolsSent, lengths, HuffmanCodes.OFFSET_LENGTHS_FROM, offsetSymbolsSent);
        }
    }

    private int computePrecodeItems(int[] lengths, int lengthCount) {
        Arrays.fill(precodeFrequencies, 0);
        int items = 0;
        int runStart = 0;
        do {
            int length = lengths[runStart];
            int runEnd = runStart;
            do {
                runEnd++;
            } while (runEnd != lengthCount && length == lengths[runEnd]);
            if (length == 0) {
                while (runEnd - runStart >= 11) {
                    int extra = Math.min(runEnd - runStart - 11, 0x7F);
                    precodeFrequencies[REPEAT_LONG_ZERO_RUN]++;
                    precodeItems[items++] = REPEAT_LONG_ZERO_RUN | (extra << PRECODE_EXTRA_SHIFT);
                    runStart += 11 + extra;
                }
                if (runEnd - runStart >= 3) {
                    int extra = Math.min(runEnd - runStart - 3, 0x7);
                    precodeFrequencies[REPEAT_SHORT_ZERO_RUN]++;
                    precodeItems[items++] = REPEAT_SHORT_ZERO_RUN | (extra << PRECODE_EXTRA_SHIFT);
                    runStart += 3 + extra;
                }
            } else if (runEnd - runStart >= 4) {
                precodeFrequencies[length]++;
                precodeItems[items++] = length;
                runStart++;
                do {
                    int extra = Math.min(runEnd - runStart - 3, 0x3);
                    precodeFrequencies[REPEAT_PREVIOUS]++;
                    precodeItems[items++] = REPEAT_PREVIOUS | (extra << PRECODE_EXTRA_SHIFT);
                    runStart += 3 + extra;
                } while (runEnd - runStart >= 3);
            }
            while (runStart != runEnd) {
                precodeFrequencies[length]++;
                precodeItems[items++] = length;
                runStart++;
            }
        } while (runStart != lengthCount);
        return items;
    }
}
