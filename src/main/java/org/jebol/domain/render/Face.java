package org.jebol.domain.render;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * One thing on a page, gathered from a layout dialect.
 *
 * <p>A face in REBOL/View is an object with a size, an offset, a colour and
 * some text. Here it is the same idea aimed at markup rather than at a
 * window: a kind, a caption, and whatever styling the dialect attached to it.
 *
 * <p>Deliberately not a REBOL value. The dialect is read into these and these
 * are turned into markup, so neither half has to know about the other.
 */
public final class Face {

    private final String kind;
    private final Map<String, String> styles = new LinkedHashMap<>();

    private String caption = "";
    private Object action;

    Face(String kind) {
        this.kind = kind;
    }

    public String kind() {
        return kind;
    }

    public String caption() {
        return caption;
    }

    void setCaption(String text) {
        this.caption = text;
    }

    void style(String property, String value) {
        styles.put(property, value);
    }

    /**
     * The block that runs when this face is acted on, if it has one.
     *
     * <p>Held as an opaque object so this stays a description of something on
     * a page rather than becoming a REBOL value in disguise.
     */
    public java.util.Optional<Object> action() {
        return java.util.Optional.ofNullable(action);
    }

    void setAction(Object block) {
        this.action = block;
    }

    public Map<String, String> styles() {
        return Map.copyOf(styles);
    }

    /** The styles as a CSS declaration list, or empty if there are none. */
    public Optional<String> styleAttribute() {
        if (styles.isEmpty()) {
            return Optional.empty();
        }
        StringBuilder rendered = new StringBuilder();
        styles.forEach((property, value) -> {
            if (!rendered.isEmpty()) {
                rendered.append(';');
            }
            rendered.append(property).append(':').append(value);
        });
        return Optional.of(rendered.toString());
    }

    @Override
    public String toString() {
        return kind + "(" + caption + ")";
    }
}
