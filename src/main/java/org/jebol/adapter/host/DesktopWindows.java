package org.jebol.adapter.host;

import org.jebol.domain.eval.WindowPort;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * The operator's own screen, through the JDK's desktop and Swing.
 *
 * <p>Every dialog answers empty when the operator declines, which is an answer
 * rather than a refusal, and refuses with {@code no-service} where the machine
 * has no screen at all.
 */
public final class DesktopWindows implements WindowPort {

    public static DesktopWindows onThisMachine() {
        return new DesktopWindows();
    }

    @Override
    public void browse(String target) {
        requireADisplayBeforeSwingIsTouchedAtAll();
        if (!Desktop.isDesktopSupported()
                || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            throw new Denied("no-service", "this machine has no browser to open");
        }
        try {
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
            Optional<String> suggestedName, Optional<String> title,
            List<String> filterPairs) {

        requireADisplayBeforeSwingIsTouchedAtAll();
        JFileChooser chooser = new JFileChooser();
        chooser.setMultiSelectionEnabled(allowingMany);
        title.ifPresent(chooser::setDialogTitle);
        suggestedName.ifPresent(name -> chooser.setSelectedFile(new File(name)));
        eachFilterOf(filterPairs, chooser);

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

    private static void eachFilterOf(List<String> filterPairs, JFileChooser chooser) {
        for (int at = 0; at + 1 < filterPairs.size(); at += 2) {
            String name = filterPairs.get(at);
            String pattern = filterPairs.get(at + 1);
            if (pattern.startsWith("*.")) {
                chooser.addChoosableFileFilter(
                        new javax.swing.filechooser.FileNameExtensionFilter(
                                name, pattern.substring(2)));
            }
        }
    }

    @Override
    public Optional<String> chooseDirectory(
            Optional<String> startingAt, Optional<String> title) {

        requireADisplayBeforeSwingIsTouchedAtAll();
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        title.ifPresent(chooser::setDialogTitle);
        startingAt.ifPresent(where -> chooser.setCurrentDirectory(new File(where)));

        if (chooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) {
            return Optional.empty();
        }
        String chosen = chooser.getSelectedFile().getPath();
        return Optional.of(chosen.endsWith("/") ? chosen : chosen + "/");
    }

    @Override
    public Optional<int[]> chooseColour(Optional<int[]> suggested) {
        requireADisplayBeforeSwingIsTouchedAtAll();
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
    public Optional<String> askForPassword() {
        requireADisplayBeforeSwingIsTouchedAtAll();
        JPasswordField typing = new JPasswordField();
        int chose = JOptionPane.showConfirmDialog(
                null, typing, "Password",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (chose != JOptionPane.OK_OPTION) {
            return Optional.empty();
        }
        char[] typed = typing.getPassword();
        String secret = new String(typed);
        Arrays.fill(typed, '\0');
        return Optional.of(secret);
    }

    private static void requireADisplayBeforeSwingIsTouchedAtAll() {
        if (GraphicsEnvironment.isHeadless()) {
            throw new Denied("no-service", "this machine has no screen to put a window on");
        }
    }
}
