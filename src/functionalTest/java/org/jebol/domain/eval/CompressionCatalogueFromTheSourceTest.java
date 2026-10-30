package org.jebol.domain.eval;

import org.jebol.application.AnInterpreter;
import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CompressionCatalogueFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = AnInterpreter.create();
        interpreter.defineNewWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Test
    @DisplayName("the catalogue lists the nine methods in the order Rebol registers them")
    void listsTheMethodsInRebolsOrder() {
        assertThat(answerTo("system/catalog/compressions"))
                .isEqualTo("[deflate zlib gzip br crush lz4 lzav lzma lzw]");
    }

    @Test
    @DisplayName("every method in the catalogue compresses, and reads back what it wrote given the size")
    void everyMethodInTheCatalogueReadsBack() {
        assertThat(answerTo("""
                collect [
                    foreach method system/catalog/compressions [
                        keep "test" = to string! decompress/size compress "test" method method 4
                    ]
                ]""")).isEqualTo("[#(true) #(true) #(true) #(true) #(true) #(true) #(true) #(true) #(true)]");
    }

    @Test
    @DisplayName("a name that is no method at all is an invalid argument, to both")
    void raisesInvalidArgForAnUnknownMethod() {
        assertThat(answerTo("""
                first-raised: try [compress "test" 'nosuch]
                second-raised: try [decompress #{} 'nosuch]
                reduce [first-raised/id second-raised/id]""")).isEqualTo("[invalid-arg invalid-arg]");
    }
}
