package org.jebol.adapter.cli;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.stream.Stream;

class CatalogueAsRebolListsItEndToEndTest {

    @TempDir
    Path directory;

    @TestFactory
    @DisplayName("system/catalog lists the natives and actions a real 3.22.5 lists, in its order")
    Stream<DynamicTest> eachRowPrintsWhatRebolPrinted() throws IOException {
        return new RecordedRebolScript("catalogue", directory).eachRowPrintsWhatRebolPrinted();
    }
}
