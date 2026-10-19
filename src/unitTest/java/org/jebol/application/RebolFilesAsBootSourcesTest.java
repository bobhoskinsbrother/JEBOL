package org.jebol.application;

import org.jebol.domain.eval.BootSources;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RebolFilesAsBootSourcesTest {

    private final RebolFiles rebolFiles = new RebolFiles(new ClasspathFiles());
    private final BootSources sources = rebolFiles;

    @Test
    @DisplayName("each boot source is the text of its own file")
    void answersEachFile() {
        assertThat(sources.errorCatalogue()).isEqualTo(rebolFiles.errors().textSource());
        assertThat(sources.datatypeSpecs()).isEqualTo(rebolFiles.typespec().textSource());
        assertThat(sources.operatorTable()).isEqualTo(rebolFiles.ops().textSource());
        assertThat(sources.modeTable()).isEqualTo(rebolFiles.modes().textSource());
    }

    @Test
    @DisplayName("the function declarations are the actions, natives and generated natives, in that order")
    void ordersFunctionDeclarations() {
        assertThat(sources.functionDeclarations()).containsExactly(
                rebolFiles.actions().textSource(),
                rebolFiles.natives().textSource(),
                rebolFiles.genNatives().textSource());
    }
}
