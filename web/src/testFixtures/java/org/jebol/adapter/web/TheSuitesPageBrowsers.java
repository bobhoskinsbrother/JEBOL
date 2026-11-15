package org.jebol.adapter.web;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;

public final class TheSuitesPageBrowsers implements ParameterResolver {

    private static final ExtensionContext.Namespace BROWSERS =
            ExtensionContext.Namespace.create(TheSuitesPageBrowsers.class);

    @Override
    public boolean supportsParameter(ParameterContext parameter, ExtensionContext context) {
        return parameter.getParameter().getType() == PageBrowsers.class;
    }

    @Override
    public PageBrowsers resolveParameter(ParameterContext parameter, ExtensionContext context) {
        return context.getRoot().getStore(BROWSERS)
                .getOrComputeIfAbsent(PageBrowsers.class, absent -> new PageBrowsers(), PageBrowsers.class);
    }
}
