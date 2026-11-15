package org.jebol.adapter.cli;

import java.util.List;

interface TerminalSettings {

    String read();

    void change(List<String> settings);

    void restore(String saved);

    int eraseCharacter();
}
