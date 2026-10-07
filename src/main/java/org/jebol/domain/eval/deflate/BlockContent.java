package org.jebol.domain.eval.deflate;

interface BlockContent {

    void writeWith(BlockEncoder encoder, HuffmanCodes codes, BitWriter out, byte[] input, int begin);
}
