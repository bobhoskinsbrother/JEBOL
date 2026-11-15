package org.jebol.adapter.host;

import javax.swing.JOptionPane;

final class SwingLeavingConfirmation implements LeavingConfirmation {

    private static final String QUESTION = "This app has unsaved work. Close it anyway?";
    private static final String HEADING = "Unsaved work";

    @Override
    public boolean operatorLeaves() {
        return JOptionPane.showConfirmDialog(null, QUESTION, HEADING,
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) == JOptionPane.YES_OPTION;
    }
}
