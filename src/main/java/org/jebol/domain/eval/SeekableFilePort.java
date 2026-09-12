package org.jebol.domain.eval;

import org.jebol.domain.value.BinaryValue;
import org.jebol.domain.value.BinaryStorage;
import org.jebol.domain.value.BlockValue;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.LogicValue;
import org.jebol.domain.value.ObjectValue;
import org.jebol.domain.value.PortValue;
import org.jebol.domain.value.StringValue;
import org.jebol.domain.value.Value;

import java.util.ArrayList;
import java.util.List;

final class SeekableFilePort {

    private SeekableFilePort() {
    }

    private static final String THE_POSITION_AND_WHETHER_IT_MAY_WRITE = "state";

    private static final String[] WHERE_A_FILE_KEEPS_ITS_PATH_BEFORE_WHERE_A_URL_DOES =
            {"path", "ref"};

    private static final int THE_POSITION = 0;

    private static final int WHETHER_IT_MAY_WRITE = 1;

    static long positionOf(PortValue port) {
        return switch (port.fieldNamed(THE_POSITION_AND_WHETHER_IT_MAY_WRITE)) {
            case IntegerValue at -> at.magnitude();
            case BlockValue kept
                    when kept.remaining().get(THE_POSITION) instanceof IntegerValue at ->
                    at.magnitude();
            default -> 0;
        };
    }

    static void moveTo(PortValue port, long position) {
        rememberOnThePort(port, Math.max(0, position), mayWriteThrough(port));
    }

    static boolean mayWriteThrough(PortValue port) {
        return !(port.fieldNamed(THE_POSITION_AND_WHETHER_IT_MAY_WRITE)
                        instanceof BlockValue kept)
                || kept.remaining().get(WHETHER_IT_MAY_WRITE).isTruthy();
    }

    static void openedAt(PortValue port, long position, boolean mayWrite) {
        rememberOnThePort(port, position, mayWrite);
    }

    private static void rememberOnThePort(
            PortValue port, long position, boolean mayWrite) {

        port.setField(THE_POSITION_AND_WHETHER_IT_MAY_WRITE, BlockValue.block(
                List.of(IntegerValue.of(position), LogicValue.of(mayWrite))));
    }

    static String pathOf(PortValue port) {
        if (!(port.fieldNamed("spec") instanceof ObjectValue fields)) {
            return "";
        }
        for (String field : WHERE_A_FILE_KEEPS_ITS_PATH_BEFORE_WHERE_A_URL_DOES) {
            if (fields.context().holds(field)
                    && fields.context().ownSlotFor(field).value()
                            instanceof StringValue named) {
                return named.text();
            }
        }
        return "";
    }

    static long sizeOf(FilePort files, String path) {
        return files.informationAbout(path)
                .flatMap(FileInformation::size)
                .orElse(0L);
    }

    static Value readFrom(FilePort files, PortValue port, Long howMany) {
        String path = pathOf(port);
        long size = sizeOf(files, path);
        long at = Math.min(positionOf(port), size);
        long wanted = howMany == null ? size - at : howMany;
        if (wanted < 0) {
            wanted = Math.max(0, Math.min(-wanted, at));
            at -= wanted;
        }
        long taken = Math.max(0, Math.min(wanted, size - at));
        byte[] whole = files.readBytes(path);
        int[] part = new int[(int) taken];
        for (int step = 0; step < taken; step++) {
            part[step] = whole[(int) at + step] & 0xFF;
        }
        moveTo(port, at + taken);
        return new BinaryValue(BinaryStorage.of(part), 1);
    }

    static void writeAt(FilePort files, PortValue port, byte[] contents) {
        String path = pathOf(port);
        long at = positionOf(port);
        files.writeAt(path, at, contents);
        moveTo(port, at + contents.length);
    }

    static Value lengthLeft(FilePort files, PortValue port) {
        String path = pathOf(port);
        if (namesARunOfNamesRatherThanBytes(port)) {
            return IntegerValue.of(files.namesIn(path).size());
        }
        return IntegerValue.of(Math.max(0, sizeOf(files, path) - positionOf(port)));
    }

    static Value wholeSize(FilePort files, PortValue port) {
        return IntegerValue.of(sizeOf(files, pathOf(port)));
    }

    static boolean atTail(FilePort files, PortValue port) {
        String path = pathOf(port);
        if (namesARunOfNamesRatherThanBytes(port)) {
            return files.namesIn(path).isEmpty();
        }
        return positionOf(port) >= sizeOf(files, path);
    }

    private static boolean namesARunOfNamesRatherThanBytes(PortValue port) {
        return port.schemeName().equals("dir");
    }

    static Value namesIn(FilePort files, String path) {
        List<Value> names = new ArrayList<>();
        for (String name : files.namesIn(path)) {
            names.add(StringValue.of(name, Datatype.FILE));
        }
        return BlockValue.block(names);
    }
}
