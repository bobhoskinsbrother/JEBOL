package org.jebol.adapter.cli;

import java.io.File;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;

final class WindowsCommandLine {

    private static final char WINDOWS_SEPARATOR = '\\';

    private WindowsCommandLine() {
    }

    static String[] asTheShellWroteThem(String[] decoded) {
        if (File.separatorChar != WINDOWS_SEPARATOR || decoded.length == 0) {
            return decoded;
        }
        try {
            String[] whole = readWideCommandLine();
            if (whole.length < decoded.length) {
                return decoded;
            }
            String[] trailing = new String[decoded.length];
            System.arraycopy(whole, whole.length - decoded.length, trailing, 0, decoded.length);
            return trailing;
        } catch (Throwable unreadable) {
            return decoded;
        }
    }

    @SuppressWarnings("restricted")
    private static String[] readWideCommandLine() throws Throwable {
        Linker linker = Linker.nativeLinker();
        try (Arena arena = Arena.ofConfined()) {
            SymbolLookup kernel = SymbolLookup.libraryLookup("kernel32", arena);
            SymbolLookup shell = SymbolLookup.libraryLookup("shell32", arena);
            MethodHandle getCommandLine = linker.downcallHandle(
                    kernel.find("GetCommandLineW").orElseThrow(),
                    FunctionDescriptor.of(ValueLayout.ADDRESS));
            MethodHandle splitCommandLine = linker.downcallHandle(
                    shell.find("CommandLineToArgvW").orElseThrow(),
                    FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
            MethodHandle freeLocal = linker.downcallHandle(
                    kernel.find("LocalFree").orElseThrow(),
                    FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
            MemorySegment line = (MemorySegment) getCommandLine.invokeExact();
            MemorySegment count = arena.allocate(ValueLayout.JAVA_INT);
            MemorySegment split = (MemorySegment) splitCommandLine.invokeExact(line, count);
            if (split.equals(MemorySegment.NULL)) {
                return new String[0];
            }
            int counted = count.get(ValueLayout.JAVA_INT, 0);
            MemorySegment pointers = split.reinterpret(ValueLayout.ADDRESS.byteSize() * counted);
            String[] arguments = new String[counted];
            for (int index = 0; index < counted; index++) {
                arguments[index] = pointers.getAtIndex(ValueLayout.ADDRESS, index)
                        .reinterpret(Long.MAX_VALUE)
                        .getString(0, StandardCharsets.UTF_16LE);
            }
            MemorySegment ignored = (MemorySegment) freeLocal.invokeExact(split);
            return arguments;
        }
    }
}
