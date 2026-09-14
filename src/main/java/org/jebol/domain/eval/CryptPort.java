package org.jebol.domain.eval;

import org.jebol.domain.cipher.Aria;
import org.jebol.domain.cipher.BlockModes;
import org.jebol.domain.cipher.Camellia;
import org.jebol.domain.cipher.CounterWithCbcMac;
import org.jebol.domain.cipher.ChaChaWithPoly1305;
import org.jebol.domain.cipher.CounterWithGalois;
import org.jebol.domain.cipher.OneBlock;
import org.jebol.domain.value.*;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

final class CryptPort {

    static final String HANDLE_TYPE = "crypt";

    private CryptPort() {
    }

    private record Cipherworks(String transformation, String keyAlgorithm,
            int keyOctets, int blockOctets, Mode mode, Family family) {
    }

    private enum Family { AES, CAMELLIA, ARIA, OTHER }

    private enum Mode {
        CODEBOOK, CHAINED, COUNTER_WITH_GALOIS, COUNTER_WITH_CBC_MAC,
        CHACHA_WITH_POLY1305, STREAM
    }

    private static boolean gathersEverythingAndAnswersATagBesideIt(Mode mode) {
        return mode == Mode.COUNTER_WITH_GALOIS
                || mode == Mode.COUNTER_WITH_CBC_MAC
                || mode == Mode.CHACHA_WITH_POLY1305;
    }

    private static final Map<String, Cipherworks> SERVED = Map.ofEntries(
            Map.entry("aes-128-ecb", new Cipherworks(
                    "AES/ECB/NoPadding", "AES", 16, 16,
                    Mode.CODEBOOK, Family.AES)),
            Map.entry("aes-192-ecb", new Cipherworks(
                    "AES/ECB/NoPadding", "AES", 24, 16,
                    Mode.CODEBOOK, Family.AES)),
            Map.entry("aes-256-ecb", new Cipherworks(
                    "AES/ECB/NoPadding", "AES", 32, 16,
                    Mode.CODEBOOK, Family.AES)),
            Map.entry("aes-128-cbc", new Cipherworks(
                    "AES/CBC/NoPadding", "AES", 16, 16,
                    Mode.CHAINED, Family.AES)),
            Map.entry("aes-192-cbc", new Cipherworks(
                    "AES/CBC/NoPadding", "AES", 24, 16,
                    Mode.CHAINED, Family.AES)),
            Map.entry("aes-256-cbc", new Cipherworks(
                    "AES/CBC/NoPadding", "AES", 32, 16,
                    Mode.CHAINED, Family.AES)),
            Map.entry("aes-128-ccm", new Cipherworks(
                    "AES/ECB/NoPadding", "AES", 16, 0,
                    Mode.COUNTER_WITH_CBC_MAC, Family.AES)),
            Map.entry("aes-192-ccm", new Cipherworks(
                    "AES/ECB/NoPadding", "AES", 24, 0,
                    Mode.COUNTER_WITH_CBC_MAC, Family.AES)),
            Map.entry("aes-256-ccm", new Cipherworks(
                    "AES/ECB/NoPadding", "AES", 32, 0,
                    Mode.COUNTER_WITH_CBC_MAC, Family.AES)),
            Map.entry("aes-128-gcm", new Cipherworks(
                    "AES/GCM/NoPadding", "AES", 16, 0,
                    Mode.COUNTER_WITH_GALOIS, Family.AES)),
            Map.entry("aes-192-gcm", new Cipherworks(
                    "AES/GCM/NoPadding", "AES", 24, 0,
                    Mode.COUNTER_WITH_GALOIS, Family.AES)),
            Map.entry("aes-256-gcm", new Cipherworks(
                    "AES/GCM/NoPadding", "AES", 32, 0,
                    Mode.COUNTER_WITH_GALOIS, Family.AES)),
            Map.entry("camellia-128-ecb", new Cipherworks(
                    "", "", 16, 16,
                    Mode.CODEBOOK, Family.CAMELLIA)),
            Map.entry("camellia-192-ecb", new Cipherworks(
                    "", "", 24, 16,
                    Mode.CODEBOOK, Family.CAMELLIA)),
            Map.entry("camellia-256-ecb", new Cipherworks(
                    "", "", 32, 16,
                    Mode.CODEBOOK, Family.CAMELLIA)),
            Map.entry("camellia-128-cbc", new Cipherworks(
                    "", "", 16, 16,
                    Mode.CHAINED, Family.CAMELLIA)),
            Map.entry("camellia-192-cbc", new Cipherworks(
                    "", "", 24, 16,
                    Mode.CHAINED, Family.CAMELLIA)),
            Map.entry("camellia-256-cbc", new Cipherworks(
                    "", "", 32, 16,
                    Mode.CHAINED, Family.CAMELLIA)),
            Map.entry("camellia-128-ccm", new Cipherworks(
                    "", "", 16, 0,
                    Mode.COUNTER_WITH_CBC_MAC, Family.CAMELLIA)),
            Map.entry("camellia-192-ccm", new Cipherworks(
                    "", "", 24, 0,
                    Mode.COUNTER_WITH_CBC_MAC, Family.CAMELLIA)),
            Map.entry("camellia-256-ccm", new Cipherworks(
                    "", "", 32, 0,
                    Mode.COUNTER_WITH_CBC_MAC, Family.CAMELLIA)),
            Map.entry("camellia-128-gcm", new Cipherworks(
                    "", "", 16, 0,
                    Mode.COUNTER_WITH_GALOIS, Family.CAMELLIA)),
            Map.entry("camellia-192-gcm", new Cipherworks(
                    "", "", 24, 0,
                    Mode.COUNTER_WITH_GALOIS, Family.CAMELLIA)),
            Map.entry("camellia-256-gcm", new Cipherworks(
                    "", "", 32, 0,
                    Mode.COUNTER_WITH_GALOIS, Family.CAMELLIA)),
            Map.entry("aria-128-ecb", new Cipherworks(
                    "", "", 16, 16,
                    Mode.CODEBOOK, Family.ARIA)),
            Map.entry("aria-192-ecb", new Cipherworks(
                    "", "", 24, 16,
                    Mode.CODEBOOK, Family.ARIA)),
            Map.entry("aria-256-ecb", new Cipherworks(
                    "", "", 32, 16,
                    Mode.CODEBOOK, Family.ARIA)),
            Map.entry("aria-128-cbc", new Cipherworks(
                    "", "", 16, 16,
                    Mode.CHAINED, Family.ARIA)),
            Map.entry("aria-192-cbc", new Cipherworks(
                    "", "", 24, 16,
                    Mode.CHAINED, Family.ARIA)),
            Map.entry("aria-256-cbc", new Cipherworks(
                    "", "", 32, 16,
                    Mode.CHAINED, Family.ARIA)),
            Map.entry("aria-128-ccm", new Cipherworks(
                    "", "", 16, 0,
                    Mode.COUNTER_WITH_CBC_MAC, Family.ARIA)),
            Map.entry("aria-192-ccm", new Cipherworks(
                    "", "", 24, 0,
                    Mode.COUNTER_WITH_CBC_MAC, Family.ARIA)),
            Map.entry("aria-256-ccm", new Cipherworks(
                    "", "", 32, 0,
                    Mode.COUNTER_WITH_CBC_MAC, Family.ARIA)),
            Map.entry("aria-128-gcm", new Cipherworks(
                    "", "", 16, 0,
                    Mode.COUNTER_WITH_GALOIS, Family.ARIA)),
            Map.entry("aria-192-gcm", new Cipherworks(
                    "", "", 24, 0,
                    Mode.COUNTER_WITH_GALOIS, Family.ARIA)),
            Map.entry("aria-256-gcm", new Cipherworks(
                    "", "", 32, 0,
                    Mode.COUNTER_WITH_GALOIS, Family.ARIA)),
            Map.entry("chacha20", new Cipherworks(
                    "ChaCha20", "ChaCha20", 32, 16,
                    Mode.STREAM, Family.OTHER)),
            Map.entry("chacha20-poly1305", new Cipherworks(
                    "ChaCha20", "ChaCha20", 32, 0,
                    Mode.CHACHA_WITH_POLY1305, Family.OTHER)),
            Map.entry("des_ecb", new Cipherworks(
                    "DES/ECB/NoPadding", "DES", 8, 8,
                    Mode.CODEBOOK, Family.OTHER)),
            Map.entry("des3_ecb", new Cipherworks(
                    "DESede/ECB/NoPadding", "DESede", 24, 8,
                    Mode.CODEBOOK, Family.OTHER)),
            Map.entry("des_cbc", new Cipherworks(
                    "DES/CBC/NoPadding", "DES", 8, 8,
                    Mode.CHAINED, Family.OTHER)),
            Map.entry("des3_cbc", new Cipherworks(
                    "DESede/CBC/NoPadding", "DESede", 24, 8,
                    Mode.CHAINED, Family.OTHER)));

    private static final List<String> IN_CATALOGUE_ORDER = List.of(
            "aes-128-ecb", "aes-192-ecb", "aes-256-ecb",
            "aes-128-cbc", "aes-192-cbc", "aes-256-cbc",
            "aes-128-ccm", "aes-192-ccm", "aes-256-ccm",
            "aes-128-gcm", "aes-192-gcm", "aes-256-gcm",
            "camellia-128-ecb", "camellia-192-ecb", "camellia-256-ecb",
            "camellia-128-cbc", "camellia-192-cbc", "camellia-256-cbc",
            "camellia-128-ccm", "camellia-192-ccm", "camellia-256-ccm",
            "camellia-128-gcm", "camellia-192-gcm", "camellia-256-gcm",
            "aria-128-ecb", "aria-192-ecb", "aria-256-ecb",
            "aria-128-cbc", "aria-192-cbc", "aria-256-cbc",
            "aria-128-ccm", "aria-192-ccm", "aria-256-ccm",
            "aria-128-gcm", "aria-192-gcm", "aria-256-gcm",
            "chacha20", "chacha20-poly1305",
            "des_ecb", "des3_ecb", "des_cbc", "des3_cbc");

    static List<Value> catalogue() {
        return IN_CATALOGUE_ORDER.stream().<Value>map(WordValue::of).toList();
    }

    static boolean serves(String algorithm) {
        return SERVED.containsKey(algorithm);
    }

    private static final int WIDEST_VECTOR = 16;

    private static final int CHACHA20_COUNTER_AT = 12;

    private static final boolean COUNTING_IS_ITS_OWN_INVERSE = false;

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

        private byte[] chaining = new byte[0];

        private boolean theNextWriteIsAHeader = true;

        private boolean headerTaken;

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
        port.setField("state", HandleValue.context(HANDLE_TYPE,
                System.identityHashCode(port.context()),
                JavaObjectValue.of(working)));
    }

    static void stop(PortValue port) {
        port.setField("state", NoneValue.none());
    }

    private static Working inProgress(PortValue port) {
        if (port.fieldNamed("state") instanceof HandleValue held
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

    static Value modify(PortValue port, String field, Value given) {
        Working working = inProgress(port);
        if (working == null) {
            return LogicValue.of(false);
        }
        boolean accepted = switch (field) {
            case "algorithm" -> anAlgorithmWasSet(working, given);
            case "direction" -> aDirectionWasSet(working, given);
            case "key" -> aRunOfOctetsOrTextWasSet(given, octets -> working.key = octets);
            case "iv", "init-vector" -> aVectorOfOctetsOnlyWasSet(working, given);
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

    private static final java.util.Set<String> TOLD_WITHOUT_STARTING_AGAIN =
            java.util.Set.of("tag-length", "aad-length");

    private static boolean anAlgorithmWasSet(Working working, Value given) {
        if (!(given instanceof WordValue asked) || !serves(asked.canonical())) {
            return false;
        }
        working.algorithm = asked.canonical();
        return true;
    }

    private static boolean aDirectionWasSet(Working working, Value given) {
        if (!(given instanceof WordValue asked)) {
            return false;
        }
        if (asked.canonical().equals("encrypt")) {
            working.decrypting = false;
            return true;
        }
        if (asked.canonical().equals("decrypt")) {
            working.decrypting = true;
            return true;
        }
        return false;
    }

    private static boolean aRunOfOctetsOrTextWasSet(
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

    private static boolean aVectorOfOctetsOnlyWasSet(Working working, Value given) {
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
        if (gathersEverythingAndAnswersATagBesideIt(working.works().mode())) {
            if (octets.length == 0
                    && working.works().mode() == Mode.CHACHA_WITH_POLY1305) {
                return;
            }
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

    private static int howMuchOfItCanBeTransformed(Cipherworks works, int gathered) {
        int block = works.blockOctets();
        if (block == 0 || works.mode() == Mode.STREAM) {
            return allOfItOrNoneWhileItIsShortOfABlock(gathered, block);
        }
        return gathered - gathered % block;
    }

    private static int allOfItOrNoneWhileItIsShortOfABlock(int gathered, int block) {
        return gathered < block ? 0 : gathered;
    }

    static void update(PortValue port) {
        Working working = inProgress(port);
        if (working == null || working.wouldNotRun) {
            return;
        }
        if (working.works() != null
                && gathersEverythingAndAnswersATagBesideIt(working.works().mode())) {
            if (working.works().mode() == Mode.COUNTER_WITH_GALOIS) {
                finishGalois(working);
            }
            if (working.works().mode() == Mode.CHACHA_WITH_POLY1305) {
                addToWhatIsReady(working, theChaChaAnswer(working).tag());
            }
            return;
        }
        if (working.heldBack.length == 0) {
            return;
        }
        addToWhatIsReady(working,
                transformed(working, paddedWithNoughtsToAWholeBlock(working)));
        working.heldBack = new byte[0];
    }

    private static byte[] paddedWithNoughtsToAWholeBlock(Working working) {
        return Arrays.copyOf(working.heldBack, working.works().blockOctets());
    }

    static Value read(PortValue port) {
        Working working = inProgress(port);
        if (working == null || working.wouldNotRun || !working.somethingIsReady) {
            return NoneValue.none();
        }
        Value answered = binaryOf(working.ready);
        working.ready = new byte[0];
        working.somethingIsReady = false;
        if (working.works() != null
                && working.works().mode() == Mode.CHACHA_WITH_POLY1305) {
            working.theNextWriteIsAHeader = true;
        }
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
        working.theNextWriteIsAHeader = true;
        working.needsStarting = false;
        working.wouldNotRun = false;
        if (works.mode() == Mode.COUNTER_WITH_GALOIS) {
            working.wouldNotRun = working.vector.length == 0;
            return;
        }
        if (works.mode() == Mode.COUNTER_WITH_CBC_MAC
                || works.mode() == Mode.CHACHA_WITH_POLY1305) {
            return;
        }
        if (works.family() == Family.CAMELLIA || works.family() == Family.ARIA) {
            working.chaining = fittedTo(working.vector, Camellia.BLOCK);
            return;
        }
        working.running = cipherFor(working, works.mode() == Mode.CHAINED
                || works.mode() == Mode.STREAM);
    }

    private static OneBlock theBlockCipherBehind(
            Working working, boolean undoing) {

        Cipherworks works = working.works();
        byte[] key = fittedTo(working.key, works.keyOctets());
        if (works.family() == Family.CAMELLIA) {
            return Camellia.under(key, undoing);
        }
        if (works.family() == Family.ARIA) {
            return Aria.under(key, undoing);
        }
        try {
            return OneBlock.jvmAes(key);
        } catch (GeneralSecurityException refused) {
            throw Raised.of(EvaluationFailure.INVALID_SPEC,
                    WordValue.of(working.algorithm));
        }
    }

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
                        counterMostSignificantFirstAfterTheNonce(whole)));
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

    private static int counterMostSignificantFirstAfterTheNonce(byte[] vector) {
        int counter = 0;
        for (int at = CHACHA20_COUNTER_AT; at < WIDEST_VECTOR; at++) {
            counter = counter << 8 | vector[at] & 0xFF;
        }
        return counter;
    }

    private static byte[] transformed(Working working, byte[] octets) {
        if (working.works().family() == Family.CAMELLIA
                || working.works().family() == Family.ARIA) {
            return throughAWrittenOutCipher(working, octets);
        }
        byte[] answered = working.running.update(octets);
        return answered == null ? new byte[0] : answered;
    }

    private static byte[] throughAWrittenOutCipher(
            Working working, byte[] octets) {
        OneBlock cipher = theBlockCipherBehind(working, working.decrypting);
        if (working.works().mode() == Mode.CODEBOOK) {
            return BlockModes.codebook(cipher, octets);
        }
        return working.decrypting
                ? BlockModes.chainingBackwards(cipher, working.chaining, octets)
                : BlockModes.chainingForwards(cipher, working.chaining, octets);
    }

    private static void gatherToAuthenticate(Working working, byte[] octets) {
        if (working.works().mode() == Mode.CHACHA_WITH_POLY1305) {
            gatherForChaCha(working, octets);
            return;
        }
        byte[] rest = octets;
        if (working.toAuthenticateOctets > 0 && !working.headerTaken) {
            if (thisWriteIsTooShortToHoldTheHeaderAndIsDiscardedWhole(
                    working, octets)) {
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

    private static boolean thisWriteIsTooShortToHoldTheHeaderAndIsDiscardedWhole(
            Working working, byte[] octets) {
        return octets.length < working.toAuthenticateOctets;
    }

    private static void gatherForChaCha(Working working, byte[] octets) {
        if (working.theNextWriteIsAHeader) {
            working.authenticated = octets;
            working.chaining = ChaChaWithPoly1305.nonceFrom(
                    fittedTo(working.vector, ChaChaWithPoly1305.NONCE), octets);
            working.theNextWriteIsAHeader = false;
            return;
        }
        working.gatheredForGalois = joined(working.gatheredForGalois, octets);
        addToWhatIsReady(working, theOctetsNotHandedOutYet(working));
    }

    private static void throughCounterWithCbcMac(Working working) {
        OneBlock cipher =
                theBlockCipherBehind(working, COUNTING_IS_ITS_OWN_INVERSE);
        CounterWithCbcMac.Sealed answer = working.decrypting
                ? CounterWithCbcMac.deciphered(cipher, working.vector,
                        working.tagOctets, working.authenticated,
                        working.gatheredForGalois)
                : CounterWithCbcMac.enciphered(cipher, working.vector,
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

    private static final int SHORTEST_TAG = 4;

    private static final int NO_TAG_AT_ALL = 0;

    private static boolean aTagOfThatLengthCanBeIssued(int wanted) {
        return wanted >= SHORTEST_TAG && wanted <= CounterWithGalois.WHOLE_TAG;
    }

    private static byte[] theOctetsNotHandedOutYet(Working working) {
        byte[] whole = theCipherTextWithoutTheTag(working);
        byte[] fresh = Arrays.copyOfRange(whole,
                Math.min(working.handedOut, whole.length), whole.length);
        working.handedOut = whole.length;
        return fresh;
    }

    private static byte[] theCipherTextWithoutTheTag(Working working) {
        if (working.works().mode() == Mode.CHACHA_WITH_POLY1305) {
            return theChaChaAnswer(working).octets();
        }
        return theGaloisAnswer(working).octets();
    }

    private static ChaChaWithPoly1305.Sealed theChaChaAnswer(Working working) {
        return ChaChaWithPoly1305.through(
                fittedTo(working.key, working.works().keyOctets()),
                working.chaining, working.authenticated,
                working.gatheredForGalois, working.decrypting);
    }

    private static byte[] theWholeTag(Working working) {
        return theGaloisAnswer(working).tag();
    }

    private static CounterWithGalois.Sealed theGaloisAnswer(Working working) {
        return CounterWithGalois.through(
                theBlockCipherBehind(working, COUNTING_IS_ITS_OWN_INVERSE),
                working.vector,
                working.authenticated, working.gatheredForGalois,
                working.decrypting);
    }

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
