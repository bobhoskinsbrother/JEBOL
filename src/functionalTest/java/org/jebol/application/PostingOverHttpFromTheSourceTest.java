package org.jebol.application;

import org.jebol.domain.host.HostService;
import org.jebol.domain.host.HttpPort;
import org.jebol.domain.host.OutputPort;
import org.jebol.domain.value.Molder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PostingOverHttpFromTheSourceTest {

    private final AServerThatEchoes server = new AServerThatEchoes();
    private final Interpreter interpreter = Interpreter.createOn(
            AnInterpreter.standardPorts().withHttp(server),
            OutputPort.discard(),
            Bounds.standard().grant(HostService.WEB));

    private String answer(String source) {
        interpreter.defineNewWordsIn(source);
        ScriptOutcome outcome = interpreter.run(source);
        return outcome.succeeded() ? Molder.form(outcome.value()) : interpreter.display(outcome);
    }

    @Test
    @DisplayName("WRITE of a string posts it as a url-encoded form and answers the decoded reply")
    void writesAStringAsAForm() {
        assertThat(answer("write http://example.test/form {a=b c}")).isEqualTo("you sent POST");

        HttpPort.Request sent = server.lastRequest();
        assertThat(sent.method()).isEqualTo("POST");
        assertThat(sent.url()).isEqualTo("http://example.test/form");
        assertThat(server.contentTypeSent()).contains("application/x-www-form-urlencoded; charset=utf-8");
        assertThat(new String(sent.content(), StandardCharsets.UTF_8)).isEqualTo("a=b c");
    }

    @Test
    @DisplayName("WRITE of a binary posts its octets as they are")
    void writesABinaryAsOctets() {
        answer("write http://example.test/bin #{0102FF}");

        assertThat(server.contentTypeSent()).contains("application/octet-stream");
        assertThat(server.lastRequest().content()).containsExactly(0x01, 0x02, 0xFF);
    }

    @Test
    @DisplayName("WRITE of a map posts it as JSON")
    void writesAMapAsJson() {
        answer("write http://example.test/json #[a: 1 b: {x}]");

        assertThat(server.contentTypeSent()).contains("application/json; charset=utf-8");
        assertThat(new String(server.lastRequest().content(), StandardCharsets.UTF_8))
                .isEqualTo("""
                        {"a":1,"b":"x"}""");
    }

    @Test
    @DisplayName("anything else is formed and posted as a form, as prot-http forms it")
    void formsAnythingElse() {
        answer("write http://example.test/num 42");
        assertThat(new String(server.lastRequest().content(), StandardCharsets.UTF_8)).isEqualTo("42");

        answer("write http://example.test/empty {}");
        assertThat(server.lastRequest().content()).isEmpty();
        assertThat(server.lastRequest().method()).isEqualTo("POST");
    }

    @Test
    @DisplayName("a block is the write dialect: a method, the headers to send, and the content")
    void readsTheWriteDialect() {
        answer("write http://example.test/start [PATCH [Content-Type: {text/plain}] {patched}]");

        HttpPort.Request sent = server.lastRequest();
        assertThat(sent.method()).isEqualTo("PATCH");
        assertThat(sent.url()).isEqualTo("http://example.test/start");
        assertThat(server.contentTypeSent()).contains("text/plain");
        assertThat(new String(sent.content(), StandardCharsets.UTF_8)).isEqualTo("patched");
    }

    @Test
    @DisplayName("a path in the dialect replaces the url's own path")
    void aPathInTheDialectReplacesTheUrls() {
        answer("write http://example.test/start [PUT %/other [X-Token: {t}] {body}]");

        HttpPort.Request sent = server.lastRequest();
        assertThat(sent.method()).isEqualTo("PUT");
        assertThat(sent.url()).isEqualTo("http://example.test/other");
        assertThat(sent.headerNames()).contains("X-Token");
        assertThat(sent.headerValues()).contains("t");
    }

    @Test
    @DisplayName("a reply to a method other than GET, POST or PATCH stays binary, as prot-http leaves it")
    void otherMethodsAnswerBinary() {
        assertThat(answer("binary? write http://example.test/start [PUT {body}]")).isEqualTo("true");
        assertThat(answer("string? write http://example.test/start [PATCH {body}]")).isEqualTo("true");
    }

    @Test
    @DisplayName("WRITE/BINARY answers the reply's octets undecoded")
    void writeBinaryAnswersOctets() {
        assertThat(answer("mold write/binary http://example.test/form {x}"))
                .isEqualTo("#{796F752073656E7420504F5354}");
    }

    @Test
    @DisplayName("a reply of 400 or more is a protocol error naming the status, as READ raises one")
    void anErrorStatusIsAProtocolError() {
        assertThat(answer("""
                e: try [write http://example.test/missing {x}]
                mold reduce [e/id e/arg1]""")).isEqualTo("""
                [protocol "Not Found"]""");
    }

    @Test
    @DisplayName("WRITE/ALL answers the status, the headers and the body instead of raising")
    void writeAllAnswersEverything() {
        assertThat(answer("""
                answered: write/all http://example.test/missing {x}
                mold reduce [first answered last answered]""")).isEqualTo("""
                [404 "you sent POST"]""");
    }

    @Test
    @DisplayName("a host that could not reach the server is a catchable error, as for READ")
    void anUnreachableServerIsCatchable() {
        server.goesAway();

        assertThat(answer("""
                e: try [write http://example.test/form {x}]
                e/id""")).isEqualTo("no-connect");
    }

    @Test
    @DisplayName("WRITE needs the web service granted, as READ does")
    void writeNeedsTheWebGranted() {
        Interpreter ungranted = Interpreter.createOn(
                AnInterpreter.standardPorts().withHttp(server), OutputPort.discard(), Bounds.standard());
        String source = "e: try [write http://example.test/form {x}] mold e";
        ungranted.defineNewWordsIn(source);

        assertThat(ungranted.display(ungranted.run(source))).contains("web is not granted");
        assertThat(server.requestsSeen()).isZero();
    }

    private static final class AServerThatEchoes implements HttpPort {

        private final List<Request> requests = new ArrayList<>();
        private boolean away;

        Request lastRequest() {
            return requests.getLast();
        }

        int requestsSeen() {
            return requests.size();
        }

        void goesAway() {
            away = true;
        }

        List<String> contentTypeSent() {
            Request sent = lastRequest();
            List<String> types = new ArrayList<>();
            for (int at = 0; at < sent.headerNames().size(); at++) {
                if (sent.headerNames().get(at).equalsIgnoreCase("Content-Type")) {
                    types.add(sent.headerValues().get(at));
                }
            }
            return types;
        }

        @Override
        public Answer fetch(Request request) {
            if (away) {
                throw new Refused("no-connect", "cannot connect", request.url());
            }
            requests.add(request);
            int status = request.url().endsWith("/missing") ? 404 : 200;
            return new Answer(status, List.of("Content-Type"), List.of("text/plain; charset=utf-8"),
                    ("you sent " + request.method()).getBytes(StandardCharsets.UTF_8));
        }
    }
}
