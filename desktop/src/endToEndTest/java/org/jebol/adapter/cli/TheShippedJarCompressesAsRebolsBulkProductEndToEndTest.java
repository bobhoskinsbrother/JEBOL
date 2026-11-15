package org.jebol.adapter.cli;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(120)
class TheShippedJarCompressesAsRebolsBulkProductEndToEndTest {

    static final String EVERY_METHOD_ONCE = """
            text: {The quick brown fox jumps over the lazy dog. The quick brown fox jumps over the lazy dog.}
            print mold system/catalog/compressions
            foreach method system/catalog/compressions [
                packed: compress text method
                print [method checksum packed 'sha1 text = to string! decompress/size packed method length? text]
            ]""";

    static final List<String> WHAT_REBOLS_BULK_PRODUCT_PRINTS = List.of(
            "[deflate zlib gzip br crush lz4 lzav lzma lzw]",
            "deflate 218C73E64CC1E20225ADF9A6EF66042DBCECA2CB true",
            "zlib B501DB7552919F7B6B2635D2F8BC8451F3AF769A true",
            "gzip 646898C33F9FBC5BD21A3EA6A1E7F2BB6C17399D true",
            "br 86A201AD8296E2DA783DF0E7DBC05CB1A80B4B65 true",
            "crush A12E7D1D4330A7E5B2A2EC9D24EC6F9A78D03D62 true",
            "lz4 E143E81125163763B80D3802A061EBC5BECC39BC true",
            "lzav 34332985D2248A84C94E822365F165957243370C true",
            "lzma A61B324BD0E62B9F687A439D21FEC90935C15A40 true",
            "lzw 084FB1ED38E862111473EEC1C2F1ED082AA76A8B true");

    @Test
    @DisplayName("the shipped jar catalogues all nine methods and writes each one's bytes as ./r3-head does")
    void answersEveryMethodAsTheBulkProduct() throws Exception {
        Process running = new ProcessBuilder(List.of(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-jar", System.getProperty("jebol.shippedJar"), "--do", EVERY_METHOD_ONCE))
                .redirectErrorStream(true)
                .start();
        running.getOutputStream().close();
        String printed = new String(running.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

        assertThat(running.waitFor()).isZero();
        assertThat(printed.lines().toList()).containsExactlyElementsOf(WHAT_REBOLS_BULK_PRODUCT_PRINTS);
    }
}
