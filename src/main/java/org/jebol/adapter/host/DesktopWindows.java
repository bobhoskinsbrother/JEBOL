package org.jebol.adapter.host;

import java.awt.Color;
import java.awt.Desktop;
import java.awt.GraphicsEnvironment;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import javax.swing.JColorChooser;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.JPasswordField;
import org.jebol.domain.eval.WindowPort;

/**
 * The operator's own screen, through the JDK's desktop and Swing.
 *
 * <p>Every other host service has an adapter and this is the one for windows.
 * Without it the five dialogs are reachable and can never succeed: a host
 * would have to write its own, and BROWSE could not open a browser in any
 * shipped configuration.
 *
 * <p>Refuses when there is no display. A machine with no screen has no such
 * service to give, which is {@code not_present} rather than a host withholding
 * something, and it is the same answer {@link WindowPort#none()} gives for the
 * same reason. Checking it here rather than letting Swing throw keeps a
 * headless server from seeing an {@code AWTError} out of a script.
 *
 * <p>Every dialog answers empty when the operator declines, which is an answer
 * rather than a refusal. Swing says the same thing three different ways --
 * a null file, {@code CANCEL_OPTION}, a null colour -- so each is translated
 * here rather than left for the domain to know about.
 */
public final class DesktopWindows implements WindowPort {

    /** A screen, or a refusal if this machine has not got one. */
    public static DesktopWindows onThisMachine() {
        return new DesktopWindows();
    }

    @Override
    public void browse(String target) {
        requireADisplay();
        if (!Desktop.isDesktopSupported()
                || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            throw new Denied("no-service", "this machine has no browser to open");
        }
        try {
            // A file and a URL reach the browser differently: a file has to
            // become a file URI first, and a bare path is not one.
            URI where = target.startsWith("/") || target.startsWith("./")
                    ? new File(target).toURI()
                    : new URI(target);
            Desktop.getDesktop().browse(where);
        } catch (URISyntaxException malformed) {
            throw new Denied("invalid-arg", target + " is not something a browser can open");
        } catch (IOException refused) {
            throw new Denied("cannot-open", "the browser would not open " + target);
        }
    }

    @Override
    public List<String> chooseFiles(
            boolean forSaving, boolean allowingMany,
            Optional<String> suggestedName, Optional<String> title) {

        requireADisplay();
        JFileChooser chooser = new JFileChooser();
        chooser.setMultiSelectionEnabled(allowingMany);
        title.ifPresent(chooser::setDialogTitle);
        suggestedName.ifPresent(name -> chooser.setSelectedFile(new File(name)));

        int chose = forSaving
                ? chooser.showSaveDialog(null)
                : chooser.showOpenDialog(null);
        if (chose != JFileChooser.APPROVE_OPTION) {
            return List.of();
        }
        if (!allowingMany) {
            return List.of(chooser.getSelectedFile().getPath());
        }
        List<String> chosen = new ArrayList<>();
        Arrays.stream(chooser.getSelectedFiles())
                .forEach(one -> chosen.add(one.getPath()));
        return chosen;
    }

    @Override
    public Optional<String> chooseDirectory(
            Optional<String> startingAt, Optional<String> title) {

        requireADisplay();
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        title.ifPresent(chooser::setDialogTitle);
        startingAt.ifPresent(where -> chooser.setCurrentDirectory(new File(where)));

        if (chooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) {
            return Optional.empty();
        }
        // A directory ends with a slash, which is how everything else in the
        // language tells one from a file.
        String chosen = chooser.getSelectedFile().getPath();
        return Optional.of(chosen.endsWith("/") ? chosen : chosen + "/");
    }

    @Override
    public Optional<int[]> chooseColour(Optional<int[]> suggested) {
        requireADisplay();
        Color start = suggested
                .filter(octets -> octets.length >= 3)
                .map(octets -> new Color(octets[0], octets[1], octets[2]))
                .orElse(Color.WHITE);
        Color chosen = JColorChooser.showDialog(null, "Choose a colour", start);
        return chosen == null
                ? Optional.empty()
                : Optional.of(new int[] {
                        chosen.getRed(), chosen.getGreen(), chosen.getBlue()});
    }

    @Override
    public Optional<String> askForPassword(Optional<String> title) {
        requireADisplay();
        // A password field rather than a text field, so what the operator
        // types does not appear on the screen. That is the whole reason this
        // is a separate request.
        JPasswordField typing = new JPasswordField();
        int chose = JOptionPane.showConfirmDialog(
                null, typing, title.orElse("Password"),
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (chose != JOptionPane.OK_OPTION) {
            return Optional.empty();
        }
        // Cleared after reading, because a char array is the reason
        // JPasswordField hands one back rather than a String.
        char[] typed = typing.getPassword();
        String secret = new String(typed);
        Arrays.fill(typed, '\0');
        return Optional.of(secret);
    }

    /**
     * Refuses when this machine has no screen.
     *
     * <p>Asked before Swing is touched. Swing throws an {@code AWTError} in a
     * headless JVM, and an error is exactly what must never escape a script:
     * the promise is that every failure arrives as a catchable {@code error!}.
     */
    private static void requireADisplay() {
        if (GraphicsEnvironment.isHeadless()) {
            throw new Denied("no-service", "this machine has no screen to put a window on");
        }
    }
}
