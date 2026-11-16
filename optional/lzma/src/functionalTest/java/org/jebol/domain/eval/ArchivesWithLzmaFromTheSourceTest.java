package org.jebol.domain.eval;

import org.jebol.application.AnInterpreter;
import org.jebol.application.CarriedArchives;
import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ArchivesWithLzmaFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = AnInterpreter.create();
        interpreter.defineNewWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Test
    @DisplayName("this build carries LZMA")
    void carriesLzma() {
        assertThat(answerTo("to logic! find system/catalog/compressions 'lzma")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("an LZMA entry in a zip decodes to its 899 bytes, whose CRC is the archive's own read as unsigned")
    void decodesLzmaEntry() {
        assertThat(answerTo("bin: " + CarriedArchives.asBinary("test-lzma.zip") + """

                info: codecs/zip/decode/info bin
                data: decode 'zip bin
                new-line/all reduce [data/1 length? data/2/2 (4294967295 and info/2/6) = checksum data/2/2 'crc32] false"""))
                .isEqualTo("[%xJSFL.komodoproject 899 #(true)]");
    }

    @Test
    @DisplayName("an LZMA-compressed movie decodes to the same tags as its zlib-compressed twin")
    void decodesLzmaMovieAsItsTwin() {
        assertThat(answerTo("deflated: " + CarriedArchives.asBinary("test1-deflate.swf")
                + "\nlzma: " + CarriedArchives.asBinary("test2-lzma.swf") + """

                import 'swf
                codecs/swf/verbose: 0
                first-movie: decode 'swf deflated
                second-movie: decode 'swf lzma
                reduce [second-movie/header/frames first-movie/tags = second-movie/tags]"""))
                .isEqualTo("[25 #(true)]");
    }
}
