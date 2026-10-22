package org.jebol.adapter.web;

import org.jebol.domain.host.ImagePort;

import java.util.Optional;

public interface BrowserPictures {

    Optional<ImagePort.Pixels> decode(byte[] encoded);

    Optional<byte[]> encode(ImagePort.Pixels image, String mediaType);
}
