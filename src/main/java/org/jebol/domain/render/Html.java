package org.jebol.domain.render;

import java.util.List;

/**
 * Turns faces into HTML.
 *
 * <p>A pure function from values to markup, so it can be cached and served
 * from anywhere. Nothing here reads a clock, touches a file or evaluates
 * anything: by the time a face arrives, every decision has been made.
 *
 * <p>Everything a script supplied is escaped. A layout is data, and data
 * arriving from a script must not become markup, or the first person to put
 * user input in a caption has an injection.
 */
public final class Html {

    private Html() {
    }

    /** A whole layout as a page fragment. */
    public static String render(List<Face> faces) {
        return render(faces, java.util.List.of());
    }

    /**
     * A layout as a page fragment, with a handle on every face that can be
     * acted on so a browser can say which one it touched.
     */
    public static String render(List<Face> faces, List<String> handles) {
        StringBuilder page = new StringBuilder("<div class=\"jebol-layout\">");
        int actionable = 0;
        for (Face face : faces) {
            String handle = face.action().isPresent() && actionable < handles.size()
                    ? handles.get(actionable++)
                    : "";
            page.append(renderFace(face, handle));
        }
        return page.append("</div>").toString();
    }

    private static String renderFace(Face face, String handle) {
        String element = Layout.elementFor(face.kind());
        StringBuilder rendered = new StringBuilder("<").append(element);

        rendered.append(" class=\"jebol-").append(face.kind()).append('"');
        face.styleAttribute().ifPresent(styles ->
                rendered.append(" style=\"").append(escape(styles)).append('"'));
        if (!handle.isEmpty()) {
            rendered.append(" data-jebol-action=\"").append(escape(handle)).append('"');
        }

        if (isSelfClosing(element)) {
            return rendered
                    .append(" value=\"").append(escape(face.caption())).append('"')
                    .append(" />")
                    .toString();
        }
        return rendered
                .append('>')
                .append(escape(face.caption()))
                .append("</").append(element).append('>')
                .toString();
    }

    private static boolean isSelfClosing(String element) {
        return element.equals("input") || element.equals("img");
    }

    /** Escapes everything a script could have put here. */
    static String escape(String text) {
        StringBuilder escaped = new StringBuilder(text.length());
        text.chars().forEach(character -> {
            switch (character) {
                case '&' -> escaped.append("&amp;");
                case '<' -> escaped.append("&lt;");
                case '>' -> escaped.append("&gt;");
                case '"' -> escaped.append("&quot;");
                case '\'' -> escaped.append("&#39;");
                default -> escaped.append((char) character);
            }
        });
        return escaped.toString();
    }
}
