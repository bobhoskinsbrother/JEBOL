package org.jebol.adapter.cli;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.stream.Stream;

class ConstructsAsARealRebolReadsThemEndToEndTest {

    @TempDir
    Path directory;

    @TestFactory
    @DisplayName("each keyword and malformed construct row prints what a real 3.22.5 printed")
    Stream<DynamicTest> printsWhatRebolPrintedOnEachRow() throws IOException {
        return new RecordedRebolScript("constructs", directory).replayEachRow();
    }
}
