package org.jebol.domain.eval;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CompressionMethodsTest {

    private record Provided(String name, int placeInRebolsOrder, CompressionMethod method)
            implements OptionalCompression {
    }

    private static CompressionMethod answering(byte[] answer) {
        return new CompressionMethod() {
            @Override
            public byte[] compress(byte[] octets, long level) {
                return answer;
            }

            @Override
            public byte[] decompress(byte[] octets, long limit) {
                return answer;
            }
        };
    }

    private static final CompressionMethod OPTIONAL = answering(new byte[] {1});

    private static final CompressionMethod REGISTERED = answering(new byte[] {2});

    @Test
    @DisplayName("with nothing optional the catalogue is the three every build has")
    void catalogueHoldsTheBaseThreeAlone() {
        assertThat(new CompressionMethods(List.of()).catalogue()).containsExactly("deflate", "zlib", "gzip");
    }

    @Test
    @DisplayName("optional methods follow the base three in Rebol's order, whatever order they were found in")
    void cataloguesOptionalMethodsInRebolsOrder() {
        CompressionMethods methods = new CompressionMethods(List.of(
                new Provided("lzw", 8, OPTIONAL),
                new Provided("br", 3, OPTIONAL),
                new Provided("lz4", 5, OPTIONAL)));

        assertThat(methods.catalogue()).containsExactly("deflate", "zlib", "gzip", "br", "lz4", "lzw");
    }

    @Test
    @DisplayName("an optional method is known and found, and one nothing provides is neither")
    void findsOnlyWhatIsProvided() {
        CompressionMethods methods = new CompressionMethods(List.of(new Provided("br", 3, OPTIONAL)));

        assertThat(methods.isKnown("br")).isTrue();
        assertThat(methods.find("br")).containsSame(OPTIONAL);
        assertThat(methods.isKnown("lzma")).isFalse();
        assertThat(methods.find("lzma")).isEmpty();
    }

    @Test
    @DisplayName("the base three are known and found in none of the tables, so Encodings serves them")
    void knowsTheBaseThreeWithoutFindingThem() {
        CompressionMethods methods = new CompressionMethods(List.of());

        assertThat(List.of("deflate", "zlib", "gzip")).allMatch(methods::isKnown);
        assertThat(methods.find("zlib")).isEmpty();
    }

    @Test
    @DisplayName("an extension's method is found in place of the optional one, and restoring brings the optional one back")
    void restoresTheOptionalMethodUnderAnExtensionsOne() {
        CompressionMethods methods = new CompressionMethods(List.of(new Provided("br", 3, OPTIONAL)));

        methods.register("br", REGISTERED);
        assertThat(methods.find("br")).containsSame(REGISTERED);

        methods.restoreBuiltIn("br");
        assertThat(methods.find("br")).containsSame(OPTIONAL);
    }

    @Test
    @DisplayName("an extension's method under a new name is known until it is restored")
    void forgetsAnExtensionsNewMethodOnRestoring() {
        CompressionMethods methods = new CompressionMethods(List.of());

        methods.register("zstd", REGISTERED);
        assertThat(methods.isKnown("zstd")).isTrue();

        methods.restoreBuiltIn("zstd");
        assertThat(methods.isKnown("zstd")).isFalse();
    }
}
