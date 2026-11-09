package org.jebol.domain.host;

import java.util.List;

public interface SecureTransport {

    SecureSession startClient(String asciiHostName, int portNumber, ServerCheck check);

    SecureSession startServer(List<byte[]> certificateChain, RsaServerKey key);

    static SecureTransport none() {
        return new SecureTransport() {

            @Override
            public SecureSession startClient(String asciiHostName, int portNumber, ServerCheck check) {
                throw refuse(asciiHostName);
            }

            @Override
            public SecureSession startServer(List<byte[]> certificateChain, RsaServerKey key) {
                throw refuse("a listening server");
            }

            private NetworkPort.Refused refuse(String about) {
                return new NetworkPort.Refused("no-port",
                        "this interpreter was given no TLS of its own", about);
            }
        };
    }
}
