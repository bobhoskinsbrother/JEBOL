package org.jebol.application;

import org.jebol.domain.host.HostService;
import org.jebol.domain.host.HttpPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TheHostsOwnHttpFromTheSourceTest {

    private static Interpreter aHostThatFetches(HttpPort port) {
        Interpreter interpreter = Interpreter.withBounds(
                Bounds.standard().withoutAWallClockLimit()
                        .grant(HostService.WEB).grant(HostService.CONSOLE));
        interpreter.useHttp(port);
        return interpreter;
    }

    private static String answerTo(Interpreter interpreter, String source) {
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static final class ACannedHost implements HttpPort {

        private final List<Request> requests = new ArrayList<>();
        private Answer answer = new Answer(200,
                List.of("Content-Type"), List.of("text/plain"),
                "hello".getBytes(StandardCharsets.UTF_8));
        private Refused refusal;

        ACannedHost answering(int status, String body) {
            this.answer = new Answer(status, List.of("Content-Type"), List.of("text/plain"),
                    body.getBytes(StandardCharsets.UTF_8));
            return this;
        }

        ACannedHost answering(int status, String contentType, byte[] body) {
            this.answer = new Answer(status, List.of("Content-Type"), List.of(contentType), body);
            return this;
        }

        ACannedHost answeringWithoutAContentType(int status, byte[] body) {
            this.answer = new Answer(status, List.of(), List.of(), body);
            return this;
        }

        ACannedHost refusing(String errorId, String because) {
            this.refusal = new Refused(errorId, because, "http://example.com/a");
            return this;
        }

        Request theOnlyRequest() {
            return requests.getFirst();
        }

        @Override
        public Answer fetch(Request request) {
            requests.add(request);
            if (refusal != null) {
                throw refusal;
            }
            return answer;
        }
    }

    @Test
    @DisplayName("reading an http url answers the body the host fetched, as text")
    void readingAnswersTheBody() {
        ACannedHost host = new ACannedHost();

        assertThat(answerTo(aHostThatFetches(host), "read http://example.com/a"))
                .isEqualTo("\"hello\"");
    }

    @Test
    @DisplayName("and the request crossed as the url, the method and the spec's defaults")
    void theRequestCrossesWhole() {
        ACannedHost host = new ACannedHost();
        answerTo(aHostThatFetches(host), "read http://example.com/a");

        assertThat(host.theOnlyRequest().url()).isEqualTo("http://example.com/a");
        assertThat(host.theOnlyRequest().method()).isEqualToIgnoringCase("GET");
        assertThat(host.theOnlyRequest().headerNames()).isEmpty();
        assertThat(host.theOnlyRequest().content()).isEmpty();
        assertThat(host.theOnlyRequest().followsRedirects()).isTrue();
        assertThat(host.theOnlyRequest().timeout()).contains(Duration.ofSeconds(15));
    }

    @Test
    @DisplayName("binary is preserved exactly")
    void binaryIsPreserved() {
        assertThat(answerTo(aHostThatFetches(new ACannedHost()),
                "read/binary http://example.com/a"))
                .isEqualTo("#{68656C6C6F}");
    }

    @Test
    @DisplayName("lines answers a block of the body's lines")
    void linesAnswersABlock() {
        assertThat(answerTo(aHostThatFetches(new ACannedHost().answering(200, "one\ntwo\n")),
                "read/lines http://example.com/a"))
                .isEqualTo("[\"one\" \"two\"]");
    }

    @Test
    @DisplayName("all answers the status, the headers and the body")
    void allAnswersTheThreeParts() {
        assertThat(answerTo(aHostThatFetches(new ACannedHost()),
                "read/all http://example.com/a"))
                .isEqualTo("[200 [\"Content-Type\" \"text/plain\"] \"hello\"]");
    }

    @Test
    @DisplayName("part and seek trim the body, and ask the host for a range")
    void partAndSeekTrim() {
        ACannedHost host = new ACannedHost();
        Interpreter interpreter = aHostThatFetches(host);

        assertThat(answerTo(interpreter, "read/part http://example.com/a 3"))
                .isEqualTo("#{68656C}");
        assertThat(host.theOnlyRequest().headerNames()).containsExactly("Range");
        assertThat(host.theOnlyRequest().headerValues()).containsExactly("bytes=0-2");
        assertThat(answerTo(interpreter, "read/seek http://example.com/a 2"))
                .isEqualTo("#{6C6C6F}");
    }

    @Test
    @DisplayName("an error page the host brought back raises, and names the status")
    void anErrorPageRaises() {
        Interpreter interpreter = aHostThatFetches(new ACannedHost().answering(404, "gone"));

        assertThat(answerTo(interpreter, "e: try [read http://example.com/a] reduce [e/id e/arg1]"))
                .isEqualTo("[protocol \"Not Found\"]");
    }

    @Test
    @DisplayName("all asks for the error page rather than raising, as Rebol's own protocol does")
    void allDoesNotRaiseOnAnErrorPage() {
        assertThat(answerTo(aHostThatFetches(new ACannedHost().answering(404, "gone")),
                "read/all http://example.com/a"))
                .isEqualTo("[404 [\"Content-Type\" \"text/plain\"] \"gone\"]");
    }

    @Test
    @DisplayName("a host that could not be reached raises rather than answering none")
    void aRefusalRaises() {
        Interpreter interpreter = aHostThatFetches(new ACannedHost()
                .refusing("no-connect", "cannot connect: example.com"));

        assertThat(answerTo(interpreter, "e: try [read http://example.com/a] e/id"))
                .isEqualTo("no-connect");
        assertThat(answerTo(interpreter,
                "e: try [read http://example.com/a] e/arg1 = {http://example.com/a}"))
                .isEqualTo("#(true)");
    }

    @Test
    @DisplayName("OPEN and CLOSE are the actor's, so a port opens by hand and reads the same")
    void openAndCloseAreTheActors() {
        ACannedHost host = new ACannedHost();

        assertThat(answerTo(aHostThatFetches(host),
                "p: open http://example.com/a reduce [open? p read p]"))
                .isEqualTo("[#(true) \"hello\"]");
        assertThat(answerTo(aHostThatFetches(host),
                "p: open http://example.com/a close p open? p"))
                .isEqualTo("#(false)");
    }

    @Test
    @DisplayName("the scheme's actor is JEBOL's own, and Rebol's module is still there")
    void theActorIsSwappedAndTheProtocolStays() {
        Interpreter interpreter = aHostThatFetches(new ACannedHost());

        assertThat(answerTo(interpreter, "type? :system/schemes/http/actor"))
                .isEqualTo("#(native!)");
        assertThat(answerTo(interpreter, "not none? select system/modules 'http"))
                .isEqualTo("#(true)");
        assertThat(answerTo(interpreter, "query http://example.com none"))
                .contains("status-code");
    }

    @Test
    @DisplayName("a host that supplies no fetching keeps Rebol's own protocol")
    void withoutAHostPortTheProtocolStands() {
        Interpreter interpreter = Interpreter.withBounds(
                Bounds.standard().withoutAWallClockLimit().grant(HostService.WEB));

        assertThat(answerTo(interpreter, "type? :system/schemes/http/actor"))
                .isEqualTo("#(object!)");
        assertThat(answerTo(interpreter, "e: try [read http://example.com/a] e/id"))
                .isEqualTo("no-service");
    }

    @Test
    @DisplayName("without the web service, even a host that fetches is refused")
    void theServiceIsAlsoNeeded() {
        Interpreter interpreter = Interpreter.withBounds(
                Bounds.standard().withoutAWallClockLimit());
        interpreter.useHttp(new ACannedHost());

        assertThat(answerTo(interpreter, "e: try [read http://example.com/a] mold e"))
                .contains("web is not granted");
    }

    @Test
    @DisplayName("write has no arm yet, and says so by naming the verb")
    void writeIsNotAnArmYet() {
        Interpreter interpreter = aHostThatFetches(new ACannedHost());

        assertThat(answerTo(interpreter, "e: try [write http://example.com/a {x}] e/arg1"))
                .isEqualTo("write:");
    }

    @Test
    @DisplayName("https is the same actor, and answers the same way")
    void httpsIsTheSameActor() {
        assertThat(answerTo(aHostThatFetches(new ACannedHost()), "read https://example.com/a"))
                .isEqualTo("\"hello\"");
    }

    @Test
    @DisplayName("refinements that contradict each other are refused as Rebol refuses them")
    void contradictoryRefinementsAreRefused() {
        Interpreter interpreter = aHostThatFetches(new ACannedHost());

        assertThat(answerTo(interpreter,
                "e: try [read/string/binary http://example.com/a] e/id"))
                .isEqualTo("bad-refines");
        assertThat(answerTo(interpreter,
                "e: try [read/lines/binary http://example.com/a] e/id"))
                .isEqualTo("bad-refine");
    }

    @Test
    @DisplayName("a 206 is already the range asked for, so nothing is trimmed twice")
    void aPartialAnswerIsNotTrimmedAgain() {
        assertThat(answerTo(aHostThatFetches(new ACannedHost().answering(206, "ell")),
                "read/part http://example.com/a 3"))
                .isEqualTo("#{656C6C}");
    }

    @Test
    @DisplayName("a request must name a url and a method, and its headers must pair")
    void aRequestWithoutItsPiecesIsRefused() {
        assertThatThrownBy(() -> new HttpPort.Request(
                "", "GET", List.of(), List.of(), new byte[0], true, Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("url");
        assertThatThrownBy(() -> new HttpPort.Request(
                "http://example.com", " ", List.of(), List.of(), new byte[0], true, Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("method");
        assertThatThrownBy(() -> new HttpPort.Request(
                "http://example.com", "GET", List.of("Accept"), List.of(),
                new byte[0], true, Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("every header name");
    }

    @Test
    @DisplayName("the request and the answer hold their bytes rather than borrowing them")
    void theRecordsHoldTheirBytes() {
        byte[] content = new byte[] {1, 2, 3};
        HttpPort.Request request = new HttpPort.Request(
                "http://example.com", "GET", List.of(), List.of(), content, true, Optional.empty());
        content[0] = 9;
        assertThat(request.content()).containsExactly((byte) 1, (byte) 2, (byte) 3);

        byte[] body = new byte[] {4, 5};
        HttpPort.Answer answer = new HttpPort.Answer(200, List.of(), List.of(), body);
        body[0] = 9;
        assertThat(answer.body()).containsExactly((byte) 4, (byte) 5);
    }

    @Test
    @DisplayName("a body whose type is not text is answered as the bytes it is")
    void aBinaryTypeStaysBinary() {
        byte[] cafeInUtf8 = new byte[] {0x63, 0x61, 0x66, (byte) 0xC3, (byte) 0xA9};

        assertThat(answerTo(aHostThatFetches(
                new ACannedHost().answering(200, "application/octet-stream", cafeInUtf8)),
                "read http://example.com/blob"))
                .isEqualTo("#{636166C3A9}");
        assertThat(answerTo(aHostThatFetches(
                new ACannedHost().answering(200, "application/octet-stream", cafeInUtf8)),
                "read/string http://example.com/blob"))
                .isEqualTo("\"café\"");
    }

    @Test
    @DisplayName("a body with no declared type at all is bytes too")
    void noContentTypeIsBytes() {
        assertThat(answerTo(aHostThatFetches(new ACannedHost()
                .answeringWithoutAContentType(200, "gone".getBytes(StandardCharsets.UTF_8))),
                "read http://example.com/blob"))
                .isEqualTo("#{676F6E65}");
    }

    @Test
    @DisplayName("a charset decodes the bytes by it rather than by UTF-8")
    void aCharsetDecodesTheBody() {
        assertThat(answerTo(aHostThatFetches(new ACannedHost()
                .answering(200, "text/html; charset=iso-8859-1", new byte[] {(byte) 0xE9})),
                "read http://example.com/page"))
                .isEqualTo("\"é\"");
    }

    @Test
    @DisplayName("a charset nothing here decodes leaves the body as bytes rather than guessing")
    void anUnknownCharsetLeavesBytes() {
        assertThat(answerTo(aHostThatFetches(new ACannedHost()
                .answering(200, "text/html; charset=made-up-8", "gone".getBytes(StandardCharsets.UTF_8))),
                "read http://example.com/page"))
                .isEqualTo("#{676F6E65}");
    }

    @Test
    @DisplayName("lines forces text whatever the type says")
    void linesForcesText() {
        assertThat(answerTo(aHostThatFetches(new ACannedHost()
                .answering(200, "application/octet-stream", "one\ntwo".getBytes(StandardCharsets.UTF_8))),
                "read/lines http://example.com/blob"))
                .isEqualTo("[\"one\" \"two\"]");
    }
}
