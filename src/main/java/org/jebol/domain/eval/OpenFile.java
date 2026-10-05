package org.jebol.domain.eval;

import org.jebol.domain.host.HostService;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.EvaluationFailure;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.PortValue;
import org.jebol.domain.value.Raised;
import org.jebol.domain.value.StringValue;
import org.jebol.domain.value.Value;

import java.util.Arrays;
import java.util.Optional;
import java.util.function.Supplier;

public final class OpenFile {

    private final PortValue port;
    private final FilePort files;
    private final GrantedServices granted;

    public OpenFile(PortValue port, FilePort files, GrantedServices granted) {
        this.port = port;
        this.files = files;
        this.granted = granted;
    }

    public long position() {
        refuseAClosedPosition();
        return SeekableFilePort.positionOf(port);
    }

    public Value movedTo(long wanted) {
        refuseAClosedPosition();
        granted.require(HostService.FILES);
        SeekableFilePort.moveTo(port, Math.max(0, Math.min(wanted, wholeSize())));
        return port;
    }

    public Value movedBy(long steps) {
        return movedTo(SeekableFilePort.positionOf(port) + steps);
    }

    public Value movedToTheEnd() {
        return movedTo(wholeSize());
    }

    public boolean atItsEnd() {
        refuseAClosedPosition();
        granted.require(HostService.FILES);
        return answered(() -> SeekableFilePort.atTail(files, port));
    }

    public Value lengthLeft() {
        refuseAClosedPosition();
        granted.require(HostService.FILES);
        return answered(() -> SeekableFilePort.lengthLeft(files, port));
    }

    public Value written(Value data, Optional<Value> seek, Optional<Value> part,
            boolean appending) {
        granted.require(HostService.FILES);
        if (port.isOpen()) {
            refuseWritingWhenOpenedOnlyToRead(EvaluationFailure.READ_ONLY);
        }
        if (appending) {
            SeekableFilePort.moveTo(port, wholeSizeEvenWhenClosed());
        }
        seek.filter(IntegerValue.class::isInstance)
                .ifPresent(at -> SeekableFilePort.moveTo(port, ((IntegerValue) at).magnitude()));
        byte[] octets = data.asOctets();
        byte[] kept = part.filter(IntegerValue.class::isInstance)
                .map(limit -> Arrays.copyOf(octets, (int) Math.max(0,
                        Math.min(((IntegerValue) limit).magnitude(), octets.length))))
                .orElse(octets);
        return answered(() -> {
            SeekableFilePort.writeAt(files, port, kept);
            return StringValue.of(SeekableFilePort.pathOf(port), Datatype.FILE);
        });
    }

    public Value appended(Value data) {
        return written(data, Optional.empty(), Optional.empty(), true);
    }

    public Value truncatedAtThePosition() {
        refuseAClosedPosition();
        refuseWritingWhenOpenedOnlyToRead(EvaluationFailure.WRITE_ERROR);
        granted.require(HostService.FILES);
        return answered(() -> {
            String path = SeekableFilePort.pathOf(port);
            byte[] whole = files.readBytes(path);
            long keeping = Math.min(SeekableFilePort.positionOf(port), whole.length);
            files.write(path, Arrays.copyOf(whole, (int) keeping));
            return port;
        });
    }

    private void refuseWritingWhenOpenedOnlyToRead(EvaluationFailure failure) {
        if (!SeekableFilePort.mayWriteThrough(port)) {
            throw Raised.of(failure,
                    StringValue.of(SeekableFilePort.pathOf(port), Datatype.FILE));
        }
    }

    private long wholeSizeEvenWhenClosed() {
        return answered(() -> SeekableFilePort.wholeSize(files, port))
                instanceof IntegerValue(long magnitude) ? magnitude : 0;
    }

    private long wholeSize() {
        granted.require(HostService.FILES);
        return ((IntegerValue) answered(() -> SeekableFilePort.wholeSize(files, port)))
                .magnitude();
    }

    private void refuseAClosedPosition() {
        if (!port.isOpen()) {
            throw Raised.of(EvaluationFailure.NOT_OPEN, SeekableFilePort.pathOf(port));
        }
    }

    private <T> T answered(Supplier<T> crossing) {
        try {
            return crossing.get();
        } catch (FilePort.Denied denied) {
            throw denied.raised();
        }
    }
}
