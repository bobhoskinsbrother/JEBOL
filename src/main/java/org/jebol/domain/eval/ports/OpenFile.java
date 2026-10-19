package org.jebol.domain.eval.ports;

import org.jebol.domain.host.FilePort;
import org.jebol.domain.eval.GrantedServices;
import org.jebol.domain.host.HostService;
import org.jebol.domain.value.EvaluationFailure;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.PortValue;
import org.jebol.domain.value.Raised;
import org.jebol.domain.value.FileValue;
import org.jebol.domain.value.Value;

import java.util.Arrays;
import java.util.Optional;
import java.util.function.Supplier;

public final class OpenFile {

    private final PortValue port;
    private final SeekableFilePort seekable;
    private final FilePort files;
    private final GrantedServices granted;

    public OpenFile(PortValue port, FilePort files, GrantedServices granted) {
        this.port = port;
        this.seekable = new SeekableFilePort(port);
        this.files = files;
        this.granted = granted;
    }

    public long position() {
        refuseAClosedPosition();
        return seekable.position();
    }

    public Value moveTo(long wanted) {
        refuseAClosedPosition();
        granted.require(HostService.FILES);
        seekable.moveTo(Math.max(0, Math.min(wanted, measureTheWholeSize())));
        return port;
    }

    public Value moveBy(long steps) {
        return moveTo(seekable.position() + steps);
    }

    public Value movedToTheEnd() {
        return moveTo(measureTheWholeSize());
    }

    public boolean atItsEnd() {
        refuseAClosedPosition();
        granted.require(HostService.FILES);
        return answerAcrossTheHost(() -> seekable.atTail(files));
    }

    public Value measureTheLengthLeft() {
        refuseAClosedPosition();
        granted.require(HostService.FILES);
        return answerAcrossTheHost(() -> seekable.measureTheLengthLeft(files));
    }

    public Value write(Value data, Optional<Value> seek, Optional<Value> part,
                       boolean appending) {
        granted.require(HostService.FILES);
        if (port.isOpen()) {
            refuseWritingWhenOpenedOnlyToRead(EvaluationFailure.READ_ONLY);
        }
        if (appending) {
            seekable.moveTo(wholeSizeEvenWhenClosed());
        }
        seek.filter(IntegerValue.class::isInstance)
                .ifPresent(at -> seekable.moveTo(((IntegerValue) at).magnitude()));
        byte[] octets = data.asOctets();
        byte[] kept = part.filter(IntegerValue.class::isInstance)
                .map(limit -> Arrays.copyOf(octets, (int) Math.max(0,
                        Math.min(((IntegerValue) limit).magnitude(), octets.length))))
                .orElse(octets);
        return answerAcrossTheHost(() -> {
            seekable.writeAt(files, kept);
            return FileValue.of(seekable.path());
        });
    }

    public Value append(Value data) {
        return write(data, Optional.empty(), Optional.empty(), true);
    }

    public Value truncateAtThePosition() {
        refuseAClosedPosition();
        refuseWritingWhenOpenedOnlyToRead(EvaluationFailure.WRITE_ERROR);
        granted.require(HostService.FILES);
        return answerAcrossTheHost(() -> {
            String path = seekable.path();
            byte[] whole = files.readBytes(path);
            long keeping = Math.min(seekable.position(), whole.length);
            files.write(path, Arrays.copyOf(whole, (int) keeping));
            return port;
        });
    }

    private void refuseWritingWhenOpenedOnlyToRead(EvaluationFailure failure) {
        if (!seekable.mayWriteThrough()) {
            throw Raised.of(failure, FileValue.of(seekable.path()));
        }
    }

    private long wholeSizeEvenWhenClosed() {
        return answerAcrossTheHost(() -> seekable.measureTheWholeSize(files))
                instanceof IntegerValue(long magnitude) ? magnitude : 0;
    }

    private long measureTheWholeSize() {
        granted.require(HostService.FILES);
        return ((IntegerValue) answerAcrossTheHost(() -> seekable.measureTheWholeSize(files))).magnitude();
    }

    private void refuseAClosedPosition() {
        if (!port.isOpen()) {
            throw Raised.of(EvaluationFailure.NOT_OPEN, seekable.path());
        }
    }

    private <T> T answerAcrossTheHost(Supplier<T> crossing) {
        try {
            return crossing.get();
        } catch (FilePort.Denied denied) {
            throw denied.raise();
        }
    }
}
