package org.jebol.application;

import org.jebol.domain.host.FilePort;
import org.jebol.domain.value.AnyBlockValue;
import org.jebol.domain.value.Molder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ALibraryFileIsReadOnceFromTheSourceTest {

    private final AtomicInteger reads = new AtomicInteger();

    private final String path = "/org/jebol/read-once-" + UUID.randomUUID() + ".reb";

    private RebolFile fileHolding(String source) {
        FilePort counting = (FilePort) Proxy.newProxyInstance(
                FilePort.class.getClassLoader(), new Class<?>[] {FilePort.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "readBytes" -> {
                        reads.incrementAndGet();
                        yield source.getBytes(StandardCharsets.UTF_8);
                    }
                    case "exists" -> true;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        return new RebolFile(path, counting);
    }

    @Test
    @DisplayName("a second read of a library file answers from what the first read parsed, without reading the file again")
    void asecondReadDoesNotTouchTheFile() {
        RebolFile file = fileHolding("Rebol [] one: 1 two: [2]");

        AnyBlockValue first = file.readTranscoded().values().orElseThrow();
        AnyBlockValue second = file.readTranscoded().values().orElseThrow();

        assertThat(reads).hasValue(1);
        assertThat(Molder.mold(second)).isEqualTo(Molder.mold(first));
    }

    @Test
    @DisplayName("asking whether a file reads as REBOL reads it at most once, and the read after it reads nothing")
    void askingWhetherItReadsCostsOneRead() {
        RebolFile file = fileHolding("Rebol [] three: 3");

        assertThat(file.isLibraryFile()).isTrue();
        assertThat(file.isLibraryFile()).isTrue();
        file.readLibraryFile();

        assertThat(reads).hasValue(1);
    }

    @Test
    @DisplayName("each read still hands out its own copy, so one interpreter changing its library changes no other")
    void eachReadIsItsOwnCopy() {
        RebolFile file = fileHolding("Rebol [] block: [a b]");

        AnyBlockValue first = file.readTranscoded().values().orElseThrow();
        AnyBlockValue second = file.readTranscoded().values().orElseThrow();

        assertThat(second).isNotSameAs(first);
    }

    @Test
    @DisplayName("a file that does not read as REBOL is refused each time it is asked for")
    void aFileThatDoesNotReadIsRefusedEachTime() {
        RebolFile file = fileHolding("Rebol [] unclosed: [");

        assertThat(file.isLibraryFile()).isFalse();
        assertThat(file.readTranscoded().values()).isEmpty();
    }
}
