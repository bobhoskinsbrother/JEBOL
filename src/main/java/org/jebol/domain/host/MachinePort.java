package org.jebol.domain.host;

import org.jebol.domain.value.Value;

/**
 * What the host says about the machine an interpreter runs on.
 *
 * <p>Told rather than read, so a host may describe a machine other than the
 * one the JVM is on: a browser, or Windows in a test running on a Mac. Every
 * interpreter has one, and a host that supplies none gets the JVM's own.
 *
 * <p>Specified in {@code spec/embed.allium} as HostMachine.
 */
public interface MachinePort {

    /** The operating system as Rebol's library names it: Windows, macOS, Linux, or the system's own name. Never blank. */
    String operatingSystem();

    char fileSeparator();

    String homeDirectory();

    String currentDirectory();

    long processNumber();

    /**
     * A program that starts another interpreter rooted at {@code root} and told
     * {@code dataDirectory}, as a {@code file!}; or none where this machine
     * cannot make one. An empty data directory means the child works it out.
     */
    Value launcherFor(String root, String dataDirectory);

    /** A machine that answers nothing, which is what an evaluator built without an interpreter has. */
    static MachinePort none() {
        return new MachinePort() {
            @Override
            public String operatingSystem() {
                throw refuse();
            }

            @Override
            public char fileSeparator() {
                throw refuse();
            }

            @Override
            public String homeDirectory() {
                throw refuse();
            }

            @Override
            public String currentDirectory() {
                throw refuse();
            }

            @Override
            public long processNumber() {
                throw refuse();
            }

            @Override
            public Value launcherFor(String root, String dataDirectory) {
                throw refuse();
            }

            private FilePort.Denied refuse() {
                return new FilePort.Denied("no-port", "this script was given no machine to ask");
            }
        };
    }
}
