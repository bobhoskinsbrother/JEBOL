package org.jebol.domain.eval.compression.bzip2;

public final class Bzip2Buffers {

    public byte[] input = new byte[0];
    public int nextIn;
    public int availableIn;
    public byte[] output = new byte[0];
    public int nextOut;
    public int availableOut;
}
