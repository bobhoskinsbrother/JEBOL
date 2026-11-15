package org.jebol.adapter.web;

import org.jebol.domain.host.DigestPort;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSObject;
import org.teavm.jso.JSProperty;
import org.teavm.jso.core.JSPromise;
import org.teavm.jso.typedarrays.Int8Array;

import java.util.Arrays;
import java.util.Set;

public final class BrowserDigests implements DigestPort {

    private static final Set<String> METHODS =
            Set.of("sha1", "sha256", "sha384", "sha512");

    @Override
    public Set<String> methods() {
        return METHODS;
    }

    @Override
    public RunningDigest start(String method) {
        require(method);
        return new RunningDigest() {

            private byte[] kept = new byte[0];

            @Override
            public void add(byte[] octets, int from, int length) {
                byte[] grown = Arrays.copyOf(kept, kept.length + length);
                System.arraycopy(octets, from, grown, kept.length, length);
                kept = grown;
            }

            @Override
            public byte[] soFar() {
                return digestOf(method, kept);
            }
        };
    }

    @Override
    public byte[] digestOf(String method, byte[] octets) {
        require(method);
        WhatCameBack answer = jebolDigest(method, asATypedArray(octets)).await();
        return readTheBytesOf(answer);
    }

    @Override
    public byte[] digestWithKey(String method, byte[] octets, byte[] key) {
        require(method);
        WhatCameBack answer =
                jebolKeyedDigest(method, asATypedArray(key), asATypedArray(octets)).await();
        return readTheBytesOf(answer);
    }

    private static byte[] readTheBytesOf(WhatCameBack answer) {
        if (answer.getFailed() != null) {
            throw new Refused("feature-na", answer.getFailed());
        }
        return asBytes(answer.getOctets());
    }

    private static void require(String method) {
        if (!METHODS.contains(method)) {
            throw new Refused("feature-na", method + " is not a method this host has");
        }
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

    interface WhatCameBack extends JSObject {

        @JSProperty
        String getFailed();

        @JSProperty
        Int8Array getOctets();
    }

    @JSBody(params = {"method", "octets"}, script = "return jebolDigest(method, octets);")
    private static native JSPromise<WhatCameBack> jebolDigest(String method, Int8Array octets);

    @JSBody(params = {"method", "key", "octets"},
            script = "return jebolKeyedDigest(method, key, octets);")
    private static native JSPromise<WhatCameBack> jebolKeyedDigest(
            String method, Int8Array key, Int8Array octets);
}
