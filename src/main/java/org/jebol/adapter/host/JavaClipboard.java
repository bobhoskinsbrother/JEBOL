package org.jebol.adapter.host;

import org.jebol.domain.host.ClipboardPort;

import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;

public final class JavaClipboard implements ClipboardPort {

    private static final String NOTHING_SCRIPT_CAN_READ = "";

    @Override
    public String read() {
        Clipboard clipboard = systemClipboard();
        try {
            if (!clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor)) {
                return NOTHING_SCRIPT_CAN_READ;
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
            systemClipboard().setContents(new StringSelection(text), null);
        } catch (IllegalStateException busy) {
            throw new Unreachable(
                    "the clipboard could not be written: " + busy.getMessage());
        }
    }

    private Clipboard systemClipboard() {
        try {
            return Toolkit.getDefaultToolkit().getSystemClipboard();
        } catch (RuntimeException | Error error) {
            throw new Unreachable("this machine has no clipboard to reach: "
                    + messageFrom(error));
        }
    }

    private String messageFrom(Throwable noWindowingSystem) {
        return noWindowingSystem.getMessage() == null
                ? noWindowingSystem.getClass().getSimpleName()
                : noWindowingSystem.getMessage();
    }
}
