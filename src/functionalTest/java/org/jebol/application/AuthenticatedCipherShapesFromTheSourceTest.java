package org.jebol.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AuthenticatedCipherShapesFromTheSourceTest {

    private static final String CIPHERS = """
            message: #{000102030405060708090A0B0C0D0E0F101112131415161718191A1B1C1D1E1F}
            cipher: func [name [string!] /local p] [
                p: open join crypt:// name
                modify p 'key head insert/dup copy #{} #{AB} either find name "128" [16] [32]
                modify p 'iv #{000102030405060708090A0B}
                modify p 'tag-length 16
                p
            ]
            """;

    private static final String CCM_OF_THE_WHOLE_MESSAGE =
            "#{108B226B3EA07E63606E3F8D44CC1CF6CF7BF7F1328D35D2EA02DD6E90C3ED9B"
                    + "43C24D870A0A2F4D977C03B9399D806C}";

    private static final String CCM_OF_THE_MESSAGE_AFTER_A_HEADER_OF_FOUR =
            "#{108B226B3EA07E63606E3F8D44CC1CF6CF7BF7F1328D35D2EA02DD6E90C3ED9B"
                    + "DBBC55998EA079A4674EB55D30A8EDF3}";

    private static String answerTo(String source) {
        String whole = CIPHERS + "print mold/flat do [\n" + source + "\n]";
        StringBuilder printed = new StringBuilder();
        Interpreter interpreter = AnInterpreter.createWithOutput(printed::append);
        interpreter.defineNewWordsIn(whole);
        interpreter.run(whole);
        return printed.toString().strip();
    }

    @Nested
    @DisplayName("counter with CBC-MAC is one whole message to a write")
    class CounterWithCbcMac {

        @Test
        @DisplayName("one write is the cipher text and its tag, all of it read at once")
        void sealsOneWrite() {
            assertThat(answerTo("""
                    p: cipher "aes-128-ccm" write p message reduce [read p take p]"""))
                    .isEqualTo("[" + CCM_OF_THE_WHOLE_MESSAGE + " _]");
        }

        @Test
        @DisplayName("two writes are two messages, each with its own tag")
        void sealsTwoWritesApart() {
            assertThat(answerTo("""
                    p: cipher "aes-128-ccm"
                    write p copy/part message 16
                    write p skip message 16
                    reduce [read p take p]"""))
                    .isEqualTo("[#{108B226B3EA07E63606E3F8D44CC1CF6BC6E11519ABB424646DFF058D872AEF0"
                            + "D5968693EEF23F30934BB8CA7144B9FA7D93AB28FF22C2396D817352D9433A02} _]");
        }

        @Test
        @DisplayName("the second message starts its counter from nought, as the tagless one shows")
        void startsTheSecondFromNought() {
            assertThat(answerTo("""
                    p: cipher "aes-128-ccm"
                    modify p 'tag-length 0
                    write p copy/part message 16
                    write p skip message 16
                    read p"""))
                    .isEqualTo("#{108B226B3EA07E63606E3F8D44CC1CF6D5968693EEF23F30934BB8CA7144B9FA}");
        }

        @Test
        @DisplayName("reading between two writes does not start the cipher again")
        void readingStartsNothing() {
            assertThat(answerTo("""
                    p: cipher "aes-128-ccm" write p message read p write p message read p"""))
                    .isEqualTo("#{C5869683FEE22F20835BA8DA6154A9EA95634B5E04E975A3D7343262AD0676F5"
                            + "5220C2C80B2582390F02EF0806A26B7D}");
        }

        @Test
        @DisplayName("an empty write adds nothing")
        void addsNothingForAnEmptyWrite() {
            assertThat(answerTo("""
                    p: cipher "aes-128-ccm" write p message write p #{} read p"""))
                    .isEqualTo(CCM_OF_THE_WHOLE_MESSAGE);
        }

        @Test
        @DisplayName("a header on the front of the write is taken from it")
        void takesTheHeaderFromTheFront() {
            assertThat(answerTo("""
                    p: cipher "aes-128-ccm"
                    modify p 'aad-length 4
                    write p join #{01020304} message
                    reduce [read p take p]"""))
                    .isEqualTo("[" + CCM_OF_THE_MESSAGE_AFTER_A_HEADER_OF_FOUR + " _]");
        }

        @Test
        @DisplayName("a header written alone is a whole empty message, and the next write brings its own")
        void sealsAHeaderAloneAsAMessage() {
            assertThat(answerTo("""
                    p: cipher "aes-128-ccm"
                    modify p 'aad-length 4
                    write p #{01020304}
                    write p message
                    reduce [read p take p]"""))
                    .isEqualTo("[#{D8ED0C3B25034B71C772FA2FB27488A2C1829287F2EE232C875FACDE7D48B5F6"
                            + "91674F5A08E579AFD330366642951F92F66BCB6BC7AB7585AE6E53C2} _]");
        }

        @Test
        @DisplayName("a header longer than the message leaves only a tag")
        void sealsTheWholeWriteAsAHeader() {
            assertThat(answerTo("""
                    p: cipher "aes-128-ccm"
                    modify p 'aad-length 32
                    write p message
                    reduce [read p take p]"""))
                    .isEqualTo("[#{4C6BCBF5F05D0D3211D97F2514335D08} _]");
        }

        @Test
        @DisplayName("a write shorter than its header fails, and so does every write after it")
        void failsFromAShortWriteOn() {
            assertThat(answerTo("""
                    p: cipher "aes-128-ccm"
                    modify p 'aad-length 4
                    write p #{0102}
                    write p message
                    reduce [read p take p]""")).isEqualTo("[_ _]");
        }

        @Test
        @DisplayName("setting the vector again does not bring it back")
        void staysFailedWhenTheVectorIsSet() {
            assertThat(answerTo("""
                    p: cipher "aes-128-ccm"
                    modify p 'aad-length 4
                    write p #{0102}
                    modify p 'iv #{000102030405060708090A0B}
                    write p join #{01020304} message
                    reduce [read p take p]""")).isEqualTo("[_ _]");
        }

        @Test
        @DisplayName("setting the algorithm again does")
        void comesBackWhenTheAlgorithmIsSet() {
            assertThat(answerTo("""
                    p: cipher "aes-128-ccm"
                    modify p 'aad-length 4
                    write p #{0102}
                    modify p 'algorithm 'aes-128-ccm
                    write p join #{01020304} message
                    reduce [read p take p]"""))
                    .isEqualTo("[" + CCM_OF_THE_MESSAGE_AFTER_A_HEADER_OF_FOUR + " _]");
        }

        @Test
        @DisplayName("deciphering a message written in one piece gives it back")
        void deciphersOneWrite() {
            assertThat(answerTo("""
                    p: cipher "aes-128-ccm"
                    write p message
                    sealed: read p
                    modify p 'direction 'decrypt
                    write p sealed
                    message = read p""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("and two messages written apart are deciphered apart")
        void deciphersTwoWritesApart() {
            assertThat(answerTo("""
                    p: cipher "aes-128-ccm"
                    write p copy/part message 16
                    write p skip message 16
                    sealed: read p
                    modify p 'direction 'decrypt
                    write p copy/part sealed 32
                    write p skip sealed 32
                    message = read p""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("a tag that does not agree hides the text from READ, and TAKE hands it over")
        void hidesWhatFailedTheTagFromReadOnly() {
            assertThat(answerTo("""
                    p: cipher "aes-128-ccm"
                    write p message
                    sealed: read p
                    modify p 'direction 'decrypt
                    sealed/1: 0
                    write p sealed
                    reduce [read p take p]"""))
                    .isEqualTo("[_ #{100102030405060708090A0B0C0D0E0F101112131415161718191A1B1C1D1E1F}]");
        }

        @Test
        @DisplayName("and the next write that works lets READ see it again")
        void showsItAfterAWriteThatWorks() {
            assertThat(answerTo("""
                    p: cipher "aes-128-ccm"
                    write p message
                    sealed: read p
                    modify p 'direction 'decrypt
                    sealed/1: 0
                    write p sealed
                    write p #{}
                    read p"""))
                    .isEqualTo("#{100102030405060708090A0B0C0D0E0F101112131415161718191A1B1C1D1E1F}");
        }
    }

    @Nested
    @DisplayName("counting with Galois takes its header from the front of a write long enough to hold it")
    class CountingWithGalois {

        @Test
        @DisplayName("a header too short is dropped and the next write's front is taken instead")
        void takesTheHeaderFromTheNextWrite() {
            assertThat(answerTo("""
                    p: cipher "aes-128-gcm"
                    modify p 'aad-length 4
                    write p #{0102}
                    write p message
                    reduce [read p take p]"""))
                    .isEqualTo("[#{12CBA0F71F85E307A918EF4B038A5AD408676468D8B963F7B13058A1}"
                            + " #{002A640F1092454E157033CA682D69E6}]");
        }

        @Test
        @DisplayName("a header written alone and on the front of the message seal the same")
        void sealsTheSameEitherWay() {
            assertThat(answerTo("""
                    p: cipher "aes-128-gcm"
                    modify p 'aad-length 4
                    write p #{01020304}
                    write p message
                    alone: reduce [read p take p]
                    p: cipher "aes-128-gcm"
                    modify p 'aad-length 4
                    write p join #{01020304} message
                    alone = reduce [read p take p]""")).isEqualTo("#(true)");
        }
    }

    @Nested
    @DisplayName("ChaCha20-Poly1305 takes its first write whole as the header")
    class ChaChaWithPoly1305 {

        @Test
        @DisplayName("so a message written first is a header, and READ answers none")
        void readsNoneAfterOneWrite() {
            assertThat(answerTo("""
                    p: cipher "chacha20-poly1305" write p message reduce [read p take p]"""))
                    .isEqualTo("[_ #{A164AFF82D8204E7D73C7F491B01C0AF}]");
        }

        @Test
        @DisplayName("whatever the length to authenticate says")
        void ignoresTheLengthToAuthenticate() {
            assertThat(answerTo("""
                    p: cipher "chacha20-poly1305"
                    modify p 'aad-length 4
                    write p join #{01020304} message
                    reduce [read p take p]"""))
                    .isEqualTo("[_ #{FF01ED295BE9A71F2C398DA29CB420C0}]");
        }

        @Test
        @DisplayName("a header written as a write of its own is the shape that works")
        void sealsAfterAHeaderOfItsOwn() {
            assertThat(answerTo("""
                    p: cipher "chacha20-poly1305"
                    modify p 'aad-length 4
                    write p #{01020304}
                    write p message
                    reduce [read p take p]"""))
                    .isEqualTo("[#{4221F161CBFE0A1E985416113A0CB6E511C33366D1B6E00244F03DABC515B84B}"
                            + " #{CBA39D8CBC79DD6B8CE8797DE9BA08B4}]");
        }

        @Test
        @DisplayName("and it deciphers back, tag and all")
        void roundTrips() {
            assertThat(answerTo("""
                    p: cipher "chacha20-poly1305"
                    write p #{0000000000000001}
                    write p message
                    sealed: read p
                    tag: take p
                    modify p 'direction 'decrypt
                    write p #{0000000000000001}
                    write p sealed
                    reduce [message = read p tag = take p]""")).isEqualTo("[#(true) #(true)]");
        }
    }
}
