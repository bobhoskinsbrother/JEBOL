package org.jebol.domain.host;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class VouchedNamesTest {

    private VouchedNames dnsNames(String... dnsNames) {
        return new VouchedNames(Set.of(dnsNames), Set.of());
    }

    private VouchedNames ipAddresses(String... ipAddresses) {
        return new VouchedNames(Set.of(), Set.of(ipAddresses));
    }

    @Nested
    @DisplayName("a name written out in full")
    class AFullName {

        @ParameterizedTest(name = "{0} covers {1}: {2}")
        @CsvSource({
                "example.com,       example.com,        true",
                "example.com,       EXAMPLE.com,        true",
                "Example.COM,       example.com,        true",
                "example.com,       example.com.,       true",
                "example.com.,      example.com,        true",
                "example.com.,      example.com.,       true",
                "example.com,       example.com..,      false",
                "example.com,       www.example.com,    false",
                "www.example.com,   example.com,        false",
                "example.com,       example.co,         false",
                "example.com,       example.comm,       false",
                "localhost,         localhost,          true",
        })
        @DisplayName("covers only itself, whatever the letter case, with one trailing dot ignored")
        void coversOnlyItself(String vouched, String host, boolean covered) {
            assertThat(dnsNames(vouched).cover(host)).isEqualTo(covered);
        }

        @Test
        @DisplayName("is one of several, and any of them covering the host is enough")
        void anyOfSeveral() {
            assertThat(dnsNames("a.test", "b.test", "c.test").cover("b.test")).isTrue();
            assertThat(dnsNames("a.test", "b.test", "c.test").cover("d.test")).isFalse();
        }
    }

    @Nested
    @DisplayName("a wildcard")
    class AWildcard {

        @ParameterizedTest(name = "{0} covers {1}: {2}")
        @CsvSource({
                "*.example.com,     www.example.com,      true",
                "*.example.com,     WWW.Example.COM.,     true",
                "*.example.com,     x.example.com,        true",
                "*.example.com,     example.com,          false",
                "*.example.com,     .example.com,         false",
                "*.example.com,     a.b.example.com,      false",
                "*.example.com,     www.example.org,      false",
                "*.example.com,     wwwexample.com,       false",
        })
        @DisplayName("stands for exactly one label that is not empty")
        void exactlyOneLabel(String vouched, String host, boolean covered) {
            assertThat(dnsNames(vouched).cover(host)).isEqualTo(covered);
        }

        @ParameterizedTest(name = "{0} covers nothing, not even {1}")
        @CsvSource({
                "w*.example.com,    www.example.com",
                "*w.example.com,    www.example.com",
                "ww*w.example.com,  www.example.com",
                "*.*.example.com,   a.b.example.com",
                "www.*.com,         www.example.com",
                "www.example.*,     www.example.com",
                "**.example.com,    www.example.com",
        })
        @DisplayName("anywhere but the whole of the leftmost label covers nothing")
        void wholeLeftmostLabelOnly(String vouched, String host) {
            assertThat(dnsNames(vouched).cover(host)).isFalse();
        }

        @ParameterizedTest(name = "{0} covers {1}: {2}")
        @CsvSource({
                "*,                 localhost,          false",
                "*.,                localhost.,         false",
                "*.com,             example.com,        false",
                "*.com.,            example.com,        false",
                "*.co.uk,           example.co.uk,      true",
                "*.a.b.c,           x.a.b.c,            true",
        })
        @DisplayName("with fewer than two labels after it covers nothing, and with two it covers one more")
        void twoLabelsAfterIt(String vouched, String host, boolean covered) {
            assertThat(dnsNames(vouched).cover(host)).isEqualTo(covered);
        }

        @Test
        @DisplayName("never covers an address")
        void neverCoversIp() {
            assertThat(dnsNames("*.0.0.1").cover("127.0.0.1")).isFalse();
            assertThat(dnsNames("*.168.1.1").cover("192.168.1.1")).isFalse();
        }
    }

    @Nested
    @DisplayName("an address")
    class AnAddress {

        @ParameterizedTest(name = "{0} covers {1}: {2}")
        @CsvSource({
                "127.0.0.1,                 127.0.0.1,          true",
                "127.0.0.1,                 127.0.0.2,          false",
                "0.0.0.0,                   0.0.0.0,            true",
                "255.255.255.255,           255.255.255.255,    true",
                "127.0.0.1,                 127.0.0.01,         false",
                "127.0.0.1,                 127.0.0.256,        false",
                "127.0.0.1,                 127.0.0,            false",
                "127.0.0.1,                 127.0.0.1.1,        false",
                "0:0:0:0:0:0:0:1,           ::1,                true",
                "0:0:0:0:0:0:0:1,           [::1],              true",
                "0:0:0:0:0:0:0:1,           0:0:0:0:0:0:0:1,    true",
                "0:0:0:0:0:0:0:1,           ::2,                false",
                "2001:db8:0:0:0:0:0:1,      2001:DB8::1,        true",
                "2001:db8:0:0:0:0:0:1,      2001:db8::1:0,      false",
                "0:0:0:0:0:0:0:1,           127.0.0.1,          false",
        })
        @DisplayName("is covered only by the same address, compared as an address")
        void sameAddress(String vouched, String host, boolean covered) {
            assertThat(ipAddresses(vouched).cover(host)).isEqualTo(covered);
        }

        @Test
        @DisplayName("is never covered by a name written the same way")
        void notByDnsName() {
            assertThat(dnsNames("127.0.0.1").cover("127.0.0.1")).isFalse();
        }

        @Test
        @DisplayName("named in a certificate never covers a host name")
        void ipNeverCoversName() {
            assertThat(ipAddresses("127.0.0.1").cover("localhost")).isFalse();
        }
    }

    @Nested
    @DisplayName("nothing at all")
    class Nothing {

        @Test
        @DisplayName("a certificate naming nothing covers no host")
        void noNames() {
            assertThat(new VouchedNames(Set.of(), Set.of()).cover("localhost")).isFalse();
        }

        @ParameterizedTest(name = "the host {0}")
        @CsvSource({"''", "'.'", "' '", "'*.example.com'", "'*'"})
        @DisplayName("an empty host, or one that is no name, is covered by nothing")
        void malformedHost(String host) {
            assertThat(dnsNames("example.com", "*.example.com", "*").cover(host)).isFalse();
        }
    }
}
