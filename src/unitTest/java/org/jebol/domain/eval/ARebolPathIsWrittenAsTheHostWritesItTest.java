package org.jebol.domain.eval;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class ARebolPathIsWrittenAsTheHostWritesItTest {

    @ParameterizedTest(name = "{0} -> [{1}]")
    @CsvSource(delimiter = '|', emptyValue = "", value = {
            "/C/Users/someone/boot.cmd | C:\\Users\\someone\\boot.cmd",
            "/c/dir/                   | c:\\dir\\",
            "/c                        | c:\\",
            "/                         | ''",
            "/cc/x                     | \\\\cc\\x",
            "//cc                      | \\cc",
            "a/b                       | a\\b",
    })
    @DisplayName("on Windows a drive comes first, as R3's To_Local_Path writes it")
    void writesWindowsPaths(String rebolPath, String local) {
        assertThat(LocalFileConvention.WINDOWS.writeLocalPath(rebolPath)).isEqualTo(local);
    }

    @ParameterizedTest(name = "{0} -> [{1}]")
    @CsvSource(delimiter = '|', value = {
            "/tmp/boot.sh | /tmp/boot.sh",
            "a/b          | a/b",
            "/            | /",
    })
    @DisplayName("elsewhere the path is written as it is")
    void writesPosixPaths(String rebolPath, String local) {
        assertThat(LocalFileConvention.POSIX.writeLocalPath(rebolPath)).isEqualTo(local);
    }
}
