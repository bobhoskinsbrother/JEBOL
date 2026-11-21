package org.jebol.application;

import org.jebol.adapter.cli.JebolLauncher;
import org.jebol.adapter.cli.Repl;
import org.jebol.domain.value.BinaryValue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;

class EachSourceSetIsCompiledForItsJavaTest {

    private static final int JAVA_25_CLASS_FILE = 69;
    private static final int JAVA_21_CLASS_FILE = 65;
    private static final int JAVA_8_CLASS_FILE = 52;
    private static final int CLASS_FILE_MAGIC = 0xCAFEBABE;

    private static int majorVersionOf(Class<?> compiled) throws IOException {
        String resource = "/" + compiled.getName().replace('.', '/') + ".class";
        try (InputStream bytes = compiled.getResourceAsStream(resource)) {
            assertThat(bytes).as("the class file of %s", compiled.getName()).isNotNull();
            DataInputStream reading = new DataInputStream(bytes);
            assertThat(reading.readInt()).isEqualTo(CLASS_FILE_MAGIC);
            reading.readUnsignedShort();
            return reading.readUnsignedShort();
        }
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(classes = {
            Interpreter.class,
            BinaryValue.class,
            AnInterpreter.class
    })
    @DisplayName("the core and its fixtures are class files a Java 21 loads, as Android's ART and TeaVM need, and none newer")
    void theCoreIsCompiledForTwentyOne(Class<?> compiled) throws IOException {
        assertThat(majorVersionOf(compiled)).isEqualTo(JAVA_21_CLASS_FILE);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(classes = {
            Repl.class,
            EachSourceSetIsCompiledForItsJavaTest.class
    })
    @DisplayName("the desktop and its tests are compiled for Java 25, for the Foreign Function and Memory API")
    void theDesktopIsCompiledForTwentyFive(Class<?> compiled) throws IOException {
        assertThat(majorVersionOf(compiled)).isEqualTo(JAVA_25_CLASS_FILE);
    }

    @Test
    @DisplayName("the suites run on Java 25, the desktop's, since the desktop is every suite's platform under test")
    void theSuitesRunOnTwentyFive() {
        assertThat(Runtime.version().feature()).isEqualTo(25);
    }

    @Test
    @DisplayName("the launcher alone stays loadable by Java 8, so an old Java is told in words")
    void theLauncherStaysAtEight() throws IOException {
        assertThat(majorVersionOf(JebolLauncher.class)).isEqualTo(JAVA_8_CLASS_FILE);
    }
}
