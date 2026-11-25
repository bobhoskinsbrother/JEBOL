package org.jebol.android;

import org.junit.jupiter.api.extension.DynamicTestInvocationContext;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.InvocationInterceptor;
import org.junit.jupiter.api.extension.ReflectiveInvocationContext;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicReference;

public final class OnTheInterpretersStack implements InvocationInterceptor {

    @Override
    public void interceptTestMethod(Invocation<Void> invocation,
            ReflectiveInvocationContext<Method> invocationContext, ExtensionContext extensionContext) throws Throwable {
        proceedOnInterpreterThread(invocation, extensionContext);
    }

    @Override
    public void interceptTestTemplateMethod(Invocation<Void> invocation,
            ReflectiveInvocationContext<Method> invocationContext, ExtensionContext extensionContext) throws Throwable {
        proceedOnInterpreterThread(invocation, extensionContext);
    }

    @Override
    public void interceptDynamicTest(Invocation<Void> invocation, DynamicTestInvocationContext invocationContext,
            ExtensionContext extensionContext) throws Throwable {
        proceedOnInterpreterThread(invocation, extensionContext);
    }

    private static void proceedOnInterpreterThread(Invocation<Void> invocation, ExtensionContext extensionContext)
            throws Throwable {
        AtomicReference<Throwable> thrown = new AtomicReference<>();
        Thread running = InterpreterThreads.start("jebol-test " + extensionContext.getDisplayName(), () -> {
            try {
                invocation.proceed();
            } catch (Throwable failed) {
                thrown.set(failed);
            }
        });
        try {
            running.join();
        } catch (InterruptedException stopped) {
            running.interrupt();
            running.join();
            throw stopped;
        }
        if (thrown.get() != null) {
            throw thrown.get();
        }
    }
}
