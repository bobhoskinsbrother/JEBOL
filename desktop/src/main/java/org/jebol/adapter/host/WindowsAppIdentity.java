package org.jebol.adapter.host;

import java.io.File;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

@SuppressWarnings("restricted")
public final class WindowsAppIdentity {

    private static final char WINDOWS_SEPARATOR = '\\';
    private static final int SUCCEEDED = 0;
    private static final int COINIT_APARTMENTTHREADED = 2;
    private static final short VT_LPWSTR = 31;
    private static final long PROPVARIANT_BYTES = 24;
    private static final long PROPVARIANT_VALUE_OFFSET = 8;
    private static final int APP_USER_MODEL_ID_PROPERTY = 5;
    private static final UUID APP_USER_MODEL_FORMAT = UUID.fromString("9F4C2855-9F79-4B39-A8D0-E1D42DE1D5F3");
    private static final UUID PROPERTY_STORE_INTERFACE = UUID.fromString("886D8EEB-8CF2-4446-8D02-CDBA1DBDCF99");
    private static final int RELEASE = 2;
    private static final int SET_VALUE = 6;
    private static final int COMMIT = 7;
    private static final int VTABLE_ENTRIES = 8;
    private static final String AWT_FRAME_CLASS = "SunAwtFrame";

    private WindowsAppIdentity() {
    }

    private static boolean isWindows() {
        return File.separatorChar == WINDOWS_SEPARATOR;
    }

    public static boolean declareForProcess(String identity) {
        if (!isWindows()) {
            return false;
        }
        try (Arena arena = Arena.ofConfined()) {
            MethodHandle declare = function(arena, "shell32", "SetCurrentProcessExplicitAppUserModelID",
                    FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
            return (int) declare.invokeExact(wide(arena, identity)) == SUCCEEDED;
        } catch (Throwable refused) {
            return false;
        }
    }

    public static Optional<String> processIdentity() {
        if (!isWindows()) {
            return Optional.empty();
        }
        try (Arena arena = Arena.ofConfined()) {
            MethodHandle read = function(arena, "shell32", "GetCurrentProcessExplicitAppUserModelID",
                    FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
            MethodHandle free = function(arena, "ole32", "CoTaskMemFree",
                    FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
            MemorySegment answer = arena.allocate(ValueLayout.ADDRESS);
            if ((int) read.invokeExact(answer) != SUCCEEDED) {
                return Optional.empty();
            }
            MemorySegment text = answer.get(ValueLayout.ADDRESS, 0);
            String identity = text.reinterpret(Long.MAX_VALUE).getString(0, StandardCharsets.UTF_16LE);
            free.invokeExact(text);
            return Optional.of(identity);
        } catch (Throwable unreadable) {
            return Optional.empty();
        }
    }

    public static boolean declareForWindow(String title, String identity) {
        if (!isWindows()) {
            return false;
        }
        try (Arena arena = Arena.ofConfined()) {
            Optional<MemorySegment> window = findOwnWindow(arena, title);
            return window.isPresent() && setWindowIdentity(arena, window.get(), identity);
        } catch (Throwable refused) {
            return false;
        }
    }

    private static Optional<MemorySegment> findOwnWindow(Arena arena, String title) throws Throwable {
        MethodHandle findNext = function(arena, "user32", "FindWindowExW", FunctionDescriptor.of(ValueLayout.ADDRESS,
                ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        MethodHandle ownerOf = function(arena, "user32", "GetWindowThreadProcessId",
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        MemorySegment frameClass = wide(arena, AWT_FRAME_CLASS);
        MemorySegment wanted = wide(arena, title);
        MemorySegment owner = arena.allocate(ValueLayout.JAVA_INT);
        long thisProcess = ProcessHandle.current().pid();
        MemorySegment found = MemorySegment.NULL;
        while (true) {
            found = (MemorySegment) findNext.invokeExact(MemorySegment.NULL, found, frameClass, wanted);
            if (found.equals(MemorySegment.NULL)) {
                return Optional.empty();
            }
            int ignoredThread = (int) ownerOf.invokeExact(found, owner);
            if (Integer.toUnsignedLong(owner.get(ValueLayout.JAVA_INT, 0)) == thisProcess) {
                return Optional.of(found);
            }
        }
    }

    private static boolean setWindowIdentity(Arena arena, MemorySegment window, String identity) throws Throwable {
        MethodHandle startCom = function(arena, "ole32", "CoInitializeEx",
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        MethodHandle storeFor = function(arena, "shell32", "SHGetPropertyStoreForWindow",
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        int ignoredStarted = (int) startCom.invokeExact(MemorySegment.NULL, COINIT_APARTMENTTHREADED);
        MemorySegment answer = arena.allocate(ValueLayout.ADDRESS);
        if ((int) storeFor.invokeExact(window, guid(arena, PROPERTY_STORE_INTERFACE), answer) != SUCCEEDED) {
            return false;
        }
        MemorySegment store = answer.get(ValueLayout.ADDRESS, 0);
        MemorySegment vtable = store.reinterpret(ValueLayout.ADDRESS.byteSize()).get(ValueLayout.ADDRESS, 0)
                .reinterpret(ValueLayout.ADDRESS.byteSize() * VTABLE_ENTRIES);
        try {
            MethodHandle setValue = method(vtable, SET_VALUE, FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
            MethodHandle commit = method(vtable, COMMIT, FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
            MemorySegment value = arena.allocate(PROPVARIANT_BYTES, ValueLayout.ADDRESS.byteAlignment());
            value.set(ValueLayout.JAVA_SHORT, 0, VT_LPWSTR);
            value.set(ValueLayout.ADDRESS, PROPVARIANT_VALUE_OFFSET, wide(arena, identity));
            return (int) setValue.invokeExact(store, appUserModelKey(arena), value) == SUCCEEDED
                    && (int) commit.invokeExact(store) == SUCCEEDED;
        } finally {
            MethodHandle release = method(vtable, RELEASE, FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
            int ignoredCount = (int) release.invokeExact(store);
        }
    }

    private static MemorySegment appUserModelKey(Arena arena) {
        MemorySegment key = arena.allocate(20, 4);
        key.copyFrom(guid(arena, APP_USER_MODEL_FORMAT));
        key.set(ValueLayout.JAVA_INT, 16, APP_USER_MODEL_ID_PROPERTY);
        return key;
    }

    private static MemorySegment guid(Arena arena, UUID uuid) {
        ByteBuffer bytes = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN);
        long high = uuid.getMostSignificantBits();
        bytes.putInt((int) (high >>> 32));
        bytes.putShort((short) (high >>> 16));
        bytes.putShort((short) high);
        bytes.order(ByteOrder.BIG_ENDIAN).putLong(uuid.getLeastSignificantBits());
        MemorySegment guid = arena.allocate(16, 4);
        guid.copyFrom(MemorySegment.ofArray(bytes.array()));
        return guid;
    }

    private static MemorySegment wide(Arena arena, String text) {
        return arena.allocateFrom(text, StandardCharsets.UTF_16LE);
    }

    @SuppressWarnings("restricted")
    private static MethodHandle function(Arena arena, String library, String name, FunctionDescriptor shape) {
        SymbolLookup found = SymbolLookup.libraryLookup(library, arena);
        return Linker.nativeLinker().downcallHandle(found.find(name).orElseThrow(), shape);
    }

    @SuppressWarnings("restricted")
    private static MethodHandle method(MemorySegment vtable, int index, FunctionDescriptor shape) {
        return Linker.nativeLinker().downcallHandle(vtable.getAtIndex(ValueLayout.ADDRESS, index), shape);
    }
}
