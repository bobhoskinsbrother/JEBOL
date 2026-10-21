package org.jebol.domain.eval.ports;

import org.jebol.domain.host.FileInformation;
import org.jebol.domain.host.FilePort;
import org.jebol.domain.value.BinaryStorage;
import org.jebol.domain.value.BinaryValue;
import org.jebol.domain.value.AnyBlockValue;
import org.jebol.domain.value.BlockValue;
import org.jebol.domain.value.EvaluationFailure;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.LogicValue;
import org.jebol.domain.value.ObjectValue;
import org.jebol.domain.value.PortValue;
import org.jebol.domain.value.Raised;
import org.jebol.domain.value.AnyStringValue;
import org.jebol.domain.value.FileValue;
import org.jebol.domain.value.UrlValue;
import org.jebol.domain.value.Value;

import java.util.List;
import java.util.Optional;

public final class SeekableFilePort {

    private static final String POSITION_AND_WHETHER_IT_MAY_WRITE = "state";

    private static final String WHAT_THE_PORT_WAS_MADE_FROM = "ref";

    private static final String WHERE_URL_LEAVES_ITS_PATH = "path";

    private static final int POSITION = 0;

    private static final int WHETHER_IT_MAY_WRITE = 1;

    private final PortValue port;

    public SeekableFilePort(PortValue port) {
        this.port = port;
    }

    public long position() {
        return switch (port.readFieldValue(POSITION_AND_WHETHER_IT_MAY_WRITE)) {
            case IntegerValue at -> at.magnitude();
            case AnyBlockValue kept
                    when kept.remaining().get(POSITION) instanceof IntegerValue(long magnitude) ->
                    magnitude;
            default -> 0;
        };
    }

    public void moveTo(long position) {
        rememberOnPort(Math.max(0, position), canWriteThrough());
    }

    public boolean canWriteThrough() {
        return !(port.readFieldValue(POSITION_AND_WHETHER_IT_MAY_WRITE) instanceof AnyBlockValue kept)
                || kept.remaining().get(WHETHER_IT_MAY_WRITE).isTruthy();
    }

    public void openAt(long position, boolean mayWrite) {
        rememberOnPort(position, mayWrite);
    }

    private void rememberOnPort(long position, boolean mayWrite) {
        port.setField(POSITION_AND_WHETHER_IT_MAY_WRITE, BlockValue.of(
                List.of(IntegerValue.of(position), LogicValue.of(mayWrite))));
    }

    public String path() {
        if (!(port.readFieldValue("spec") instanceof ObjectValue spec)) {
            return "";
        }
        if (!spec.context().holds(WHAT_THE_PORT_WAS_MADE_FROM)) {
            throw Raised.of(EvaluationFailure.INVALID_SPEC, spec);
        }
        Value made = spec.context().ownSlotFor(WHAT_THE_PORT_WAS_MADE_FROM).value();
        if (made instanceof UrlValue) {
            return spec.readFieldValue(WHERE_URL_LEAVES_ITS_PATH) instanceof AnyStringValue path ? path.text() : "";
        }
        if (!(made instanceof FileValue file)) {
            throw Raised.of(EvaluationFailure.INVALID_SPEC, made);
        }
        return file.text();
    }

    private long measureSizeIn(FilePort files) {
        return files.describe(path())
                .flatMap(FileInformation::size)
                .orElse(0L);
    }

    public Value readFrom(FilePort files, Optional<Long> howMany) {
        long size = measureSizeIn(files);
        long at = Math.min(position(), size);
        long wanted = howMany.orElse(size - at);
        if (wanted < 0) {
            wanted = Math.max(0, Math.min(-wanted, at));
            at -= wanted;
        }
        long taken = Math.max(0, Math.min(wanted, size - at));
        byte[] whole = files.readBytes(path());
        int[] part = new int[(int) taken];
        for (int step = 0; step < taken; step++) {
            part[step] = whole[(int) at + step] & 0xFF;
        }
        moveTo(at + taken);
        return new BinaryValue(BinaryStorage.of(part), 1);
    }

    public void writeAt(FilePort files, byte[] contents) {
        long at = position();
        files.writeAt(path(), at, contents);
        moveTo(at + contents.length);
    }

    public Value measureLengthLeft(FilePort files) {
        if (namesFiles()) {
            return IntegerValue.of(files.listFileNames(path()).size());
        }
        return IntegerValue.of(Math.max(0, measureSizeIn(files) - position()));
    }

    public Value measureWholeSize(FilePort files) {
        return IntegerValue.of(measureSizeIn(files));
    }

    public boolean atTail(FilePort files) {
        if (namesFiles()) {
            return files.listFileNames(path()).isEmpty();
        }
        return position() >= measureSizeIn(files);
    }

    private boolean namesFiles() {
        return port.schemeName().equals("dir");
    }
}
