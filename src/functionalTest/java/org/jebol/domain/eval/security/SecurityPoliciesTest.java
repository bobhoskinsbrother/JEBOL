package org.jebol.domain.eval.security;

import org.jebol.application.Interpreter;
import org.jebol.domain.value.ErrorValue;
import org.jebol.domain.value.FileValue;
import org.jebol.domain.value.NoneValue;
import org.jebol.domain.value.Raised;
import org.jebol.domain.value.UrlValue;
import org.jebol.domain.value.Value;
import org.jebol.domain.value.WordValue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Arrays;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityPoliciesTest {

    private final Interpreter interpreter = Interpreter.create();

    private SecurityPolicies policiesFrom(String fields) {
        String source = "construct [" + fields + "]";
        return new SecurityPolicies(interpreter.run(source).value());
    }

    private Optional<Value> aFile(String path) {
        return Optional.of(FileValue.of(path));
    }

    private record Refusal(String id, Value arg1) {
    }

    private Refusal refusalOf(Runnable asking) {
        try {
            asking.run();
        } catch (Raised raised) {
            ErrorValue error = raised.error();
            return new Refusal(error.errorId(), error.subject().orElseGet(NoneValue::none));
        }
        throw new AssertionError("nothing was refused");
    }

    @Nested
    @DisplayName("a class holding one tuple")
    class OneTuple {

        @Test
        @DisplayName("0.0.0 allows every access")
        void allowsEverything() {
            SecurityPolicies policies = policiesFrom("file: 0.0.0");

            for (PolicyAccess access : PolicyAccess.values()) {
                assertThat(policies.findTheLevelFor(PolicyClass.FILE, access, aFile("/x"))).isEqualTo(PolicyLevel.ALLOW);
            }
        }

        @Test
        @DisplayName("each byte is one access, in the order read, write, execute")
        void eachByteIsOneAccess() {
            SecurityPolicies policies = policiesFrom("call: 1.2.3");

            assertThat(policies.findTheLevelFor(PolicyClass.CALL, PolicyAccess.READ, Optional.empty()))
                    .isEqualTo(PolicyLevel.ASK);
            assertThat(policies.findTheLevelFor(PolicyClass.CALL, PolicyAccess.WRITE, Optional.empty()))
                    .isEqualTo(PolicyLevel.THROW);
            assertThat(policies.findTheLevelFor(PolicyClass.CALL, PolicyAccess.EXECUTE, Optional.empty()))
                    .isEqualTo(PolicyLevel.QUIT);
        }

        @ParameterizedTest(name = "a byte of {0} is {1}")
        @CsvSource({"0, ALLOW", "1, ASK", "2, THROW", "3, QUIT", "4, ALLOW", "255, ALLOW"})
        @DisplayName("a byte past quit means nothing, so the access goes ahead, as Trap_Security has no branch for it")
        void aByteAtEachBoundary(int written, PolicyLevel meant) {
            SecurityPolicies policies = policiesFrom("envr: " + written + "." + written + "." + written);

            assertThat(policies.findTheLevelFor(PolicyClass.ENVR, PolicyAccess.READ, Optional.empty())).isEqualTo(meant);
        }

        @Test
        @DisplayName("a tuple of two bytes leaves execute at nought, which allows")
        void aShortTuple() {
            SecurityPolicies policies = new SecurityPolicies(
                    interpreter.run("make object! [call: to tuple! [2 2]]").value());

            assertThat(policies.findTheLevelFor(PolicyClass.CALL, PolicyAccess.EXECUTE, Optional.empty()))
                    .isEqualTo(PolicyLevel.ALLOW);
        }
    }

    @Nested
    @DisplayName("a class holding a block of names")
    class ABlockOfNames {

        @Test
        @DisplayName("a subject beneath a named folder takes that folder's tuple")
        void beneathAFolder() {
            SecurityPolicies policies = policiesFrom("file: [file 2.2.2 %/app/ 0.0.0]");

            assertThat(policies.findTheLevelFor(PolicyClass.FILE, PolicyAccess.READ, aFile("/app/in.txt")))
                    .isEqualTo(PolicyLevel.ALLOW);
        }

        @Test
        @DisplayName("the folder named exactly is covered")
        void theFolderExactly() {
            SecurityPolicies policies = policiesFrom("file: [file 2.2.2 %/app/ 0.0.0]");

            assertThat(policies.findTheLevelFor(PolicyClass.FILE, PolicyAccess.READ, aFile("/app/")))
                    .isEqualTo(PolicyLevel.ALLOW);
        }

        @Test
        @DisplayName("the folder named without its slash is one character short and falls to the default")
        void oneCharacterShort() {
            SecurityPolicies policies = policiesFrom("file: [file 2.2.2 %/app/ 0.0.0]");

            assertThat(policies.findTheLevelFor(PolicyClass.FILE, PolicyAccess.READ, aFile("/app")))
                    .isEqualTo(PolicyLevel.THROW);
        }

        @Test
        @DisplayName("a file name covers a longer name that begins with it, as Match_Sub_Path compares only the front")
        void aFileNameCoversALongerOne() {
            SecurityPolicies policies = policiesFrom("file: [file 2.2.2 %/app/in.txt 0.0.0]");

            assertThat(policies.findTheLevelFor(PolicyClass.FILE, PolicyAccess.READ, aFile("/app/in.txt2")))
                    .isEqualTo(PolicyLevel.ALLOW);
        }

        @Test
        @DisplayName("anything else takes the class's default")
        void elsewhereTakesTheDefault() {
            SecurityPolicies policies = policiesFrom("file: [file 2.2.2 %/app/ 0.0.0]");

            assertThat(policies.findTheLevelFor(PolicyClass.FILE, PolicyAccess.READ, aFile("/apple.txt")))
                    .isEqualTo(PolicyLevel.THROW);
        }

        @Test
        @DisplayName("the first name matched decides, which SECURE makes the longest")
        void theFirstMatchDecides() {
            SecurityPolicies policies = policiesFrom("file: [file 2.2.2 %/app/secret/ 3.3.3 %/app/ 0.0.0]");

            assertThat(policies.findTheLevelFor(PolicyClass.FILE, PolicyAccess.READ, aFile("/app/secret/x")))
                    .isEqualTo(PolicyLevel.QUIT);
            assertThat(policies.findTheLevelFor(PolicyClass.FILE, PolicyAccess.READ, aFile("/app/x")))
                    .isEqualTo(PolicyLevel.ALLOW);
        }

        @Test
        @DisplayName("a file name matches by case")
        void aFileMatchesByCase() {
            SecurityPolicies policies = policiesFrom("file: [file 2.2.2 %/App/ 0.0.0]");

            assertThat(policies.findTheLevelFor(PolicyClass.FILE, PolicyAccess.READ, aFile("/app/x")))
                    .isEqualTo(PolicyLevel.THROW);
        }

        @Test
        @DisplayName("a url matches without case")
        void aUrlMatchesWithoutCase() {
            SecurityPolicies policies = policiesFrom("net: [net 2.2.2 tcp://LOCALHOST 0.0.0]");

            assertThat(policies.findTheLevelFor(PolicyClass.NET, PolicyAccess.EXECUTE,
                    Optional.of(UrlValue.of("tcp://localhost:80"))))
                    .isEqualTo(PolicyLevel.ALLOW);
        }

        @Test
        @DisplayName("of two words, the later is the default, as the C overwrites as it goes")
        void theLaterWordIsTheDefault() {
            SecurityPolicies policies = policiesFrom("file: [file 2.2.2 again 0.0.0]");

            assertThat(policies.findTheLevelFor(PolicyClass.FILE, PolicyAccess.READ, aFile("/x")))
                    .isEqualTo(PolicyLevel.ALLOW);
        }

        @Test
        @DisplayName("a block with no default still lets through what it names")
        void noDefaultButAMatch() {
            SecurityPolicies policies = policiesFrom("file: [%/app/ 0.0.0]");

            assertThat(policies.findTheLevelFor(PolicyClass.FILE, PolicyAccess.READ, aFile("/app/x")))
                    .isEqualTo(PolicyLevel.ALLOW);
        }

        @Test
        @DisplayName("and refuses what it does not name, as a violation naming the subject")
        void noDefaultAndNoMatch() {
            SecurityPolicies policies = policiesFrom("file: [%/app/ 0.0.0]");

            Refusal refused = refusalOf(() ->
                    policies.findTheLevelFor(PolicyClass.FILE, PolicyAccess.READ, aFile("/elsewhere")));

            assertThat(refused.id()).isEqualTo("security");
            assertThat(refused.arg1()).isEqualTo(FileValue.of("/elsewhere"));
        }

        @Test
        @DisplayName("an empty block, with neither name nor default, refuses everything")
        void anEmptyBlock() {
            SecurityPolicies policies = policiesFrom("file: []");

            assertThat(refusalOf(() ->
                    policies.findTheLevelFor(PolicyClass.FILE, PolicyAccess.READ, aFile("/x"))).id())
                    .isEqualTo("security");
        }
    }

    @Nested
    @DisplayName("a policy that is not one of those shapes is a security error")
    class MalformedPolicies {

        @Test
        @DisplayName("a class missing from the policies names the class")
        void aMissingClass() {
            SecurityPolicies policies = policiesFrom("net: 0.0.0");

            Refusal refused = refusalOf(() ->
                    policies.findTheLevelFor(PolicyClass.FILE, PolicyAccess.READ, aFile("/x")));

            assertThat(refused.id()).isEqualTo("security-error");
            assertThat(refused.arg1()).isEqualTo(WordValue.of("file"));
        }

        @Test
        @DisplayName("policies that are not an object name the class")
        void notAnObject() {
            SecurityPolicies policies = new SecurityPolicies(interpreter.run("[file 0.0.0]").value());

            assertThat(refusalOf(() ->
                    policies.findTheLevelFor(PolicyClass.FILE, PolicyAccess.READ, aFile("/x"))).arg1())
                    .isEqualTo(WordValue.of("file"));
        }

        @Test
        @DisplayName("a class holding a number names the number")
        void aNumber() {
            SecurityPolicies policies = policiesFrom("file: 5");

            Refusal refused = refusalOf(() ->
                    policies.findTheLevelFor(PolicyClass.FILE, PolicyAccess.READ, aFile("/x")));

            assertThat(refused.id()).isEqualTo("security-error");
            assertThat(refused.arg1()).isEqualTo(interpreter.run("5").value());
        }

        @Test
        @DisplayName("a name with no tuple after it names the name")
        void aNameWithoutATuple() {
            SecurityPolicies policies = policiesFrom("file: [file 0.0.0 %/app/ allow]");

            Refusal refused = refusalOf(() ->
                    policies.findTheLevelFor(PolicyClass.FILE, PolicyAccess.READ, aFile("/app/x")));

            assertThat(refused.id()).isEqualTo("security-error");
            assertThat(refused.arg1()).isEqualTo(FileValue.of("/app/"));
        }

        @Test
        @DisplayName("a name in a class asked about with no subject names the name")
        void aNameWithNoSubject() {
            SecurityPolicies policies = policiesFrom("debug: [debug 0.0.0 %/app/ 0.0.0]");

            Refusal refused = refusalOf(() ->
                    policies.findTheLevelFor(PolicyClass.DEBUG, PolicyAccess.READ, Optional.empty()));

            assertThat(refused.id()).isEqualTo("security-error");
            assertThat(refused.arg1()).isEqualTo(FileValue.of("/app/"));
        }

        @Test
        @DisplayName("an entry that is neither word nor name names the entry")
        void anEntryOfAnotherKind() {
            SecurityPolicies policies = policiesFrom("file: [file 0.0.0 7 0.0.0]");

            assertThat(refusalOf(() ->
                    policies.findTheLevelFor(PolicyClass.FILE, PolicyAccess.READ, aFile("/x"))).id())
                    .isEqualTo("security-error");
        }
    }

    @Test
    @DisplayName("every class is spelled as SECURE spells it")
    void everyClassIsSpelledAsSecureSpellsIt() {
        assertThat(Arrays.stream(PolicyClass.values()).map(PolicyClass::word))
                .containsExactly("file", "net", "eval", "memory", "secure", "protect", "debug",
                        "envr", "call", "browse", "extension");
    }
}
