package org.jebol.domain.eval.compression.bzip2;

import java.util.Arrays;

public final class Bzip2CompressionStream {

    private enum Mode { IDLE, RUNNING, FLUSHING, FINISHING }

    private static final int NO_PENDING_OCTET = 256;
    private static final int LONGEST_RUN = 255;
    private static final int SHORTEST_COUNTED_RUN = 4;

    private final Bzip2BlockCompressor blocks;
    private Mode mode = Mode.RUNNING;
    private boolean isTakingInput = true;
    private int inputExpected;
    private int pendingOctet = NO_PENDING_OCTET;
    private int pendingLength;
    private int compressedTaken;
    private Bzip2Buffers io;

    public Bzip2CompressionStream(int blockSize100k) {
        blocks = new Bzip2BlockCompressor(blockSize100k);
        prepareNewBlock();
    }

    private void prepareNewBlock() {
        blocks.blockLength = 0;
        blocks.compressedLength = 0;
        compressedTaken = 0;
        blocks.blockCrc = Bzip2Tables.CRC_START;
        Arrays.fill(blocks.inUse, false);
        blocks.blockNumber++;
    }

    private boolean isRunEmpty() {
        return !(pendingOctet < NO_PENDING_OCTET && pendingLength > 0);
    }

    private void addPendingRunToBlock() {
        byte octet = (byte) pendingOctet;
        for (int i = 0; i < pendingLength; i++) {
            blocks.blockCrc = Bzip2Tables.updateCrc(blocks.blockCrc, pendingOctet);
        }
        blocks.inUse[pendingOctet] = true;
        int copies = Math.min(pendingLength, SHORTEST_COUNTED_RUN);
        for (int i = 0; i < copies; i++) {
            blocks.block[blocks.blockLength++] = octet;
        }
        if (pendingLength >= SHORTEST_COUNTED_RUN) {
            blocks.inUse[pendingLength - SHORTEST_COUNTED_RUN] = true;
            blocks.block[blocks.blockLength++] = (byte) (pendingLength - SHORTEST_COUNTED_RUN);
        }
    }

    private void flushPendingRun() {
        if (pendingOctet < NO_PENDING_OCTET) {
            addPendingRunToBlock();
        }
        pendingOctet = NO_PENDING_OCTET;
        pendingLength = 0;
    }

    private void addOctetToBlock(int octet) {
        if (octet != pendingOctet && pendingLength == 1) {
            blocks.blockCrc = Bzip2Tables.updateCrc(blocks.blockCrc, pendingOctet);
            blocks.inUse[pendingOctet] = true;
            blocks.block[blocks.blockLength++] = (byte) pendingOctet;
            pendingOctet = octet;
        } else if (octet != pendingOctet || pendingLength == LONGEST_RUN) {
            if (pendingOctet < NO_PENDING_OCTET) {
                addPendingRunToBlock();
            }
            pendingOctet = octet;
            pendingLength = 1;
        } else {
            pendingLength++;
        }
    }

    private boolean copyInputUntilStop() {
        boolean didProgress = false;
        boolean isRunning = mode == Mode.RUNNING;
        while (blocks.blockLength < blocks.blockLimit && io.availableIn != 0 && (isRunning || inputExpected != 0)) {
            didProgress = true;
            addOctetToBlock(io.input[io.nextIn] & 0xFF);
            io.nextIn++;
            io.availableIn--;
            if (!isRunning) {
                inputExpected--;
            }
        }
        return didProgress;
    }

    private boolean copyOutputUntilStop() {
        int count = Math.min(io.availableOut, blocks.compressedLength - compressedTaken);
        if (count <= 0) {
            return false;
        }
        System.arraycopy(blocks.compressed, compressedTaken, io.output, io.nextOut, count);
        compressedTaken += count;
        io.nextOut += count;
        io.availableOut -= count;
        return true;
    }

    private boolean handleCompress() {
        boolean didTakeInput = false;
        boolean didGiveOutput = false;
        for (;;) {
            if (!isTakingInput) {
                didGiveOutput |= copyOutputUntilStop();
                if (compressedTaken < blocks.compressedLength) {
                    break;
                }
                if (mode == Mode.FINISHING && inputExpected == 0 && isRunEmpty()) {
                    break;
                }
                prepareNewBlock();
                isTakingInput = true;
                if (mode == Mode.FLUSHING && inputExpected == 0 && isRunEmpty()) {
                    break;
                }
            }
            if (isTakingInput) {
                didTakeInput |= copyInputUntilStop();
                if (mode != Mode.RUNNING && inputExpected == 0) {
                    flushPendingRun();
                    blocks.compressBlock(mode == Mode.FINISHING);
                    isTakingInput = false;
                } else if (blocks.blockLength >= blocks.blockLimit) {
                    blocks.compressBlock(false);
                    isTakingInput = false;
                } else if (io.availableIn == 0) {
                    break;
                }
            }
        }
        return didTakeInput || didGiveOutput;
    }

    private boolean isAllGivenOut() {
        return inputExpected <= 0 && isRunEmpty() && compressedTaken >= blocks.compressedLength;
    }

    public int compress(Bzip2Buffers buffers, int action) {
        io = buffers;
        if (mode == Mode.IDLE) {
            return Bzip2Codes.SEQUENCE_ERROR;
        }
        if (mode == Mode.RUNNING) {
            if (action == Bzip2Codes.RUN) {
                return handleCompress() ? Bzip2Codes.RUN_OK : Bzip2Codes.PARAM_ERROR;
            }
            inputExpected = io.availableIn;
            mode = action == Bzip2Codes.FLUSH ? Mode.FLUSHING : Mode.FINISHING;
        }
        if (mode == Mode.FLUSHING) {
            return continueFlushing(action);
        }
        return continueFinishing(action);
    }

    private int continueFlushing(int action) {
        if (action != Bzip2Codes.FLUSH || inputExpected != io.availableIn) {
            return Bzip2Codes.SEQUENCE_ERROR;
        }
        handleCompress();
        if (!isAllGivenOut()) {
            return Bzip2Codes.FLUSH_OK;
        }
        mode = Mode.RUNNING;
        return Bzip2Codes.RUN_OK;
    }

    private int continueFinishing(int action) {
        if (action != Bzip2Codes.FINISH || inputExpected != io.availableIn) {
            return Bzip2Codes.SEQUENCE_ERROR;
        }
        if (!handleCompress()) {
            return Bzip2Codes.SEQUENCE_ERROR;
        }
        if (!isAllGivenOut()) {
            return Bzip2Codes.FINISH_OK;
        }
        mode = Mode.IDLE;
        return Bzip2Codes.STREAM_END;
    }
}
