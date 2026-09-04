package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The two ways COMPRESS refuses a method, which are two different answers.
 *
 * <p>A name nobody has heard of is an invalid argument. A method REBOL really
 * has and this build was not compiled with is {@code feature-na}, which is
 * what that error is for and what tells a caller to look for another build
 * rather than for a typo.
 *
 * <p>Rebol's own suite is written for both: each of the four groups for a
 * method that may be missing opens with a try and accepts {@code feature-na}
 * as the whole answer, because a build without the algorithm is an ordinary
 * build rather than a broken one.
 */
class CompressionThisBuildLacksFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    /**
     * The names are Rebol's, and taking them from anywhere else is the bug.
     *
     * <p>This asked about {@code brotli} because that is what JEBOL called
     * it. Rebol calls it {@code br} -- {@code system/catalog/compressions} is
     * {@code [deflate zlib gzip br crush lz4 lzav lzma lzw]} -- so the one
     * name Rebol's own suite asks about answered {@code invalid-arg}, a name
     * nobody has heard of, where the suite was waiting to be told the build
     * has not got it. A test written from the port rather than from the thing
     * being ported agrees with the port and finds nothing.
     */
    @Test
    @DisplayName("a real method this build has not got is a feature that is not available")
    void aMissingMethodIsFeatureNa() {
        assertThat(answerTo("""
                collect [
                    foreach method [br lz4 lzav lzma lzw][
                        raised: try [compress "test" method]
                        keep raised/id
                    ]
                ]""")).isEqualTo("[feature-na feature-na feature-na"
                        + " feature-na feature-na]");
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
                ]""")).as("crush is written out here; the other five are not")
                        .isEqualTo("[works works works feature-na works"
                        + " feature-na feature-na feature-na feature-na]");
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
                first-raised: try [decompress #{} 'lzw]
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
