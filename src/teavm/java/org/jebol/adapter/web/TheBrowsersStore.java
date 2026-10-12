package org.jebol.adapter.web;

import org.jebol.domain.host.FilePort;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSObject;
import org.teavm.jso.JSProperty;
import org.teavm.jso.core.JSArrayReader;
import org.teavm.jso.core.JSPromise;
import org.teavm.jso.typedarrays.Int8Array;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

final class TheBrowsersStore implements WebFiles.Store {

    static WebFiles.Store forThePage() {
        return theStoreIsHere() ? new TheBrowsersStore() : WebFiles.Store.memory();
    }

    @Override
    public WebFiles.Store.Tree everything() {
        WhatIsStored stored = jebolStoreEverything().await();
        if (stored.getFailed() != null) {
            return WebFiles.Store.Tree.empty();
        }
        Map<String, byte[]> files = new LinkedHashMap<>();
        Set<String> directories = new TreeSet<>();
        JSArrayReader<StoredEntry> entries = stored.getEntries();
        for (int at = 0; at < entries.getLength(); at++) {
            StoredEntry entry = entries.get(at);
            if (entry.isDirectory()) {
                directories.add(withoutTheTrailingSlash(entry.getPath()));
            } else {
                files.put(entry.getPath(), asBytes(entry.getOctets()));
            }
        }
        return new WebFiles.Store.Tree(files, directories);
    }

    @Override
    public void wrote(String path, byte[] contents) {
        requireItWorked(jebolStoreWrite(path, asATypedArray(contents)).await());
    }

    @Override
    public void removed(String path) {
        requireItWorked(jebolStoreRemove(path).await());
    }

    @Override
    public void madeADirectory(String path) {
        requireItWorked(jebolStoreMakeDirectory(path).await());
    }

    private static void requireItWorked(WhatHappened happened) {
        if (happened.getFailed() != null) {
            throw new FilePort.Denied("cannot-open", happened.getFailed());
        }
    }

    private static String withoutTheTrailingSlash(String path) {
        return path.length() > 1 && path.endsWith("/")
                ? path.substring(0, path.length() - 1)
                : path;
    }

    private static Int8Array asATypedArray(byte[] octets) {
        Int8Array array = new Int8Array(octets.length);
        for (int at = 0; at < octets.length; at++) {
            array.set(at, octets[at]);
        }
        return array;
    }

    private static byte[] asBytes(Int8Array octets) {
        byte[] bytes = new byte[octets.getLength()];
        for (int at = 0; at < bytes.length; at++) {
            bytes[at] = octets.get(at);
        }
        return bytes;
    }

    interface StoredEntry extends JSObject {

        @JSProperty
        String getPath();

        @JSProperty
        boolean isDirectory();

        @JSProperty
        Int8Array getOctets();
    }

    interface WhatIsStored extends JSObject {

        @JSProperty
        String getFailed();

        @JSProperty
        JSArrayReader<StoredEntry> getEntries();
    }

    interface WhatHappened extends JSObject {

        @JSProperty
        String getFailed();
    }

    @JSBody(script = "return !!(self.navigator && navigator.storage && navigator.storage.getDirectory);")
    private static native boolean theStoreIsHere();

    @JSBody(script = "return jebolStoreEverything();")
    private static native JSPromise<WhatIsStored> jebolStoreEverything();

    @JSBody(params = {"path", "octets"}, script = "return jebolStoreWrite(path, octets);")
    private static native JSPromise<WhatHappened> jebolStoreWrite(String path, Int8Array octets);

    @JSBody(params = "path", script = "return jebolStoreRemove(path);")
    private static native JSPromise<WhatHappened> jebolStoreRemove(String path);

    @JSBody(params = "path", script = "return jebolStoreMakeDirectory(path);")
    private static native JSPromise<WhatHappened> jebolStoreMakeDirectory(String path);
}
