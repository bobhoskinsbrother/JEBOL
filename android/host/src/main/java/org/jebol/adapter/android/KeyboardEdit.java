package org.jebol.adapter.android;

public record KeyboardEdit(int deletions, String typed) {

    public static KeyboardEdit between(CharSequence before, CharSequence after) {
        String was = before.toString();
        String now = after.toString();
        int kept = sharedStart(was, now);
        return new KeyboardEdit(was.codePointCount(kept, was.length()), now.substring(kept));
    }

    private static int sharedStart(String was, String now) {
        int shorter = Math.min(was.length(), now.length());
        int kept = 0;
        while (kept < shorter && was.charAt(kept) == now.charAt(kept)) {
            kept++;
        }
        if (kept > 0 && Character.isHighSurrogate(was.charAt(kept - 1))) {
            kept--;
        }
        return kept;
    }
}
