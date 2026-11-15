package org.jebol.adapter.web;

import org.jebol.domain.host.HttpPort;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSObject;
import org.teavm.jso.core.JSPromise;
import org.teavm.jso.typedarrays.Int8Array;

import java.time.Duration;
import java.util.List;

public final class WebHttp implements HttpPort {

    private static final int WHEN_THE_SPEC_NAMES_NO_TIMEOUT = 30_000;

    private static final String NOTHING_WAS_REFUSED = "";

    @Override
    public Answer fetch(Request request) {
        JSObject answer = fetchTheUrl(
                request.url(),
                request.method(),
                request.headerNames().toArray(String[]::new),
                request.headerValues().toArray(String[]::new),
                asATypedArray(request.content()),
                request.followsRedirects(),
                (int) request.timeout().orElse(Duration.ofMillis(
                        WHEN_THE_SPEC_NAMES_NO_TIMEOUT)).toMillis())
                .await();
        String refused = readTheRefusalOf(answer);
        if (!NOTHING_WAS_REFUSED.equals(refused)) {
            throw new Refused("no-connect", refused, request.url());
        }
        return new Answer(
                statusOf(answer),
                linesOf(readTheNamesOf(answer)),
                linesOf(readTheValuesOf(answer)),
                asBytes(answer));
    }

    private static Int8Array asATypedArray(byte[] octets) {
        Int8Array array = new Int8Array(octets.length);
        for (int at = 0; at < octets.length; at++) {
            array.set(at, octets[at]);
        }
        return array;
    }

    private static byte[] asBytes(JSObject answer) {
        byte[] bytes = new byte[countTheOctetsOf(answer)];
        for (int at = 0; at < bytes.length; at++) {
            bytes[at] = readTheOctetAt(answer, at);
        }
        return bytes;
    }

    private static List<String> linesOf(String joined) {
        return joined.isEmpty() ? List.of() : List.of(joined.split("\n", -1));
    }

    @JSBody(params = "answer", script = "return answer.status;")
    private static native int statusOf(JSObject answer);

    @JSBody(params = "answer", script = "return answer.refused;")
    private static native String readTheRefusalOf(JSObject answer);

    @JSBody(params = "answer", script = "return answer.names;")
    private static native String readTheNamesOf(JSObject answer);

    @JSBody(params = "answer", script = "return answer.values;")
    private static native String readTheValuesOf(JSObject answer);

    @JSBody(params = "answer", script = "return answer.body.length;")
    private static native int countTheOctetsOf(JSObject answer);

    @JSBody(params = {"answer", "at"}, script = "return answer.body[at];")
    private static native byte readTheOctetAt(JSObject answer, int at);

    @JSBody(params = {"url", "method", "names", "values", "content", "followsRedirects", "milliseconds"},
            script = """
                    var stopped = new AbortController();
                    var timer = setTimeout(function () { stopped.abort(); }, milliseconds);
                    var headers = {};
                    for (var at = 0; at < names.length; at++) { headers[names[at]] = values[at]; }
                    var asked = {
                        method: method,
                        headers: headers,
                        redirect: followsRedirects ? 'follow' : 'manual',
                        signal: stopped.signal
                    };
                    if (content && content.length) { asked.body = content; }
                    return fetch(url, asked).then(function (response) {
                        return response.arrayBuffer().then(function (buffer) {
                            var flat = [];
                            response.headers.forEach(function (value, name) {
                                flat.push(name, value);
                            });
                            var split = { names: [], values: [] };
                            for (var at = 0; at + 1 < flat.length; at += 2) {
                                split.names.push(flat[at]);
                                split.values.push(flat[at + 1]);
                            }
                            return {
                                status: response.status,
                                refused: '',
                                names: split.names.join('\\n'),
                                values: split.values.join('\\n'),
                                body: new Int8Array(buffer)
                            };
                        });
                    }, function (problem) {
                        return {
                            status: 0,
                            refused: problem && problem.name === 'AbortError'
                                ? 'the host did not answer in time'
                                : 'cannot connect: ' + (problem && problem.message || problem),
                            names: '',
                            values: '',
                            body: new Int8Array()
                        };
                    }).then(function (answer) {
                        clearTimeout(timer);
                        return answer;
                    });
                    """)
    private static native JSPromise<JSObject> fetchTheUrl(
            String url, String method, String[] names, String[] values,
            Int8Array content, boolean followsRedirects, int milliseconds);
}
