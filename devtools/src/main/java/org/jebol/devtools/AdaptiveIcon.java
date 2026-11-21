package org.jebol.devtools;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class AdaptiveIcon {

    public static final Map<String, Integer> DENSITIES = densities();

    private static final int LAYER_DP = 108;
    private static final int SAFE_ZONE_DP = 66;

    private AdaptiveIcon() {
    }

    private static Map<String, Integer> densities() {
        Map<String, Integer> densities = new LinkedHashMap<>();
        densities.put("mdpi", 108);
        densities.put("hdpi", 162);
        densities.put("xhdpi", 216);
        densities.put("xxhdpi", 324);
        densities.put("xxxhdpi", 432);
        return Collections.unmodifiableMap(densities);
    }

    public static BufferedImage foreground(BufferedImage icon, Optional<BufferedImage> ownForeground, int size) {
        if (ownForeground.isPresent()) {
            return IconSizes.scaled(ownForeground.get(), size);
        }
        int inner = size * SAFE_ZONE_DP / LAYER_DP;
        BufferedImage shrunk = IconSizes.scaled(icon, inner);
        BufferedImage layer = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D painting = layer.createGraphics();
        int margin = (size - inner) / 2;
        painting.drawImage(shrunk, margin, margin, null);
        painting.dispose();
        return layer;
    }
}
