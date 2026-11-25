package org.jebol.suite;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TheSuiteKeepsItsDirectoryTest {

    private static final String SEES_THE_SUITES_FILES = "exists? %units/files/test-deflate.zip";

    private static String answerTo(Interpreter interpreter, String source) {
        interpreter.defineNewWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static Interpreter aSuiteInterpreter() {
        return SuiteHost.anInterpreterFor(SuiteHost.grantEverything());
    }

    @Test
    @DisplayName("a run starts among the suite's files")
    void startsAmongTheFiles() {
        assertThat(answerTo(aSuiteInterpreter(), SEES_THE_SUITES_FILES)).isEqualTo("file");
    }

    @Test
    @DisplayName("WILDCARD finds the suite's three zip files there, as file-test asks")
    void findsTheZipFiles() {
        assertThat(answerTo(aSuiteInterpreter(), """
                e: try [files: wildcard %units/files/ %*.zip]
                either error? e [reduce [e/id e/arg1 e/arg2]] [
                    new-line/all collect [
                        foreach file sort files [
                            keep reduce [(first split-path file) = join what-dir %units/files/ second split-path file]
                        ]
                    ] false
                ]"""))
                .isEqualTo("[#(true) %test-deflate.zip #(true) %test-lzma.zip #(true) %test-stored.zip]");
    }

    @Test
    @DisplayName("file-test is back where the run started when its WILDCARD group begins, though issue-2351 steps out to / and back")
    void fileTestComesBackBeforeItsWildcards() {
        SuiteFile fileTest = SuiteGrading.filesInSuite().stream()
                .filter(file -> file.name().equals("file-test.r3"))
                .findFirst()
                .orElseThrow();
        Interpreter interpreter = aSuiteInterpreter();
        String start = answerTo(interpreter, "what-dir");
        for (SuiteFile.Step step : fileTest.steps()) {
            if (step.isAssertion() && step.assertion().group().equals("WILDCARD")) {
                break;
            }
            String source = step.sourceToRun();
            if (source == null || source.isBlank()) {
                continue;
            }
            interpreter.defineNewWordsIn(source);
            interpreter.run("try [" + source + "\n]");
        }

        assertThat(answerTo(interpreter, "what-dir")).isEqualTo(start);
    }

    @Test
    @DisplayName("withholding file-test's issue-2538 withholds every step of it, the cd into its folder included, and no other")
    void withholdingATestWithholdsItsSetupToo() {
        SuiteFile fileTest = SuiteGrading.filesInSuite().stream()
                .filter(file -> file.name().equals("file-test.r3"))
                .findFirst()
                .orElseThrow();
        List<String> named = List.of("file-test.r3 / to-local-file / issue-2538");

        List<String> withheld = fileTest.steps().stream()
                .filter(step -> SuiteGrading.isWithheldBy(named, fileTest.name(), step))
                .map(SuiteFile.Step::sourceToRun)
                .filter(source -> source != null && !source.isBlank())
                .toList();

        assertThat(withheld).anyMatch(source -> source.contains("cd (mkdir %issue-2538)"));
        assertThat(withheld).allMatch(source -> source.contains("issue-2538"));
    }

    @Test
    @DisplayName("withholding one codec's test withholds its own block and none of the codec groups after it")
    void withholdingATestLeavesTheNextBlockAlone() {
        SuiteFile codecsTest = SuiteGrading.filesInSuite().stream()
                .filter(file -> file.name().equals("codecs-test.r3"))
                .findFirst()
                .orElseThrow();
        List<String> named = List.of("codecs-test.r3 / PNG codec / png/size?");

        assertThat(codecsTest.steps().stream()
                .filter(step -> SuiteGrading.isWithheldBy(named, codecsTest.name(), step))
                .flatMap(step -> step.isAssertion()
                        ? java.util.stream.Stream.concat(java.util.stream.Stream.of(step.assertion()), step.nested().stream())
                        : step.nested().stream())
                .map(SuiteFile.Assertion::group)
                .distinct()
                .toList())
                .containsExactly("PNG codec");
    }

    @Test
    @DisplayName("withholding a test written inside an IF block withholds that block, though its group and test name hold a slash")
    void withholdingATestInsideABlockWithholdsTheBlock() {
        SuiteFile imageTest = SuiteGrading.filesInSuite().stream()
                .filter(file -> file.name().equals("image-test.r3"))
                .findFirst()
                .orElseThrow();
        List<String> named = List.of("image-test.r3 / Save/load image / save/load PNG");

        assertThat(imageTest.steps().stream()
                .filter(step -> SuiteGrading.isWithheldBy(named, imageTest.name(), step))
                .map(SuiteFile.Step::sourceToRun)
                .toList())
                .anyMatch(source -> source.contains("save %units/files/test.png img1"));
    }

    @Test
    @DisplayName("file-test's cd to / and ~ and back leaves it among them")
    void comesBackFromRootAndHome() {
        Interpreter interpreter = aSuiteInterpreter();
        answerTo(interpreter, """
                dir: what-dir
                all [not error? try [cd /] not error? try [cd ~]]
                change-dir dir""");

        assertThat(answerTo(interpreter, "reduce [dir = what-dir " + SEES_THE_SUITES_FILES + "]"))
                .isEqualTo("[#(true) file]");
    }

    @Test
    @DisplayName("file-test's issue-2351, which reads / from /, leaves it among them")
    void comesBackFromReadingTheRoot() {
        Interpreter interpreter = aSuiteInterpreter();
        answerTo(interpreter, """
                d: what-dir
                cd %/ f: first read %/""");
        answerTo(interpreter, "cd d");

        assertThat(answerTo(interpreter, "reduce [d = what-dir " + SEES_THE_SUITES_FILES + "]"))
                .isEqualTo("[#(true) file]");
    }
}
