package org.jebol.devtools;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class AWixFragmentIsFilledAsXmlTest {

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"main.wxs", "overrides.wxi", "os-condition.wxf"})
    @DisplayName("a WiX source, include or fragment has its placeholders filled, escaped as XML")
    void fillsWixFilesAsXml(String name) {
        assertThat(FilledFiles.isText(Path.of(name))).isTrue();
        assertThat(Placeholders.escapingFor(name)).isEqualTo(Placeholders.Escaping.XML);
    }
}
