package org.jebol.domain.host;

import java.util.List;
import java.util.Optional;

@FunctionalInterface
public interface ServerCheck {

    Optional<CertificateMistrust> judge(List<byte[]> chain);
}
