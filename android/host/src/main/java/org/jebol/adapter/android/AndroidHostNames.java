package org.jebol.adapter.android;

import android.icu.text.IDNA;

import org.jebol.domain.host.HostNames;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

public final class AndroidHostNames implements HostNames {

    private static final Set<IDNA.Error> CHECKS_A_BROWSER_LEAVES_OFF = EnumSet.of(
            IDNA.Error.HYPHEN_3_4, IDNA.Error.LEADING_HYPHEN, IDNA.Error.TRAILING_HYPHEN,
            IDNA.Error.LABEL_TOO_LONG, IDNA.Error.DOMAIN_NAME_TOO_LONG, IDNA.Error.EMPTY_LABEL);

    private static final IDNA NONTRANSITIONAL = IDNA.getUTS46Instance(
            IDNA.NONTRANSITIONAL_TO_ASCII | IDNA.NONTRANSITIONAL_TO_UNICODE | IDNA.CHECK_BIDI | IDNA.CHECK_CONTEXTJ);

    @Override
    public Optional<String> asciiHostName(String hostName) {
        return HostNames.keepUsableAsHostName(toAscii(hostName));
    }

    public Optional<String> toAscii(String domain) {
        IDNA.Info info = new IDNA.Info();
        StringBuilder ascii = new StringBuilder();
        NONTRANSITIONAL.nameToASCII(domain, ascii, info);
        Set<IDNA.Error> errors = EnumSet.noneOf(IDNA.Error.class);
        errors.addAll(info.getErrors());
        errors.removeAll(CHECKS_A_BROWSER_LEAVES_OFF);
        return errors.isEmpty() ? Optional.of(ascii.toString()) : Optional.empty();
    }
}
