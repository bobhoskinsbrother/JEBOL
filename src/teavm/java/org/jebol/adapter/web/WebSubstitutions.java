package org.jebol.adapter.web;

import org.teavm.extension.spi.substitution.SimpleSubstitutionPolicy;
import org.teavm.extension.spi.substitution.SubstitutionSink;

public class WebSubstitutions extends SimpleSubstitutionPolicy {

    @Override
    public void contribute(SubstitutionSink sink) {
        sink.selectClasses(inPackage("javax.crypto", true))
                .packagePrefix("org.teavm.classlib.")
                .simpleNamePrefix("T");
        sink.selectClasses(named("java.security.InvalidKeyException"))
                .packagePrefix("org.teavm.classlib.")
                .simpleNamePrefix("T");
        sink.selectClasses(named("org.jebol.application.ExecutableFile"))
                .simpleNamePrefix("Web");
        sink.selectClasses(named("org.jebol.domain.host.DigestPorts"))
                .simpleNamePrefix("Web");
        sink.selectClasses(named("org.jebol.domain.eval.compression.brotli.FusedMultiplyAdd"))
                .simpleNamePrefix("Exact");
    }
}
