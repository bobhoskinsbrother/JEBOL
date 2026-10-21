package org.jebol.domain.host;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

public interface HttpPort {

    record Request(
            String url,
            String method,
            List<String> headerNames,
            List<String> headerValues,
            byte[] content,
            boolean followsRedirects,
            Optional<Duration> timeout) {

        public Request {
            if (url == null || url.isBlank()) {
                throw new IllegalArgumentException("an HTTP request names a url");
            }
            if (method == null || method.isBlank()) {
                throw new IllegalArgumentException("an HTTP request names a method");
            }
            if (headerNames.size() != headerValues.size()) {
                throw new IllegalArgumentException(
                        "every header name has a value, and there are "
                                + headerNames.size() + " names and "
                                + headerValues.size() + " values");
            }
            headerNames = List.copyOf(headerNames);
            headerValues = List.copyOf(headerValues);
            content = content.clone();
        }
    }

    record Answer(int status, List<String> headerNames, List<String> headerValues, byte[] body) {

        public Answer {
            headerNames = List.copyOf(headerNames);
            headerValues = List.copyOf(headerValues);
            body = body.clone();
        }

        public Optional<String> readHeaderNamed(String name) {
            for (int at = 0; at < headerNames.size(); at++) {
                if (headerNames.get(at).equalsIgnoreCase(name)) {
                    return Optional.of(headerValues.get(at));
                }
            }
            return Optional.empty();
        }
    }

    Answer fetch(Request request);

    final class Refused extends RuntimeException {

        private static final long serialVersionUID = 1L;

        private final transient String errorId;
        private final transient String subject;

        public Refused(String errorId, String because, String subject) {
            super(because, null, false, false);
            this.errorId = errorId;
            this.subject = subject;
        }

        public String errorId() {
            return errorId;
        }

        public String subject() {
            return subject;
        }
    }

    static HttpPort none() {
        return request -> {
            throw new Refused("no-port",
                    "this interpreter was given no way to fetch", request.url());
        };
    }
}
