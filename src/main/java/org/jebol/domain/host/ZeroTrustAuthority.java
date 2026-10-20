package org.jebol.domain.host;

import java.util.List;

public final class ZeroTrustAuthority implements CertificateAuthority {

    @Override
    public ChainVerdict judge(List<byte[]> chain) {
        return new ChainVerdict.Mistrusted(CertificateMistrust.UNKNOWN_AUTHORITY);
    }
}
