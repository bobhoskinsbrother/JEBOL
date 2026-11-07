package org.jebol.adapter.host;

import org.jebol.application.AnInterpreter;
import org.jebol.application.Bounds;
import org.jebol.application.ExtensionReport;
import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ExtensionDescriptorsFromTheSourceTest {

    private static final String GREETING = """
            name: greeting
            version: 1.0.0
            setups: [
                jvm [
                    provider: org.jebol.adapter.host.GreetingExtensionProvider
                    needs: []
                ]
            ]
            """;

    @TempDir
    Path directory;

    private final List<Path> jars = new ArrayList<>();

    private void addJarDescribedBy(String descriptor) throws IOException {
        Path jar = directory.resolve("jar" + jars.size());
        Path written = jar.resolve("META-INF/jebol/extension.reb");
        Files.createDirectories(written.getParent());
        Files.writeString(written, descriptor);
        jars.add(jar);
    }

    private Bounds readDescriptors() throws MalformedURLException {
        URL[] roots = new URL[jars.size()];
        for (int at = 0; at < jars.size(); at++) {
            roots[at] = jars.get(at).toUri().toURL();
        }
        ClassLoader loader = new URLClassLoader(roots, getClass().getClassLoader());
        return new ExtensionDescriptors(loader).addJvmSetupsTo(Bounds.standard());
    }

    private static String answerTo(Interpreter interpreter, String source) {
        interpreter.defineNewWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Test
    @DisplayName("a jar's jvm setup is added, and its commands answer")
    void addsTheJvmSetup() throws IOException {
        addJarDescribedBy(GREETING);

        Interpreter interpreter = AnInterpreter.withBounds(readDescriptors());

        assertThat(interpreter.extensions()).containsExactly(new ExtensionReport("greeting", Optional.empty()));
        assertThat(answerTo(interpreter, "greet = {hello from a jar}")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("with no descriptor the bounds are unchanged")
    void changesNothingWithoutAJar() throws IOException {
        assertThat(readDescriptors()).isEqualTo(Bounds.standard());
    }

    @Test
    @DisplayName("a descriptor with no jvm setup is refused as bad-extension, and the others still go in")
    void refusesAJarWithoutAJvmSetup() throws IOException {
        addJarDescribedBy("""
                name: browser-only
                version: 1.0.0
                setups: [web [provider: org.example.Web assets: []]]
                """);
        addJarDescribedBy(GREETING);

        assertThat(AnInterpreter.withBounds(readDescriptors()).extensions()).containsExactlyInAnyOrder(
                new ExtensionReport("browser-only", Optional.of("bad-extension")),
                new ExtensionReport("greeting", Optional.empty()));
    }

    @Test
    @DisplayName("a descriptor that does not read is refused as bad-extension")
    void refusesAnUnreadableDescriptor() throws IOException {
        addJarDescribedBy("name: broken setups: [jvm [");

        assertThat(AnInterpreter.withBounds(readDescriptors()).extensions()).containsExactly(
                new ExtensionReport("extension", Optional.of("bad-extension")));
    }

    @Test
    @DisplayName("a provider that is not on the classpath is refused as bad-extension")
    void refusesAMissingProvider() throws IOException {
        addJarDescribedBy(GREETING.replace("GreetingExtensionProvider", "NoSuchProvider"));

        assertThat(AnInterpreter.withBounds(readDescriptors()).extensions()).containsExactly(
                new ExtensionReport("greeting", Optional.of("bad-extension")));
    }

    @Test
    @DisplayName("a provider class that provides no extension is refused as bad-extension")
    void refusesAClassThatIsNoProvider() throws IOException {
        addJarDescribedBy(GREETING.replace("org.jebol.adapter.host.GreetingExtensionProvider", "java.lang.Object"));

        assertThat(AnInterpreter.withBounds(readDescriptors()).extensions()).containsExactly(
                new ExtensionReport("greeting", Optional.of("bad-extension")));
    }
}
