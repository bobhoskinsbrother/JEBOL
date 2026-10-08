package org.jebol.adapter.host;

import org.jebol.domain.host.ClipboardPort;

import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;

public final class JavaClipboard implements ClipboardPort {

    private static final String NOTHING_A_SCRIPT_CAN_READ = "";

    @Override
    public String read() {
        Clipboard clipboard = theSystemClipboard();
        try {
            if (!clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor)) {
                return NOTHING_A_SCRIPT_CAN_READ;
            }
            return (String) clipboard.getData(DataFlavor.stringFlavor);
        } catch (UnsupportedFlavorException | IOException | IllegalStateException
                 unreadable) {
            throw new Unreachable(
                    "the clipboard could not be read: " + unreadable.getMessage());
        }
    }

    @Override
    public void write(String text) {
        try {
            theSystemClipboard().setContents(new StringSelection(text), null);
        } catch (IllegalStateException busy) {
            throw new Unreachable(
                    "the clipboard could not be written: " + busy.getMessage());
        }
    }

    private static Clipboard theSystemClipboard() {
        try {
            return Toolkit.getDefaultToolkit().getSystemClipboard();
        } catch (RuntimeException | Error noWindowingSystem) {
            throw new Unreachable("this machine has no clipboard to reach: "
                    + becauseOf(noWindowingSystem));
        }
    }

    private static String becauseOf(Throwable refusal) {
        return refusal.getMessage() == null
                ? refusal.getClass().getSimpleName()
                : refusal.getMessage();
    }
}
