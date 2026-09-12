package org.jebol.domain.eval;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

class XxHash3FromTheSourceTest {

    private static byte[] bytesOf(int length) {
        byte[] made = new byte[length];
        for (int at = 0; at < length; at++) {
            made[at] = (byte) ((at * 167 + 13) % 256);
        }
        return made;
    }

    private static String hexOf(byte[] digest) {
        return HexFormat.of().withUpperCase().formatHex(digest);
    }

    @Test
    @DisplayName("the generated input is the input the reference was given")
    void theGeneratedInputIsWhatTheReferenceWasGiven() {
        assertThat(hexOf(bytesOf(5))).isEqualTo("0DB45B02A9");
        assertThat(bytesOf(1024)).hasSize(1024);
        assertThat(bytesOf(0)).isEmpty();
    }

    @Nested
    @DisplayName("every length at which XXH3 changes what it does")
    class TheBoundaries {

        @ParameterizedTest(name = "{0} bytes")
        @CsvSource({
                "0,    2D06800538D394C2, 99AA06D3014798D86001C324468D497F",
                "1,    8A21D78B1538B1C0, 79D2C79E874F72CD8A21D78B1538B1C0",
                "2,    A020A917C68E5888, 424E3BC292E2FAC3A020A917C68E5888",
                "3,    5F1FA6D2A3AA5A3B, 27056158676515D75F1FA6D2A3AA5A3B",
                "4,    A8A9B82C81542A43, 18BEC2DF875B7D35CB8C01D87EE4BB85",
                "5,    2F1E508DC78686C2, D9326D709245DF77A3CF2BB12412B2D3",
                "8,    67B8F67A80D308A6, 5EE08611ACAF82709987B0F6A787FCFF",
                "9,    7DF166798FE37670, D7B1B30E3925AEA8D5D74FB50DABF9A3",
                "12,   2EE9684733877373, 3A1B0D748E1ED369DC47BC13A87C62FD",
                "16,   C0967FEE676A5837, AEFC4C7B6B2355E8FF22986FB4ABA31B",
                "17,   BC307578D06E9D93, F600C8A98B27CDA20DFDB8ED0DE7262A",
                "32,   FFDAE7523DD0DAEC, 29F5D000B9C3A663587150F0FA952681",
                "33,   27B514591EF37D2D, 76D2A9372BD473AC90C6E66DB31D5277",
                "64,   2C6E0B294ABFC3F0, 8DCC0937DB38819C7720BC9B713F9574",
                "65,   31D8DB1918167D5C, 1D2E8D50CCD79C4C5591647F08EAFA8E",
                "96,   1EBAB520F4C1E5AF, D29F6391C4AB87AD63E41FE35C2097B1",
                "97,   4A2CFEE5178BDE3C, D2B8A6F9A9B2D48D486E1E7040444162",
                "128,  A45617BFE9BB88D6, 9B0E839B5061F424572DD69BD15CDB73",
                "129,  4CA31F6B2CFD9A3E, 485B2B823D90AFA95A753049F4B49D33",
                "160,  2FB4144DD1B5A99E, B797836B62BAE5972594C96FB22E2625",
                "161,  CD8B2CFA07003298, 258D6A7154D973B931DBFE7A6FFC75F7",
                "192,  DD6770141A9D3A2F, C6BA1B58B1EBEFB0234926E8547E3A7D",
                "240,  2817ED3CDF9547E7, 5D572FC255E19C13DFD310866E8AD632",
                "241,  02F838DD48200EE8, 7FB50EBCE4E0117802F838DD48200EE8",
                "256,  C67D143EC3572269, 1A9EA0BF440F5EB2C67D143EC3572269",
                "512,  81D2BB89DF27623E, 21ED11918D28440981D2BB89DF27623E",
                "1023, 470376F374F704CC, 87F529AA0BBFEED0470376F374F704CC",
                "1024, 4ECDE09865C37511, 5D7A8FDAB30B2C4F4ECDE09865C37511",
                "1025, A08B2694BF52957E, D7C0800F443749BCA08B2694BF52957E",
                "2048, 1D0F40A0FF717F3E, 9E56666710A26BD81D0F40A0FF717F3E",
                "2049, F24D7378A4AFEFFC, 21FA55EF02999041F24D7378A4AFEFFC",
                "4096, 5B1812436F21D06D, C86B5857C54D0BE95B1812436F21D06D",
        })
        @DisplayName("answers what a real 3.22.5 answers")
        void itAnswersWhatTheReferenceAnswers(
                int length, String expected64, String expected128) {

            byte[] subject = bytesOf(length);

            assertThat(hexOf(XxHash3.of64MostSignificantByteFirst(subject)))
                    .as("xxh3 of %d bytes", length)
                    .isEqualTo(expected64);
            assertThat(hexOf(XxHash3.of128MostSignificantByteFirst(subject)))
                    .as("xxh128 of %d bytes", length)
                    .isEqualTo(expected128);
        }
    }

    @Nested
    @DisplayName("the published vectors, which the reference also agrees with")
    class ThePublishedVectors {

        @Test
        @DisplayName("the sentence every hash is demonstrated on")
        void theSentenceEveryHashIsDemonstratedOn() {
            byte[] sentence =
                    "The quick brown fox jumps over the lazy dog".getBytes(
                            java.nio.charset.StandardCharsets.UTF_8);

            assertThat(hexOf(XxHash3.of64MostSignificantByteFirst(sentence)))
                    .isEqualTo("CE7D19A5418FB365");
            assertThat(hexOf(XxHash3.of128MostSignificantByteFirst(sentence)))
                    .isEqualTo("DDD650205CA3E7FA24A1CC2E3A8A7651");
        }

        @Test
        @DisplayName("and the digest widths are eight and sixteen bytes")
        void theDigestWidthsAreEightAndSixteen() {
            assertThat(XxHash3.of64MostSignificantByteFirst(bytesOf(300))).hasSize(8);
            assertThat(XxHash3.of128MostSignificantByteFirst(bytesOf(300))).hasSize(16);
        }
    }
}
