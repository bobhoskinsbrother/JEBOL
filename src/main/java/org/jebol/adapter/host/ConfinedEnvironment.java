package org.jebol.adapter.host;

import org.jebol.domain.host.EnvironmentPort;
import org.jebol.domain.host.FilePort;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class ConfinedEnvironment implements EnvironmentPort {

    private static final List<String> NAMES_HOLDING_HOME = List.of("HOME", "USERPROFILE");

    private static final String APPLICATION_DATA = "APPDATA";

    private static final String ROOT_AS_SCRIPT_SEES_IT = "/";

    private final EnvironmentPort machine;

    private final Map<String, Optional<String>> toldInsteadOfMachines = new HashMap<>();

    private ConfinedEnvironment(EnvironmentPort machine) {
        this.machine = machine;
    }

    public static EnvironmentPort confine(EnvironmentPort machine, FilePort files) {
        ConfinedEnvironment confined = new ConfinedEnvironment(machine);
        for (String name : NAMES_HOLDING_HOME) {
            Optional.ofNullable(machine.valueOf(name)).ifPresent(home -> confined.toldInsteadOfMachines.put(name,
                    Optional.of(asLocalPath(files.findScriptPath(home).orElse(ROOT_AS_SCRIPT_SEES_IT)))));
        }
        Optional.ofNullable(machine.valueOf(APPLICATION_DATA)).ifPresent(data ->
                confined.toldInsteadOfMachines.put(APPLICATION_DATA,
                        files.findScriptPath(data).map(ConfinedEnvironment::asLocalPath)));
        return confined;
    }

    private static String asLocalPath(String scriptPath) {
        return scriptPath.replace('/', File.separatorChar);
    }

    @Override
    public String valueOf(String name) {
        return toldInsteadOfMachines.containsKey(name)
                ? toldInsteadOfMachines.get(name).orElse(null)
                : machine.valueOf(name);
    }

    @Override
    public Map<String, String> readAll() {
        Map<String, String> reported = new HashMap<>(machine.readAll());
        toldInsteadOfMachines.forEach((name, told) -> told.ifPresentOrElse(
                value -> reported.put(name, value),
                () -> reported.remove(name)));
        return Map.copyOf(reported);
    }

    @Override
    public Map<String, String> listWhatProgramsInherit() {
        return machine.listWhatProgramsInherit();
    }

    @Override
    public void assign(String name, String value) {
        toldInsteadOfMachines.remove(name);
        machine.assign(name, value);
    }
}
