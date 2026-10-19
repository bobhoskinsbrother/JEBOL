package org.jebol.application;

import org.jebol.domain.eval.Binder;
import org.jebol.domain.eval.Evaluator;
import org.jebol.domain.eval.SealedLibrary;
import org.jebol.domain.read.TranscodeResult;
import org.jebol.domain.value.AnyBlockValue;
import org.jebol.domain.value.BinaryValue;
import org.jebol.domain.value.Context;
import org.jebol.domain.value.DispatcherHandle;
import org.jebol.domain.value.EvaluationFailure;
import org.jebol.domain.value.HandleValue;
import org.jebol.domain.value.JavaObjectValue;
import org.jebol.domain.value.ModuleValue;
import org.jebol.domain.value.ObjectValue;
import org.jebol.domain.value.Raised;
import org.jebol.domain.value.TupleValue;
import org.jebol.domain.value.Value;
import org.jebol.domain.value.WordValue;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

final class ExtensionInstallation {

    private static final String LOAD_BOOT_EXTENSIONS_REBOL_STRING =
            "make module! sys/load-ext-module load-extension/dispatch the-source :the-dispatcher";

    private final Evaluator evaluator;

    private final SealedLibrary library;

    private final Map<String, Class<?>> knownTypes = new HashMap<>();

    ExtensionInstallation(Evaluator evaluator, SealedLibrary library) {
        this.evaluator = evaluator;
        this.library = library;
    }

    ExtensionReport installAndReport(Extension extension) {
        try {
            install(extension);
            return new ExtensionReport(extension.name(), Optional.empty());
        } catch (Raised refused) {
            return new ExtensionReport(extension.name(), Optional.of(refused.error().errorId()));
        }
    }

    private void install(Extension extension) {
        refuseIfUnreadable(extension);
        refuseRegisteredLibraryExtension(extension);
        refuseKnownHandleTypes(extension);
        start(extension);
        ModuleValue module = makeModule(extension);
        extension.handleTypes().forEach(type -> knownTypes.put(type.name(), type.holds()));
        registerSystemModules(extension.name(), module);
        extension.commands().forEach(command ->
                library.register(command.name(), module.context().findSlotFor(Context.canonicalise(command.name())).value()));
    }

    private void refuseIfUnreadable(Extension extension) {
        boolean isTuple = readFirstValue(extension.version()).filter(TupleValue.class::isInstance).isPresent();
        boolean isEverySpecPresent = extension.commands().stream()
                .allMatch(command -> readFirstValue("[" + command.spec() + "]").isPresent());
        Set<String> distinctCommandNames = extension.commands().stream()
                .map(command -> Context.canonicalise(command.name()))
                .collect(Collectors.toSet());
        if (extension.name().isBlank() || !isTuple || !isEverySpecPresent ||
                distinctCommandNames.size() != extension.commands().size()) {
            throw refuseBadExtension(extension);
        }
    }

    private Optional<Value> readFirstValue(String source) {
        TranscodeResult read = evaluator.read(source);
        return read.values()
                .map(AnyBlockValue::remaining)
                .filter(values -> values.size() == 1)
                .map(List::getFirst);
    }

    private Raised refuseBadExtension(Extension extension) {
        return Raised.of(EvaluationFailure.BAD_EXTENSION, WordValue.of(extension.name().isBlank() ? "extension" : extension.name()));
    }

    private void refuseRegisteredLibraryExtension(Extension extension) {
        extension.commands().stream()
                .map(ExtensionCommand::name)
                .filter(library::holds)
                .findFirst()
                .ifPresent(taken -> {
                    throw Raised.of(EvaluationFailure.ALREADY_USED, WordValue.of(taken));
                });
    }

    private void refuseKnownHandleTypes(Extension extension) {
        Set<String> declaredHere = new HashSet<>();
        for (HandleType kind : extension.handleTypes()) {
            Class<?> known = knownTypes.get(kind.name());
            if (known != null && !known.equals(kind.holds()) || !declaredHere.add(kind.name())) {
                throw Raised.of(EvaluationFailure.HANDLE_EXISTS, WordValue.of(kind.name()));
            }
        }
    }

    private void start(Extension extension) {
        try {
            extension.start().start(evaluator.postbox());
        } catch (RuntimeException failed) {
            throw Raised.of(EvaluationFailure.EXTENSION_INIT, WordValue.of(extension.name()));
        }
    }

    private ModuleValue makeModule(Extension extension) {
        Context context = Context.makeChildOf(evaluator.systemContext());
        context.register("the-source", BinaryValue.ofBytes(writeRebolSourceFor(extension).getBytes(StandardCharsets.UTF_8)));
        context.register("the-dispatcher", HandleValue.wrapAFunction(DispatcherHandle.THE_KIND_A_DISPATCHER_IS,
                evaluator.numberTheNextExtension(), JavaObjectValue.of(new ExtensionCommands(extension))));
        TranscodeResult read = evaluator.read(LOAD_BOOT_EXTENSIONS_REBOL_STRING);
        try {
            if (evaluateOrRaise(read, context) instanceof ModuleValue made) {
                return made;
            }
        } catch (Raised ignored) {
            throw refuseBadExtension(extension);
        }
        throw refuseBadExtension(extension);
    }

    private Value evaluateOrRaise(TranscodeResult read, Context context) {
        return evaluator.evaluateOrRaise(Binder.bindAndDefine(read.values().orElseThrow(), context), context);
    }

    private String writeRebolSourceFor(Extension extension) {
        StringBuilder source = new StringBuilder("REBOL [name: ").append(extension.name())
                .append(" version: ").append(extension.version())
                .append(" type: module exports: [")
                .append(extension.commands().stream().map(ExtensionCommand::name).collect(Collectors.joining(" ")))
                .append("]]\n");
        extension.commands().forEach(command ->
                source.append(command.name()).append(": command [").append(command.spec()).append("]\n"));
        return source.append(extension.script()).append('\n').toString();
    }

    private void registerSystemModules(String name, ModuleValue module) {
        if (evaluator.systemContext().readValueAt("system", "modules") instanceof ObjectValue(Context modules)) {
            modules.register(name, module);
        }
    }
}
