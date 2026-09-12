package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CompressionThisBuildLacksFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Test
    @DisplayName("a real method this build has not got is a feature that is not available")
    void aMissingMethodIsFeatureNa() {
        assertThat(answerTo("""
                collect [
                    foreach method [lz4 lzav][
                        raised: try [compress "test" method]
                        keep raised/id
                    ]
                ]""")).isEqualTo("[feature-na feature-na]");
    }

    @Test
    @DisplayName("and the names are the ones Rebol's own catalogue uses")
    void theNamesAreRebolsOwn() {
        assertThat(answerTo("""
                collect [
                    foreach method [deflate zlib gzip br crush lz4 lzav lzma lzw][
                        keep either error? e: try [compress "test" method][
                            e/id
                        ][ 'works ]
                    ]
                ]""")).as("only lz4 and lzav are still somebody else's")
                        .isEqualTo("[works works works works works"
                        + " feature-na feature-na works works]");
    }

    @Test
    @DisplayName("and a name that is no method at all is an invalid argument")
    void anUnknownMethodIsInvalidArg() {
        assertThat(answerTo("""
                raised: try [compress "test" 'nosuch]
                raised/id""")).isEqualTo("invalid-arg");
    }

    @Test
    @DisplayName("DECOMPRESS refuses them the same two ways")
    void decompressRefusesTheSameWays() {
        assertThat(answerTo("""
                first-raised: try [decompress #{} 'lz4]
                second-raised: try [decompress #{} 'nosuch]
                reduce [first-raised/id second-raised/id]"""))
                .isEqualTo("[feature-na invalid-arg]");
    }

    @Test
    @DisplayName("the methods this build does have still work")
    void theMethodsItHasStillWork() {
        assertThat(answerTo("""
                collect [
                    foreach method [gzip zlib deflate][
                        keep "test" = to string! decompress compress "test" method method
                    ]
                ]""")).isEqualTo("[#(true) #(true) #(true)]");
    }
}
