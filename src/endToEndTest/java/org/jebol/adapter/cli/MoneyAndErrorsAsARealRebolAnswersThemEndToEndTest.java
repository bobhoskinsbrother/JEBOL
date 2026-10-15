package org.jebol.adapter.cli;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.stream.Stream;

class MoneyAndErrorsAsARealRebolAnswersThemEndToEndTest {

    @TempDir
    Path directory;

    @TestFactory
    @DisplayName("each money, comparison, rounding, error and assignment row prints what a real 3.22.5 printed")
    Stream<DynamicTest> eachRowPrintsWhatRebolPrinted() throws IOException {
        return new RecordedRebolScript("money-and-errors", directory).eachRowPrintsWhatRebolPrinted();
    }
}
