package org.jebol.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Opening, closing and reconfiguring a cipher port.
 *
 * <p>{@code Crypt_Actor} in {@code p-crypt.c}, and the {@code crypt} scheme's
 * own INIT in {@code sys-ports.reb}. The scheme reads the algorithm from any
 * of three places -- {@code algorithm:} in a block, the host of
 * {@code crypt://AES-128-CBC}, or the target of {@code crypt:chacha20} -- and
 * the direction from the url's fragment or a {@code direction:} field. So
 * {@code open crypt://AES-128-CBC#decrypt} and a five-line block are the same
 * port.
 *
 * <p>The actor looks for its cipher before it looks at what was asked, which
 * is why every action on a closed port is refused and not only the ones that
 * would need the cipher. Asking whether it is open is refused too, which is
 * the surprising one and is pinned below.
 *
 * <p>Every expectation here was read off a real 3.22.5 before it was written.
 */
class CryptPortLifecycleFromTheSourceTest {

    private static final String KEY = "#{2B7E151628AED2A6ABF7158809CF4F3C}";

    private static final String VECTOR = "#{000102030405060708090A0B0C0D0E0F}";

    private static final String BLOCK = "#{6BC1BEE22E409F96E93D7E117393172A}";

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String errorIdFrom(String source) {
        return answerTo("e: try [" + source + "] either error? e [e/id] ['no-error]");
    }

    private static String errorArgumentFrom(String source) {
        return answerTo("e: try [" + source + "] either error? e [e/arg1] ['no-error]");
    }

    private static String openedOn(String algorithm) {
        return "p: open make port! [scheme: 'crypt algorithm: '" + algorithm + "]\n";
    }

    @Test
    @DisplayName("an algorithm is required, and it must be one this build serves")
    void anAlgorithmIsRequired() {
        assertThat(errorIdFrom("open make port! [scheme: 'crypt]"))
                .isEqualTo("invalid-spec");
        assertThat(errorIdFrom("""
                open make port! [scheme: 'crypt algorithm: 'NONSENSE-1]"""))
                .isEqualTo("invalid-spec");
        assertThat(errorArgumentFrom("""
                open make port! [scheme: 'crypt algorithm: 'NONSENSE-1]"""))
                .isEqualTo("NONSENSE-1");
    }

    @Test
    @DisplayName("the url form names the algorithm, and its fragment the direction")
    void theUrlFormNamesTheAlgorithmAndTheDirection() {
        assertThat(answerTo("""
                p: open crypt://AES-128-CBC#decrypt
                reduce [p/spec/algorithm p/spec/direction]"""))
                .isEqualTo("[AES-128-CBC decrypt]");
        assertThat(answerTo("""
                p: open crypt://AES-128-CBC
                p/spec/direction""")).isEqualTo("encrypt");
    }

    @Test
    @DisplayName("a direction that is neither encrypting nor decrypting is refused")
    void aDirectionThatIsNeitherIsRefused() {
        assertThat(errorIdFrom("open crypt://AES-128-CBC#sideways"))
                .isEqualTo("invalid-spec");
        assertThat(errorArgumentFrom("open crypt://AES-128-CBC#sideways"))
                .isEqualTo("\"sideways\"");
    }

    /**
     * The key and the starting vector are copied into the port and blanked in
     * the specification. A specification is an ordinary object a script can
     * read, mold or pass on, so a key that stayed in it would travel
     * everywhere the port did.
     */
    @Test
    @DisplayName("the key and the vector do not stay in the specification")
    void theKeyAndTheVectorDoNotStayInTheSpecification() {
        assertThat(answerTo("""
                p: open make port! [
                    scheme: 'crypt algorithm: 'AES-128-CBC
                    key: %s init-vector: %s
                ]
                reduce [p/spec/key p/spec/init-vector p/spec/ref]"""
                .formatted(KEY, VECTOR)))
                .isEqualTo("[_ _ crypt://AES-128-CBC#encrypt]");
    }

    @Test
    @DisplayName("and the port still has them, so the key went somewhere")
    void theKeyWentSomewhere() {
        assertThat(answerTo("""
                p: open make port! [
                    scheme: 'crypt algorithm: 'AES-128-CBC
                    key: %s init-vector: %s
                ]
                write p %s
                enbase/flat read p 16""".formatted(KEY, VECTOR, BLOCK)))
                .isEqualTo("\"7649ABAC8119B246CEE98E9B12E9197D\"");
    }

    /**
     * Opening checks the algorithm again, and not only the scheme's INIT does.
     *
     * <p>They run at different moments and a port can be changed in between:
     * INIT runs when the port is made, and the specification is an ordinary
     * object a script can write to afterwards. {@code Crypt_Open} reads
     * {@code spec/algorithm} for itself and traps {@code RE_INVALID_SPEC}
     * before it builds anything.
     *
     * <p>Without the second check the port opens with no cipher behind it and
     * the next write reaches for one that is not there. That was a
     * NullPointerException escaping to the top of the interpreter, which is
     * the one kind of failure a port must never produce.
     */
    @Test
    @DisplayName("opening checks the algorithm, not only making the port")
    void openingChecksTheAlgorithmAgain() {
        assertThat(errorIdFrom(openedOn("AES-128-ECB") + """
                close p
                p/spec/algorithm: 'NONSENSE-1
                open p
                write p #{00112233445566778899AABBCCDDEEFF}
                read p""")).isEqualTo("invalid-spec");
    }

    @Test
    @DisplayName("opening a port that is already open is refused")
    void openingAnOpenPortIsRefused() {
        assertThat(errorIdFrom(openedOn("AES-128-CBC") + "open p"))
                .isEqualTo("already-open");
        assertThat(errorArgumentFrom(openedOn("AES-128-CBC") + "open p"))
                .isEqualTo("crypt://AES-128-CBC#encrypt");
    }

    /**
     * Every one of them, because the actor looks for its cipher above the
     * switch on what was asked. Closing twice is refused for the same reason.
     */
    @Test
    @DisplayName("every action on a closed port is refused, and names the port")
    void everyActionOnAClosedPortIsRefused() {
        for (String action : new String[] {
                "write p #{00}", "read p", "update p", "take p",
                "modify p 'key " + KEY, "close p"}) {
            assertThat(errorIdFrom(openedOn("AES-128-CBC") + "close p\n" + action))
                    .as(action).isEqualTo("not-open");
        }
        assertThat(errorArgumentFrom(openedOn("AES-128-CBC") + "close p\nread p"))
                .isEqualTo("crypt://AES-128-CBC#encrypt");
    }

    /**
     * Asking whether a closed port is open raises rather than answering false,
     * which reads as wrong until you see where the check sits: the actor wants
     * its cipher before it reads the question, and a closed port has not got
     * one to answer with.
     */
    @Test
    @DisplayName("asking whether a closed port is open raises rather than answering")
    void askingWhetherAClosedPortIsOpenRaises() {
        assertThat(answerTo(openedOn("AES-128-CBC") + "open? p")).isEqualTo("#(true)");
        assertThat(errorIdFrom(openedOn("AES-128-CBC") + "close p\nopen? p"))
                .isEqualTo("not-open");
    }

    @Test
    @DisplayName("only bytes can be written to it")
    void onlyBytesCanBeWritten() {
        for (String data : new String[] {"{hello}", "5", "none", "[1 2 3]", "1.5"}) {
            assertThat(errorIdFrom(openedOn("AES-128-CBC") + "write p " + data))
                    .as(data).isEqualTo("feature-na");
        }
        assertThat(errorArgumentFrom(openedOn("AES-128-CBC") + "write p 5"))
                .isEqualTo("crypt://AES-128-CBC#encrypt");
    }

    /**
     * Only an unknown *word* is refused. A field that is not a word at all
     * answers the port and changes nothing, because the C breaks out of the
     * switch before it looks anything up: {@code if (!IS_WORD(arg1)) break}.
     */
    @Test
    @DisplayName("a field that is not a word changes nothing and is not refused")
    void aFieldThatIsNotAWordChangesNothing() {
        assertThat(answerTo(openedOn("AES-128-CBC") + "port? modify p none 1"))
                .isEqualTo("#(true)");
    }

    @Test
    @DisplayName("a field the port has not got is refused, and named")
    void aFieldThePortHasNotGotIsRefused() {
        assertThat(errorIdFrom(openedOn("AES-128-CBC") + "modify p 'nonsense 1"))
                .isEqualTo("invalid-arg");
        assertThat(errorArgumentFrom(openedOn("AES-128-CBC") + "modify p 'nonsense 1"))
                .isEqualTo("nonsense");
    }

    /**
     * A value the field cannot hold answers false rather than raising, which
     * is what lets a script offer a cipher and fall back when the build has
     * not got it.
     */
    @Test
    @DisplayName("a value a field cannot hold answers false")
    void aValueAFieldCannotHoldAnswersFalse() {
        assertThat(answerTo(openedOn("AES-128-CBC") + "modify p 'direction 'sideways"))
                .isEqualTo("#(false)");
        assertThat(answerTo(openedOn("AES-128-CBC") + "modify p 'algorithm 'NOPE"))
                .isEqualTo("#(false)");
        assertThat(answerTo(openedOn("AES-128-CBC") + """
                modify p 'init-vector {abc}""")).isEqualTo("#(false)");
    }

    /**
     * The key is the one field that takes text as well as bytes, because its
     * arm accepts a string where the vector's arm accepts only a binary. So
     * sixteen characters of ASCII are a key and sixteen characters of ASCII
     * are not a starting vector.
     */
    @Test
    @DisplayName("a key may be text where a vector may not")
    void aKeyMayBeTextWhereAVectorMayNot() {
        assertThat(answerTo(openedOn("AES-128-ECB") + """
                modify p 'key {abcdefghijklmnop}
                write p %s
                enbase/flat read p 16""".formatted(BLOCK)))
                .isEqualTo("\"69E36F18733C673656624D6D8F18389B\"");
    }

    @Test
    @DisplayName("IV is the other spelling of INIT-VECTOR")
    void ivIsTheOtherSpellingOfInitVector() {
        assertThat(answerTo(openedOn("AES-128-CBC") + """
                modify p 'key %s
                modify p 'iv %s
                write p %s
                enbase/flat read p 16""".formatted(KEY, VECTOR, BLOCK)))
                .isEqualTo("\"7649ABAC8119B246CEE98E9B12E9197D\"");
    }

    /**
     * One port can run several ciphers in turn. Changing the algorithm throws
     * the old cipher away, so the key has to be set again after it.
     */
    @Test
    @DisplayName("the algorithm can be changed on an open port")
    void theAlgorithmCanBeChangedOnAnOpenPort() {
        assertThat(answerTo(openedOn("AES-128-ECB") + """
                modify p 'key %s
                write p %s
                first-answer: enbase/flat read p 16
                modify p 'algorithm 'AES-256-ECB
                modify p 'key #{000102030405060708090A0B0C0D0E0F101112131415161718191A1B1C1D1E1F}
                write p #{00112233445566778899AABBCCDDEEFF}
                reduce [first-answer enbase/flat read p 16]""".formatted(KEY, BLOCK)))
                .isEqualTo("""
                        ["3AD77BB40D7A3660A89ECAF32466EF97" "8EA2B7CA516745BFEAFC49904B496089"]""");
    }
}
