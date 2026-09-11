package org.jebol.domain.eval;

import org.jebol.domain.value.*;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * A port that enciphers what is written to it, {@code crypt://}.
 *
 * <p>{@code Crypt_Actor} in {@code p-crypt.c}. Bytes go in a write at a time
 * and come out when there are enough of them, because a block cipher cannot
 * answer until it has a whole block. What is left over waits inside the port
 * for the next write to complete it, so a caller reading a stream never has to
 * know the cipher's block size.
 *
 * <p>UPDATE is how a caller says there is no more input coming: it completes a
 * half-finished block by padding it with noughts. Noughts and not a counted
 * padding, so the padding cannot be told from data and nothing takes it off
 * again -- eight bytes through and back are those eight bytes followed by
 * eight noughts.
 *
 * <p>The fourteen ciphers here are the ones the JVM carries. REBOL's own
 * catalogue holds forty-two; Camellia, ARIA and counter-with-CBC-MAC are the
 * other twenty-eight and no JVM provider has them.
 */
final class CryptPort {

    static final String HANDLE_TYPE = "crypt";

    private CryptPort() {
    }

    /**
     * What each cipher is called here, how long its key is and how much of it
     * a write has to gather before anything comes out.
     *
     * <p>A block of nought means a stream: everything written is transformed
     * at once and nothing is ever held back. ChaCha20 is the odd one, a stream
     * cipher REBOL gives a block of sixteen anyway, so four bytes written and
     * taken come back as sixteen.
     */
    private record Cipherworks(String transformation, String keyAlgorithm,
            int keyOctets, int blockOctets, Mode mode) {
    }

    private enum Mode {
        CODEBOOK, CHAINED, COUNTER_WITH_GALOIS, COUNTER_WITH_CBC_MAC, STREAM
    }

    /** Whether a mode gathers everything and answers a tag beside the rest. */
    private static boolean authenticates(Mode mode) {
        return mode == Mode.COUNTER_WITH_GALOIS
                || mode == Mode.COUNTER_WITH_CBC_MAC;
    }

    private static final Map<String, Cipherworks> SERVED = Map.ofEntries(
            Map.entry("aes-128-ecb", new Cipherworks(
                    "AES/ECB/NoPadding", "AES", 16, 16, Mode.CODEBOOK)),
            Map.entry("aes-192-ecb", new Cipherworks(
                    "AES/ECB/NoPadding", "AES", 24, 16, Mode.CODEBOOK)),
            Map.entry("aes-256-ecb", new Cipherworks(
                    "AES/ECB/NoPadding", "AES", 32, 16, Mode.CODEBOOK)),
            Map.entry("aes-128-cbc", new Cipherworks(
                    "AES/CBC/NoPadding", "AES", 16, 16, Mode.CHAINED)),
            Map.entry("aes-192-cbc", new Cipherworks(
                    "AES/CBC/NoPadding", "AES", 24, 16, Mode.CHAINED)),
            Map.entry("aes-256-cbc", new Cipherworks(
                    "AES/CBC/NoPadding", "AES", 32, 16, Mode.CHAINED)),
            Map.entry("aes-128-ccm", new Cipherworks(
                    "AES/ECB/NoPadding", "AES", 16, 0, Mode.COUNTER_WITH_CBC_MAC)),
            Map.entry("aes-192-ccm", new Cipherworks(
                    "AES/ECB/NoPadding", "AES", 24, 0, Mode.COUNTER_WITH_CBC_MAC)),
            Map.entry("aes-256-ccm", new Cipherworks(
                    "AES/ECB/NoPadding", "AES", 32, 0, Mode.COUNTER_WITH_CBC_MAC)),
            Map.entry("aes-128-gcm", new Cipherworks(
                    "AES/GCM/NoPadding", "AES", 16, 0, Mode.COUNTER_WITH_GALOIS)),
            Map.entry("aes-192-gcm", new Cipherworks(
                    "AES/GCM/NoPadding", "AES", 24, 0, Mode.COUNTER_WITH_GALOIS)),
            Map.entry("aes-256-gcm", new Cipherworks(
                    "AES/GCM/NoPadding", "AES", 32, 0, Mode.COUNTER_WITH_GALOIS)),
            Map.entry("chacha20", new Cipherworks(
                    "ChaCha20", "ChaCha20", 32, 16, Mode.STREAM)),
            Map.entry("des_ecb", new Cipherworks(
                    "DES/ECB/NoPadding", "DES", 8, 8, Mode.CODEBOOK)),
            Map.entry("des3_ecb", new Cipherworks(
                    "DESede/ECB/NoPadding", "DESede", 24, 8, Mode.CODEBOOK)),
            Map.entry("des_cbc", new Cipherworks(
                    "DES/CBC/NoPadding", "DES", 8, 8, Mode.CHAINED)),
            Map.entry("des3_cbc", new Cipherworks(
                    "DESede/CBC/NoPadding", "DESede", 24, 8, Mode.CHAINED)));

    /**
     * The catalogue, in the order a real 3.22.5 lists the same names.
     *
     * <p>Order is not decoration. {@code codec-safe.reb} falls back to
     * {@code first system/catalog/ciphers} when none of the four it prefers is
     * there, so the first name is a choice and not an accident.
     */
    private static final List<String> IN_CATALOGUE_ORDER = List.of(
            "aes-128-ecb", "aes-192-ecb", "aes-256-ecb",
            "aes-128-cbc", "aes-192-cbc", "aes-256-cbc",
            "aes-128-ccm", "aes-192-ccm", "aes-256-ccm",
            "aes-128-gcm", "aes-192-gcm", "aes-256-gcm",
            "chacha20", "des_ecb", "des3_ecb", "des_cbc", "des3_cbc");

    static List<Value> catalogue() {
        return IN_CATALOGUE_ORDER.stream().<Value>map(WordValue::of).toList();
    }

    static boolean serves(String algorithm) {
        return SERVED.containsKey(algorithm);
    }

    /**
     * How wide a starting vector may be before it is cut short.
     *
     * <p>{@code MBEDTLS_MAX_IV_LENGTH}. Sixteen rather than a block, because
     * ChaCha20 reads a twelve byte nonce and a four byte counter out of the
     * same field.
     */
    private static final int WIDEST_VECTOR = 16;

    /** Where ChaCha20's counter starts inside the vector. */
    private static final int CHACHA20_COUNTER_AT = 12;

    /**
     * A cipher in progress: what it was told, and what it has not finished
     * with.
     *
     * <p>The four fields a caller may set all live here rather than in the
     * port's specification, because opening takes the key out of the
     * specification and blanks it. A specification is an ordinary object a
     * script can read and pass on, and a key that stayed in it would travel
     * everywhere the port did.
     */
    private static final class Working {

        private String algorithm;
        private boolean decrypting;
        private byte[] key = new byte[0];
        private byte[] vector = new byte[0];
        private int tagOctets;
        private int toAuthenticateOctets;

        private Cipher running;
        private byte[] heldBack = new byte[0];
        private byte[] ready = new byte[0];
        private boolean somethingIsReady;
        private boolean needsStarting = true;
        private byte[] authenticated = new byte[0];
        private byte[] gatheredForGalois = new byte[0];
        private int handedOut;

        /**
         * Whether the bytes to authenticate have been taken.
         *
         * <p>They come off the front of one write and not several:
         * {@code if (ctx->state == CRYPT_PORT_NO_DATA && ctx->aad_len)} acts
         * only while nothing has been enciphered yet, and a write shorter than
         * the header is discarded whole with no error a caller can see. That
         * loses data, and it is what a real 3.22.5 does.
         */
        private boolean headerTaken;

        /**
         * Whether the cipher refused to start or to run, which is remembered
         * rather than raised.
         *
         * <p>{@code ctx->error}, which {@code A_READ} checks before it answers
         * anything: {@code if (ctx->state != CRYPT_PORT_HAS_DATA ||
         * ctx->error) return R_NONE}. A caller reading a stream is already
         * looping until something comes out, and a cipher that cannot run is a
         * stream that never produces.
         */
        private boolean wouldNotRun;

        private Cipherworks works() {
            return SERVED.get(algorithm);
        }
    }

    static void start(PortValue port, String algorithm, boolean decrypting,
            byte[] key, byte[] vector) {

        Working working = new Working();
        working.algorithm = algorithm;
        working.decrypting = decrypting;
        working.key = key;
        working.vector = noWiderThanAVector(vector);
        port.setField("extra", HandleValue.context(HANDLE_TYPE,
                System.identityHashCode(port.context()),
                JavaObjectValue.of(working)));
    }

    static void stop(PortValue port) {
        port.setField("extra", NoneValue.none());
    }

    private static Working inProgress(PortValue port) {
        if (port.fieldNamed("extra") instanceof HandleValue held
                && HANDLE_TYPE.equals(held.typeName())
                && held.payload() instanceof JavaObjectValue wrapped
                && wrapped.held().orElse(null) instanceof Working working) {
            return working;
        }
        return null;
    }

    static boolean isWorking(PortValue port) {
        return inProgress(port) != null;
    }

    /**
     * Sets one field and makes the next write start the cipher again.
     *
     * <p>Starting again is the point of it. A chaining mode carries state from
     * block to block, and that state has to be thrown away between messages or
     * the second message decrypts to nothing. So REBOL's own test resets the
     * vector between messages and says why: "must reset IV, because it was
     * changed internally".
     *
     * <p>A value the field cannot hold answers false and changes nothing,
     * which is what lets a script offer a cipher and fall back when the build
     * has not got it.
     */
    static Value modify(PortValue port, String field, Value given) {
        Working working = inProgress(port);
        if (working == null) {
            return LogicValue.of(false);
        }
        boolean accepted = switch (field) {
            case "algorithm" -> anAlgorithmWasSet(working, given);
            case "direction" -> aDirectionWasSet(working, given);
            case "key" -> aRunOfOctetsWasSet(given, octets -> working.key = octets);
            case "iv", "init-vector" -> aVectorWasSet(working, given);
            case "tag-length" -> aCountWasSet(given, count -> working.tagOctets = count);
            case "aad-length" -> aCountWasSet(given,
                    count -> working.toAuthenticateOctets = count);
            default -> throw Raised.of(EvaluationFailure.INVALID_ARG,
                    WordValue.of(field));
        };
        if (!accepted) {
            return LogicValue.of(false);
        }
        if (!TOLD_WITHOUT_STARTING_AGAIN.contains(field)) {
            working.needsStarting = true;
        }
        return port;
    }

    /**
     * The two fields that do not start the cipher again.
     *
     * <p>The C assigns {@code ctx->tag_len} and {@code ctx->aad_len} and
     * restarts nothing, where every other field sets the state back to needing
     * initialisation. So a tag length set between two writes keeps both blocks
     * and a starting vector set between them throws the first away.
     */
    private static final java.util.Set<String> TOLD_WITHOUT_STARTING_AGAIN =
            java.util.Set.of("tag-length", "aad-length");

    private static boolean anAlgorithmWasSet(Working working, Value given) {
        if (!(given instanceof WordValue named) || !serves(named.canonical())) {
            return false;
        }
        working.algorithm = named.canonical();
        return true;
    }

    private static boolean aDirectionWasSet(Working working, Value given) {
        if (!(given instanceof WordValue named)) {
            return false;
        }
        if (named.canonical().equals("encrypt")) {
            working.decrypting = false;
            return true;
        }
        if (named.canonical().equals("decrypt")) {
            working.decrypting = true;
            return true;
        }
        return false;
    }

    /**
     * A key may be text where a starting vector may not, because the key's own
     * arm in the C accepts a string and the vector's accepts only a binary.
     */
    private static boolean aRunOfOctetsWasSet(
            Value given, java.util.function.Consumer<byte[]> into) {

        if (given instanceof NoneValue) {
            into.accept(new byte[0]);
            return true;
        }
        if (given instanceof BinaryValue octets) {
            into.accept(octets.octetsFromHere());
            return true;
        }
        if (given instanceof StringValue text) {
            into.accept(text.text().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return true;
        }
        return false;
    }

    private static boolean aVectorWasSet(Working working, Value given) {
        if (given instanceof NoneValue) {
            working.vector = new byte[0];
            return true;
        }
        if (given instanceof BinaryValue octets) {
            working.vector = noWiderThanAVector(octets.octetsFromHere());
            return true;
        }
        return false;
    }

    private static boolean aCountWasSet(
            Value given, java.util.function.IntConsumer into) {

        if (!(given instanceof IntegerValue count)) {
            return false;
        }
        into.accept((int) count.magnitude());
        return true;
    }

    /**
     * Feeds bytes in, transforming every whole block and holding the rest.
     *
     * <p>Galois counter mode is gathered instead of transformed as it arrives,
     * because its tag is computed over everything at once and the JVM will not
     * hand back a tag until it has seen the end. So a message in five writes
     * is five appends and one transformation when the answer is asked for.
     */
    static void write(PortValue port, byte[] octets) {
        Working working = inProgress(port);
        if (working == null) {
            return;
        }
        if (working.needsStarting) {
            startTheCipher(working);
        }
        if (working.wouldNotRun) {
            return;
        }
        if (authenticates(working.works().mode())) {
            gatherToAuthenticate(working, octets);
            return;
        }
        byte[] waiting = joined(working.heldBack, octets);
        int wholePart = howMuchOfItCanBeTransformed(working.works(), waiting.length);
        if (wholePart > 0) {
            addToWhatIsReady(working,
                    transformed(working, Arrays.copyOf(waiting, wholePart)));
        }
        working.heldBack = Arrays.copyOfRange(waiting, wholePart, waiting.length);
    }

    /**
     * How much of what has gathered can go through the cipher now.
     *
     * <p>A block cipher takes whole blocks and holds the rest. A stream cipher
     * with a block named anyway -- ChaCha20 is the one -- takes everything
     * once it has a block's worth and holds everything below that, because the
     * C returns early while {@code len < blk} and its ChaCha20 arm then
     * consumes the whole input rather than a whole number of blocks. So
     * twenty-one bytes come back as twenty-one, not as sixteen and a
     * remainder.
     */
    private static int howMuchOfItCanBeTransformed(Cipherworks works, int gathered) {
        int block = works.blockOctets();
        if (block == 0 || works.mode() == Mode.STREAM) {
            return gathered < block ? 0 : gathered;
        }
        return gathered - gathered % block;
    }

    /**
     * Completes a half-finished block with noughts, which is how a caller says
     * there is no more input coming.
     */
    static void update(PortValue port) {
        Working working = inProgress(port);
        if (working == null || working.wouldNotRun) {
            return;
        }
        if (working.works() != null && authenticates(working.works().mode())) {
            if (working.works().mode() == Mode.COUNTER_WITH_GALOIS) {
                finishGalois(working);
            }
            return;
        }
        if (working.heldBack.length == 0) {
            return;
        }
        addToWhatIsReady(working, transformed(working,
                Arrays.copyOf(working.heldBack, working.works().blockOctets())));
        working.heldBack = new byte[0];
    }

    /**
     * Everything that is ready, leaving the port empty.
     *
     * <p>The opposite of the checksum port, which answers the same digest
     * every time because a sum has no length. This one is a conveyor: what has
     * been read has left.
     */
    static Value read(PortValue port) {
        Working working = inProgress(port);
        if (working == null || working.wouldNotRun || !working.somethingIsReady) {
            return NoneValue.none();
        }
        Value answered = binaryOf(working.ready);
        working.ready = new byte[0];
        working.somethingIsReady = false;
        return answered;
    }

    private static void addToWhatIsReady(Working working, byte[] octets) {
        working.ready = joined(working.ready, octets);
        working.somethingIsReady = true;
    }

    private static void startTheCipher(Working working) {
        Cipherworks works = working.works();
        working.heldBack = new byte[0];
        working.ready = new byte[0];
        working.somethingIsReady = false;
        working.authenticated = new byte[0];
        working.gatheredForGalois = new byte[0];
        working.handedOut = 0;
        working.headerTaken = false;
        working.needsStarting = false;
        working.wouldNotRun = false;
        if (works.mode() == Mode.COUNTER_WITH_GALOIS) {
            working.wouldNotRun = working.vector.length == 0;
            return;
        }
        if (works.mode() == Mode.COUNTER_WITH_CBC_MAC) {
            return;
        }
        working.running = cipherFor(working, works.mode() == Mode.CHAINED
                || works.mode() == Mode.STREAM);
    }

    /**
     * A JVM cipher set up the way the port was told to.
     *
     * <p>ChaCha20 is the one that reads its parameters oddly: the counter is
     * the four bytes after the twelve byte nonce, most significant first, so
     * the port's single vector field carries both.
     */
    private static Cipher cipherFor(Working working, boolean needsAVector) {
        Cipherworks works = working.works();
        try {
            Cipher cipher = Cipher.getInstance(works.transformation());
            int direction = working.decrypting
                    ? Cipher.DECRYPT_MODE
                    : Cipher.ENCRYPT_MODE;
            SecretKeySpec key = new SecretKeySpec(
                    fittedTo(working.key, works.keyOctets()), works.keyAlgorithm());
            if (works.mode() == Mode.STREAM) {
                byte[] whole = fittedTo(working.vector, WIDEST_VECTOR);
                cipher.init(direction, key, new javax.crypto.spec.ChaCha20ParameterSpec(
                        Arrays.copyOf(whole, CHACHA20_COUNTER_AT),
                        counterWithin(whole)));
            } else if (needsAVector) {
                cipher.init(direction, key, new IvParameterSpec(
                        fittedTo(working.vector, works.blockOctets())));
            } else {
                cipher.init(direction, key);
            }
            return cipher;
        } catch (GeneralSecurityException refused) {
            throw Raised.of(EvaluationFailure.INVALID_SPEC,
                    WordValue.of(working.algorithm));
        }
    }

    private static int counterWithin(byte[] vector) {
        int counter = 0;
        for (int at = CHACHA20_COUNTER_AT; at < WIDEST_VECTOR; at++) {
            counter = counter << 8 | vector[at] & 0xFF;
        }
        return counter;
    }

    private static byte[] transformed(Working working, byte[] octets) {
        byte[] answered = working.running.update(octets);
        return answered == null ? new byte[0] : answered;
    }

    /**
     * Galois counter mode takes the bytes to authenticate first and the bytes
     * to encipher after, both through the same WRITE, told apart by a length
     * the caller set beforehand.
     */
    private static void gatherToAuthenticate(Working working, byte[] octets) {
        byte[] rest = octets;
        if (working.toAuthenticateOctets > 0 && !working.headerTaken) {
            if (octets.length < working.toAuthenticateOctets) {
                return;
            }
            working.authenticated =
                    Arrays.copyOf(octets, working.toAuthenticateOctets);
            working.headerTaken = true;
            rest = Arrays.copyOfRange(octets, working.toAuthenticateOctets,
                    octets.length);
        }
        working.gatheredForGalois = joined(working.gatheredForGalois, rest);
        if (working.works().mode() == Mode.COUNTER_WITH_CBC_MAC) {
            throughCounterWithCbcMac(working);
            return;
        }
        addToWhatIsReady(working, theOctetsNotHandedOutYet(working));
    }

    /**
     * Counter with CBC-MAC answers everything at the write, tag included,
     * because it computes its tag as it goes rather than at the end.
     *
     * <p>"The tag is computed immediatelly, so no need to finish CCM" --
     * {@code Crypt_Update} says exactly that and returns without doing
     * anything, which is why TAKE and READ answer the same thing here.
     *
     * <p>And it checks the tag while deciphering rather than handing it back,
     * so a message whose tag disagrees leaves the port with nothing at all
     * instead of plain text nobody vouched for.
     */
    private static void throughCounterWithCbcMac(Working working) {
        Cipherworks works = working.works();
        byte[] key = fittedTo(working.key, works.keyOctets());
        CounterWithCbcMac.Sealed answer = working.decrypting
                ? CounterWithCbcMac.deciphered(key, working.vector,
                        working.tagOctets, working.authenticated,
                        working.gatheredForGalois)
                : CounterWithCbcMac.enciphered(key, working.vector,
                        working.tagOctets, working.authenticated,
                        working.gatheredForGalois);
        if (!answer.worked()) {
            working.wouldNotRun = true;
            return;
        }
        working.ready = answer.octets();
        working.handedOut = answer.octets().length;
        working.somethingIsReady = true;
    }

    /**
     * The tag, cut to the length asked for, added to whatever is still waiting
     * rather than put in its place.
     *
     * <p>{@code Extend_Series(bin, ctx->tag_len)} and then
     * {@code SERIES_TAIL(bin) += ctx->tag_len} -- the C appends. Which only
     * shows when nothing read first: REBOL's own test reads and then takes, so
     * the cipher text has already left and the take answers a tag alone. A
     * port that replaced the buffer would pass that test and lose the message
     * for anybody who only took.
     *
     * <p>Where none was asked for the C computes nothing and the port is left
     * exactly as it was, so a TAKE straight after a READ answers none rather
     * than an empty run of bytes. That is the one place an empty append would
     * be wrong, which is why this guards itself rather than the adding doing
     * it: a WRITE in this mode marks the port as having data whether or not
     * any bytes came of it, and an empty message really does read as
     * {@code #\{}}.
     */
    private static void finishGalois(Working working) {
        if (working.tagOctets == NO_TAG_AT_ALL) {
            return;
        }
        if (!aTagOfThatLengthCanBeIssued(working.tagOctets)) {
            working.wouldNotRun = true;
            return;
        }
        addToWhatIsReady(working,
                Arrays.copyOf(theWholeTag(working), working.tagOctets));
    }

    /**
     * How short and how long a tag may be asked for.
     *
     * <p>{@code mbedtls_gcm_finish} refuses anything outside four to sixteen,
     * the failure is remembered, and reading answers nothing from then on. So
     * a tag of one or of seventeen is not a shorter or a longer tag -- it is a
     * port with no answer.
     */
    private static final int SHORTEST_TAG = 4;

    /** Asking for no tag at all, which is not a refusal and produces nothing. */
    private static final int NO_TAG_AT_ALL = 0;

    private static boolean aTagOfThatLengthCanBeIssued(int wanted) {
        return wanted >= SHORTEST_TAG && wanted <= A_WHOLE_TAG;
    }

    /**
     * The transformed bytes this write added, and not the ones before it.
     *
     * <p>An authenticated mode cannot transform a run of bytes and forget
     * them, because the tag is computed over all of them at once and the JVM
     * will not hand a tag back until it has seen the end. So everything is
     * kept and transformed again on each write, and a count of what has
     * already gone out says which part is new. The C keeps its own running
     * state instead and appends only the new bytes, which comes to the same
     * answer.
     */
    private static byte[] theOctetsNotHandedOutYet(Working working) {
        byte[] whole = theCipherText(working);
        byte[] fresh = Arrays.copyOfRange(whole,
                Math.min(working.handedOut, whole.length), whole.length);
        working.handedOut = whole.length;
        return fresh;
    }

    /** What READ answers: the transformed bytes, without the tag. */
    private static byte[] theCipherText(Working working) {
        return working.decrypting
                ? thePlainTextBehind(working)
                : withoutItsTag(galoisOver(working, working.gatheredForGalois, true));
    }

    /** What TAKE answers after READ: the tag, cut to the length asked for. */
    private static byte[] theWholeTag(Working working) {
        byte[] plain = working.decrypting
                ? thePlainTextBehind(working)
                : working.gatheredForGalois;
        byte[] both = galoisOver(working, plain, true);
        return Arrays.copyOfRange(both, both.length - A_WHOLE_TAG, both.length);
    }

    /**
     * The plain text under a cipher text, recovered without letting the JVM
     * check the tag.
     *
     * <p>The JVM will not hand a tag back while decrypting -- it compares the
     * tag itself and throws when it disagrees -- and this port hands the tag
     * to the caller to compare instead. Counter mode is its own inverse, so
     * enciphering the cipher text runs the same key stream over it and gives
     * the plain text back. The tag that comes with it is over the wrong bytes
     * and is thrown away; the one the caller is owed is computed from the
     * plain text afterwards.
     */
    private static byte[] thePlainTextBehind(Working working) {
        return withoutItsTag(
                galoisOver(working, working.gatheredForGalois, false));
    }

    private static byte[] withoutItsTag(byte[] both) {
        return Arrays.copyOf(both, both.length - A_WHOLE_TAG);
    }

    /**
     * How long a tag the JVM's own Galois mode will issue.
     *
     * <p>Always sixteen bytes, because its parameter object refuses anything
     * below twelve and REBOL's callers ask for four. A shorter tag is the
     * first bytes of the whole one, so computing the whole one and cutting it
     * down answers every length a caller can ask for.
     */
    private static final int A_WHOLE_TAG = 16;

    private static byte[] galoisOver(
            Working working, byte[] octets, boolean authenticating) {

        Cipherworks works = working.works();
        try {
            Cipher cipher = Cipher.getInstance(works.transformation());
            cipher.init(Cipher.ENCRYPT_MODE,
                    new SecretKeySpec(fittedTo(working.key, works.keyOctets()),
                            works.keyAlgorithm()),
                    new GCMParameterSpec(A_WHOLE_TAG * Byte.SIZE, working.vector));
            if (authenticating && working.authenticated.length > 0) {
                cipher.updateAAD(working.authenticated);
            }
            return cipher.doFinal(octets);
        } catch (GeneralSecurityException refused) {
            throw Raised.of(EvaluationFailure.INVALID_SPEC,
                    WordValue.of(working.algorithm));
        }
    }

    /**
     * A key or a vector at exactly the length the cipher wants: noughts added
     * where it is short, and the rest dropped where it is long.
     *
     * <p>Neither is refused, which is what {@code init_crypt_key} does --
     * clear the whole field, then copy in as much as fits.
     */
    private static byte[] fittedTo(byte[] given, int wanted) {
        return Arrays.copyOf(given, wanted);
    }

    private static byte[] noWiderThanAVector(byte[] given) {
        return given.length <= WIDEST_VECTOR
                ? given.clone()
                : Arrays.copyOf(given, WIDEST_VECTOR);
    }

    private static Value binaryOf(byte[] octets) {
        int[] widened = new int[octets.length];
        for (int at = 0; at < octets.length; at++) {
            widened[at] = octets[at] & 0xFF;
        }
        return BinaryValue.of(widened);
    }

    private static byte[] joined(byte[] first, byte[] second) {
        byte[] both = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, both, first.length, second.length);
        return both;
    }
}
