package org.jebol.domain.eval.deflate;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.zip.CRC32;

import static org.assertj.core.api.Assertions.assertThat;

class DeflateCompressorFromTheSourceTest {

    private final DeflateFixtures fixtures = new DeflateFixtures();

    private byte[] compressed(String method, int level, byte[] input) {
        DeflateCompressor compressor = new DeflateCompressor(level);
        return switch (method) {
            case "zlib" -> compressor.zlib(input);
            case "gzip" -> compressor.gzip(input);
            default -> compressor.deflate(input);
        };
    }

    private String hexOf(byte[] bytes) {
        return HexFormat.of().withUpperCase().formatHex(bytes);
    }

    private String sha256Of(byte[] bytes) throws NoSuchAlgorithmException {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private String crc32Of(byte[] bytes) {
        CRC32 checksum = new CRC32();
        checksum.update(bytes);
        return Long.toHexString(checksum.getValue());
    }

    @Nested
    @DisplayName("each fixture is the input it claims to be")
    class TheFixtures {

        @ParameterizedTest(name = "{0} of {1} bytes has CRC-32 {2}")
        @CsvSource(delimiter = '|', textBlock = """
                words  | 0      | 0
                words  | 1      | 5767df55
                words  | 7      | d994de09
                words  | 8      | 6cd06b43
                words  | 51     | 501cb5e
                words  | 52     | 9bc2790
                words  | 20000  | 75f1f8b
                words  | 32769  | a81bedc4
                words  | 70000  | 4405db75
                words  | 310000 | 7c25e5b
                mixed  | 100000 | 510103e1
                noise  | 40000  | 11d1fcf
                zeros  | 70000  | a6a9c8dc
                """)
        void holdsWhatItClaims(String kind, int length, String crc) {
            byte[] fixture = fixtures.of(kind, length);
            assertThat(fixture).hasSize(length);
            assertThat(crc32Of(fixture)).isEqualTo(crc);
        }
    }

    @Nested
    @DisplayName("an input at or under 55 minus four times the level is stored, and one byte more is compressed")
    class ThePassthroughLength {

        @ParameterizedTest(name = "level {0} of {1} bytes")
        @CsvSource(delimiter = '|', textBlock = """
                 1 | 51 | 013300CCFF5245424F4C20237B30307D2062696E61727920717569636B20717569636B205D0A6C617A7920666F7820636F6D707265737320
                 1 | 52 | 0B7275F2F75150AE3630A85548CACC4B2CAA54282CCD4CCE8692B15C398955950A69F9150AC9F9B90545A9C5C50A2500
                 2 | 47 | 012F00D0FF5245424F4C20237B30307D2062696E61727920717569636B20717569636B205D0A6C617A7920666F7820636F6D7072
                 2 | 48 | 0B7275F2F75150AE3630A85548CACC4B2CAA54282CCD4CCE8692B15C398955950A69F9150AC9F9B90545A900
                 3 | 43 | 012B00D4FF5245424F4C20237B30307D2062696E61727920717569636B20717569636B205D0A6C617A7920666F782063
                 3 | 44 | 0B7275F2F75150AE3630A85548CACC4B2CAA54282CCD4CCE8692B15C398955950A69F9150AC9F900
                 4 | 39 | 012700D8FF5245424F4C20237B30307D2062696E61727920717569636B20717569636B205D0A6C617A792066
                 4 | 40 | 0B7275F2F75150AE3630A85548CACC4B2CAA54282CCD4CCE8692B15C398955950A69F900
                 5 | 35 | 012300DCFF5245424F4C20237B30307D2062696E61727920717569636B20717569636B205D0A6C61
                 5 | 36 | 0B7275F2F75150AE3630A85548CACC4B2CAA54282CCD4CCE8692B15C39895500
                 6 | 31 | 011F00E0FF5245424F4C20237B30307D2062696E61727920717569636B20717569636B20
                 6 | 32 | 0B7275F2F75150AE3630A85548CACC4B2CAA54282CCD4CCE8692B100
                 7 | 27 | 011B00E4FF5245424F4C20237B30307D2062696E61727920717569636B207175
                 7 | 28 | 0B7275F2F75150AE3630A85548CACC4B2CAA54282CCD4CCE56282CCD0400
                 8 | 23 | 011700E8FF5245424F4C20237B30307D2062696E6172792071756963
                 8 | 24 | 0B7275F2F75150AE3630A85548CACC4B2CAA54282CCD4CCE0600
                 9 | 19 | 011300ECFF5245424F4C20237B30307D2062696E61727920
                 9 | 20 | 0B7275F2F75150AE3630A85548CACC4B2CAA54280400
                10 | 15 | 010F00F0FF5245424F4C20237B30307D2062696E
                10 | 16 | 0B7275F2F75150AE3630A85548CACC4B0400
                11 | 11 | 010B00F4FF5245424F4C20237B30307D
                11 | 12 | 0B7275F2F75150AE3630A8550000
                12 |  7 | 010700F8FF5245424F4C2023
                12 |  8 | 0B7275F2F75150AE0600
                """)
        void storesUpToTheLengthAndCompressesPastIt(int level, int length, String wanted) {
            assertThat(hexOf(compressed("deflate", level, fixtures.of("words", length)))).isEqualTo(wanted);
        }
    }

    @Nested
    @DisplayName("the smallest inputs and the three wrappers")
    class TheSmallest {

        @ParameterizedTest(name = "{0} at level {1} of {2} bytes")
        @CsvSource(delimiter = '|', textBlock = """
                deflate | 12 | 0 | 010000FFFF
                deflate | 0  | 0 | 010000FFFF
                deflate | 12 | 1 | 010100FEFF52
                zlib    | 12 | 0 | 78DA010000FFFF00000001
                zlib    | 1  | 1 | 7801010100FEFF5200530053
                gzip    | 12 | 1 | 1F8B08000000000002FF010100FEFF5255DF675701000000
                gzip    | 1  | 0 | 1F8B08000000000004FF010000FFFF0000000000000000
                gzip    | 5  | 1 | 1F8B08000000000000FF010100FEFF5255DF675701000000
                """)
        void writesWhatLibdeflateWrites(String method, int level, int length, String wanted) {
            assertThat(hexOf(compressed(method, level, fixtures.of("words", length)))).isEqualTo(wanted);
        }
    }

    @Nested
    @DisplayName("every parser, across the window, the block lengths and the block splitter")
    class EveryParser {

        @ParameterizedTest(name = "{0} at level {1} of {2} {3} bytes")
        @CsvSource(delimiter = '|', textBlock = """
                zlib    |  0 | words | 20000  | 20011 | b0d98485680a094f71321d0c6d2fc8d671ab32f1de810d761deb0942216998ef
                zlib    |  1 | words | 20000  | 4766  | cdaff359f9ba2b51a64f4ab857881c95e32bdc112b112799be66fe3d72d31315
                zlib    |  2 | words | 20000  | 4659  | a4a58580a5e9f4cb8da3e6562536a949c0f4d17452cca1d8b1cd61a7b76befe7
                zlib    |  3 | words | 20000  | 4419  | 161c8be58e958757465357a7c8389782b76e40b9a9824153d1a917d28054511e
                zlib    |  4 | words | 20000  | 4307  | aa50c7cf5c76ba8d85d01ee0e4cc17914dcc05b1956be2a89bccae207b78e3c1
                zlib    |  5 | words | 20000  | 4300  | 7d02671462fa4f7a2f7a2b6053dc7de23f9a4389f443f4da0bb660a466b1acca
                zlib    |  6 | words | 20000  | 4018  | 114d65304fb0b66deeb8df6f1c3aa6cc03ebd55329dc2ee311e0b4f6c9c844d0
                zlib    |  7 | words | 20000  | 3882  | 6607f32a5096354837af6db9b0f2c560131de1bbce722a9cb4054b04efc23f11
                zlib    |  8 | words | 20000  | 3864  | 8e2832f237579ec0010ce7703689cf5a706ef435684f662d23570cb6dc1b4bc2
                zlib    |  9 | words | 20000  | 3862  | 4e98bcf4864dc238b4a194b6a3fa6ca19787f85b710592cac193d458f11f5d86
                zlib    | 10 | words | 20000  | 3574  | bcb75d9c0b17bca3cdd8d8b45a9a0e97ebdfc92868b1d01dae09d7fb39dab3a8
                zlib    | 11 | words | 20000  | 3557  | f774fe826f33f2cb792dc9c9ad54c65e933c67b483a05a9f83a64c3172a0518b
                zlib    | 12 | words | 20000  | 3549  | 481f650ebfed264b88e2becd2258902a2a8cc6310139a6f3c92a08da586f981e
                deflate |  0 | words | 70000  | 70010 | d64b34ca8647a53e0cf96b69738ab4c509370de6b8943c8315136cfb437a7c04
                deflate |  1 | words | 70000  | 16225 | 6051f0f2562778f2eca0bf7d9ebb72bc81bd9cf79e50b9592dbd55042688804a
                deflate |  4 | words | 310000 | 62912 | 24cbefa7aaea77c74c29aab39f0b1e8dd069c80b5d87b401b10c27c78103408f
                deflate |  6 | words | 310000 | 58553 | 8ff043fbb63bcb30a8e831edb236b6c3c8933c8dbfef59a506c9038dfc47a9ec
                deflate |  9 | words | 310000 | 52443 | c5a2501b9a8c96d6e86d7b9d43a682154085960d80902f6882cadfe229a40f97
                deflate | 12 | words | 310000 | 48544 | 8255cf3937da75d561b218c3610626c9e084b28c02ad1a15113082655a88a00a
                deflate |  2 | mixed | 100000 | 64837 | 572f7eaea8cd14b3e87eb5cf07912845c8f657875c93b215319372322f043436
                deflate |  5 | mixed | 100000 | 63470 | d1bc664b299b1e44969591e9b56113df36b264f2fbf2129387215b626e84f323
                deflate |  8 | mixed | 100000 | 61732 | 252115d2e5f47142f9e74019ba358b9e88ec9f87cda76ce24d58a51b71cf1c9a
                deflate | 10 | mixed | 100000 | 61464 | cb1b45d10be246d720fbf657547f58e533157c52ecaa91c548cfcf60b9298d99
                deflate | 11 | mixed | 100000 | 61481 | 193b53481ba858c2b5121e8d04e86e5ba5acf5dd0872e624497547fb441c4dd2
                deflate | 12 | mixed | 100000 | 61453 | 81da44d08b26dc3c87aee0c0055abbe1a527b4d011fa92568364e5d9b79edb5e
                deflate |  1 | noise | 40000  | 40005 | f0fca6dc68d48ac518655a05ec3e5a999b103569282af166455c504484ed5801
                deflate | 12 | noise | 40000  | 40005 | f0fca6dc68d48ac518655a05ec3e5a999b103569282af166455c504484ed5801
                deflate |  1 | zeros | 70000  | 84    | 35f7b714bbc093b163bbe6090aaa781e86fb1dfcf4fe4afa9842b5c5f4e931ab
                deflate |  6 | zeros | 70000  | 84    | 35f7b714bbc093b163bbe6090aaa781e86fb1dfcf4fe4afa9842b5c5f4e931ab
                deflate | 12 | zeros | 70000  | 84    | 35f7b714bbc093b163bbe6090aaa781e86fb1dfcf4fe4afa9842b5c5f4e931ab
                deflate | 12 | words | 32769  | 5572  | ca187bba71fd8152d999a440d7c1e1e9410677fe0029b788836116806110ea23
                gzip    | 12 | words | 70000  | 11379 | 666bda3f489cbd3b4ecb84fde195d969e69dd3afda8f5f3623437e4220e08b46
                gzip    |  3 | words | 70000  | 14886 | af7fca90c10e99e40e5f45660ddf373089e61cea19b3ca9a3afc08c00bb03137
                """)
        void writesWhatLibdeflateWrites(String method, int level, String kind, int length,
                int wantedLength, String wantedSha256) throws NoSuchAlgorithmException {
            byte[] written = compressed(method, level, fixtures.of(kind, length));
            assertThat(written).hasSize(wantedLength);
            assertThat(sha256Of(written)).isEqualTo(wantedSha256);
        }
    }

    @Nested
    @DisplayName("a level outside nought to twelve is twelve, as an unsigned twelve-capped level is in the C")
    class OutsideTheRange {

        @ParameterizedTest(name = "level {0}")
        @ValueSource(ints = {13, 99, Integer.MAX_VALUE, -1, -2, Integer.MIN_VALUE})
        void isTwelve(int level) {
            byte[] input = fixtures.of("words", 20000);
            assertThat(compressed("zlib", level, input)).isEqualTo(compressed("zlib", 12, input));
        }
    }

    @Nested
    @DisplayName("compress, as a REBOL script calls it, answers what r3 answers")
    class ThroughTheInterpreter {

        private String answerTo(String source) {
            Interpreter interpreter = Interpreter.create();
            interpreter.defineFreshWordsIn(source);
            return interpreter.display(interpreter.run(source));
        }

        @ParameterizedTest(name = "{0}")
        @CsvSource(delimiter = '|', quoteCharacter = '`', textBlock = """
                compress "abcdef" 'zlib                                           | #{78DA010600F9FF616263646566081E0256}
                compress/level "abcdef" 'deflate 0                                | #{010600F9FF616263646566}
                compress/level {Hello Hello Hello Hello Hello Hello Hello Hello Hello Hello Hello Hello Hello} 'deflate 1 | #{F348CDC9C957F0A00E0900}
                compress {Hello Hello Hello Hello Hello Hello Hello Hello Hello Hello Hello Hello Hello} 'gzip | #{1F8B08000000000002FFF348CDC9C957F0A00E090073074E704D000000}
                compress/part {abcdefabcdefabcdefabcdefabcdefabcdefabcdefabcdefabcdefabcdef} 'zlib 40 | #{78DA4B4C4A4E494D4B244802003E750F89}
                compress/level #{} 'deflate 5                                     | #{010000FFFF}
                compress/level "abcdef" 'zlib 2                                   | #{785E010600F9FF616263646566081E0256}
                """)
        void answersAsR3Does(String source, String wanted) {
            assertThat(answerTo(source)).isEqualTo(wanted);
        }

        @Test
        @DisplayName("a script's three hundred and fifty kilobytes compress to r3's bytes by every wrapper")
        void aLargeScriptInput() {
            assertThat(answerTo("""
                    data: copy {} repeat n 20000 [append data rejoin [n " block " n * 7 " "]]
                    reduce [
                        length? data
                        checksum data 'sha256
                        length? compress data 'zlib
                        checksum compress data 'zlib 'sha256
                        checksum compress/level data 'deflate 1 'sha256
                        checksum compress/level data 'gzip 6 'sha256
                        equal? data to string! decompress compress data 'gzip 'gzip
                    ]""")).isEqualTo("""
                    [353024 #{AFEDF5095F91D1F9BB52D100F035E485FB33B45E040DBD3EA4781C756B7CAE10} \
                    65656 #{D977F9F393EC43FBF27C49D6A6380D8BF0FB90674012EC8C11F96772B16B820E} \
                    #{5886C3672D12CF6037A0FFB9F11BE0072453CC2992EC36F08B23E8263461A61A} \
                    #{2698437EC5426273B8F5E08AAD34D3D46583035DA48456B937198A1CD4C2696D} #(true)]""");
        }
    }
}
