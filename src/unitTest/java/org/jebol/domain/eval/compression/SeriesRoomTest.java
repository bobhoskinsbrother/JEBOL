package org.jebol.domain.eval.compression;

import org.jebol.domain.value.Raised;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SeriesRoomTest {

    @ParameterizedTest(name = "a binary of {0} octets has room for {1}")
    @DisplayName("the room is the smallest memory pool that holds the octets and their terminator")
    @CsvSource({
            "0, 8", "1, 8", "7, 8",
            "8, 32", "31, 32", "32, 64", "63, 64", "64, 96", "100, 128",
            "479, 480", "480, 512", "511, 512",
            "512, 640", "639, 640", "640, 768", "767, 768", "768, 896", "895, 896", "896, 960", "959, 960",
            "960, 1024", "1023, 1024", "1024, 2048", "2047, 2048", "2048, 3072", "3071, 3072",
            "3072, 4096", "4095, 4096",
            "4096, 5120", "5000, 5120", "6143, 6144", "6144, 7168", "10000, 10240", "70000, 70656",
            "2147482623, 2147482624", "2147482624, 2147483648", "2147483646, 2147483648"
    })
    void roundsUpToThePool(long length, long room) {
        assertThat(SeriesRoom.makeBinaryOf(length)).isEqualTo(room);
    }

    @Test
    @DisplayName("a binary whose terminator would take it past the largest series is out of memory")
    void refusesASeriesPastTheLargest() {
        assertThatThrownBy(() -> SeriesRoom.makeBinaryOf(Integer.MAX_VALUE))
                .isInstanceOf(Raised.class)
                .hasMessageContaining("room the count cannot hold");
    }
}
