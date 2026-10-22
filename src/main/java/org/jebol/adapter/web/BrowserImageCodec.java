package org.jebol.adapter.web;

import org.jebol.domain.host.ImagePort;
import org.jebol.domain.value.ErrorCategory;
import org.jebol.domain.value.ErrorValue;
import org.jebol.domain.value.Raised;
import org.jebol.domain.value.StringValue;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class BrowserImageCodec implements ImagePort {

    private static final Set<String> FORMATS = Set.of("png", "jpeg", "jpg", "gif", "bmp", "dds", "tiff", "webp");

    private static final Map<String, String> CANVAS_MEDIA_TYPES = Map.of(
            "", "image/png",
            "png", "image/png",
            "jpeg", "image/jpeg",
            "jpg", "image/jpeg",
            "webp", "image/webp");

    private static final String UNREADABLE_FORMAT = "dds";

    private static final String SAFARI_ONLY_FORMAT = "tiff";

    private static final int ONLY_DECODED_FRAME = 1;

    private final BrowserPictures browser;

    public BrowserImageCodec(BrowserPictures browser) {
        this.browser = browser;
    }

    @Override
    public boolean hasImageType(String type) {
        return FORMATS.contains(type.toLowerCase(Locale.ROOT));
    }

    @Override
    public Pixels decode(byte[] encoded, String type, int frame) {
        String format = type.toLowerCase(Locale.ROOT);
        if (format.equals(UNREADABLE_FORMAT)) {
            throw refuse(format + ", which a browser cannot read");
        }
        if (frame != ONLY_DECODED_FRAME) {
            return null;
        }
        return browser.decode(encoded).orElseGet(() -> {
            if (format.equals(SAFARI_ONLY_FORMAT)) {
                throw refuse(format + ", which this browser cannot read");
            }
            return null;
        });
    }

    @Override
    public byte[] encode(Pixels image, String type) {
        String format = type.toLowerCase(Locale.ROOT);
        String mediaType = Optional.ofNullable(CANVAS_MEDIA_TYPES.get(format))
                .orElseThrow(() -> refuse(format + ", which a browser cannot write"));
        return browser.encode(image, mediaType)
                .orElseThrow(() -> refuse(format + ", which this browser cannot write"));
    }

    private Raised refuse(String why) {
        return new Raised(ErrorValue.of(ErrorCategory.ACCESS, "no-codec",
                "cannot decode or encode (no codec): " + why, StringValue.of(why)));
    }
}
