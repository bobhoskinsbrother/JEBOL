package org.jebol.domain.host;

public sealed interface ChainVerdict {

    record Trusted(VouchedNames names) implements ChainVerdict {
    }

    record Mistrusted(CertificateMistrust why) implements ChainVerdict {
    }
}
