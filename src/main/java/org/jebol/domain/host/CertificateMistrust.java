package org.jebol.domain.host;

public enum CertificateMistrust {

    EXPIRED("a certificate for %s has expired"),

    NOT_YET_VALID("a certificate for %s is not valid yet"),

    UNKNOWN_AUTHORITY("no trusted authority vouches for %s"),

    BROKEN_CHAIN("the certificate chain for %s is broken"),

    NO_CERTIFICATE("%s sent no certificate"),

    WRONG_HOST("the certificate does not name %s");

    private final String template;

    CertificateMistrust(String template) {
        this.template = template;
    }

    public String explainTo(String hostName) {
        return template.formatted(hostName);
    }
}
