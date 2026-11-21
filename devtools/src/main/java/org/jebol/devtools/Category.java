package org.jebol.devtools;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

public enum Category {
    UTILITY("Utility", "public.app-category.utilities"),
    PRODUCTIVITY("Office", "public.app-category.productivity"),
    GAME("Game", "public.app-category.games"),
    EDUCATION("Education", "public.app-category.education"),
    GRAPHICS("Graphics", "public.app-category.graphics-design"),
    AUDIO("Audio", "public.app-category.music"),
    VIDEO("Video", "public.app-category.video"),
    DEVELOPER("Development", "public.app-category.developer-tools"),
    NETWORK("Network", "public.app-category.social-networking");

    private final String freedesktopCategory;
    private final String macCategory;

    Category(String freedesktopCategory, String macCategory) {
        this.freedesktopCategory = freedesktopCategory;
        this.macCategory = macCategory;
    }

    public String word() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String freedesktopCategory() {
        return freedesktopCategory;
    }

    public String macCategory() {
        return macCategory;
    }

    public static Optional<Category> named(String word) {
        return Arrays.stream(values()).filter(category -> category.word().equals(word)).findFirst();
    }

    public static String listed() {
        return Arrays.stream(values()).map(Category::word).collect(Collectors.joining(", "));
    }
}
