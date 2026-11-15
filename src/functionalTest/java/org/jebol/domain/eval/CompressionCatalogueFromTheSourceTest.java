package org.jebol.domain.eval;

import org.jebol.application.AnInterpreter;
import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class CompressionCatalogueFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = AnInterpreter.create();
        interpreter.defineNewWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Test
    @DisplayName("the catalogue lists the methods this build has, in the order Rebol registers them")
    void listsTheMethodsInRebolsOrder() {
        assertThat(answerTo("system/catalog/compressions"))
                .isEqualTo("[deflate zlib gzip br lzma]");
    }

    @Test
    @DisplayName("every method in the catalogue compresses, and reads back what it wrote given the size")
    void everyMethodInTheCatalogueReadsBack() {
        assertThat(answerTo("""
                collect [
                    foreach method system/catalog/compressions [
                        keep "test" = to string! decompress/size compress "test" method method 4
                    ]
                ]""")).isEqualTo("[#(true) #(true) #(true) #(true) #(true)]");
    }

    @Test
    @DisplayName("a name that is no method at all is an invalid argument, to both")
    void raisesInvalidArgForAnUnknownMethod() {
        assertThat(answerTo("""
                first-raised: try [compress "test" 'nosuch]
                second-raised: try [decompress #{} 'nosuch]
                reduce [first-raised/id second-raised/id]""")).isEqualTo("[invalid-arg invalid-arg]");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"lzw", "crush", "lzav", "lz4"})
    @DisplayName("a method of R3's Bulk product that the core leaves out is an invalid argument naming it, to both, as R3's Core product answers")
    void raisesInvalidArgForAMethodTheCoreLeavesOut(String method) {
        assertThat(answerTo("""
                first-raised: try [compress "test" '%1$s]
                second-raised: try [decompress #{00010203} '%1$s]
                reduce [first-raised/id first-raised/arg1 second-raised/id second-raised/arg1]""".formatted(method)))
                .isEqualTo("[invalid-arg %1$s invalid-arg %1$s]".formatted(method));
    }
}
