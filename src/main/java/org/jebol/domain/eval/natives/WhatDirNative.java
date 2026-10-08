package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.GrantedServices;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.host.HostService;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.FileValue;

import java.util.List;

public class WhatDirNative extends HostNative {

    public WhatDirNative(GrantedServices granted) {
        super(granted);
    }

    @Override
    public String nativeName() {
        return "what-dir";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of();
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            granted.require(HostService.WORKING_DIRECTORY);
            return throughTheFileSystem(() ->
                    FileValue.of(evaluator.files().workingDirectory()));
        };
    }
}
