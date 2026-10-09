package org.jebol.domain.render;

public record TextCaret(
        TextLines.RunAndCharacter at,
        TextLines.RunAndCharacter selectionFrom,
        TextLines.RunAndCharacter selectionTo) {

    public static final TextCaret NONE = new TextCaret(
            TextLines.RunAndCharacter.NOWHERE, TextLines.RunAndCharacter.NOWHERE, TextLines.RunAndCharacter.NOWHERE);

    public boolean isShown() {
        return !at.isNowhere();
    }

    public int run() {
        return at.run();
    }

    public int character() {
        return at.character();
    }

    public boolean marksASelection() {
        return !selectionFrom.isNowhere() && !selectionTo.isNowhere() && !selectionFrom.equals(selectionTo);
    }
}
