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

    private static final String MAKING_THE_MODULE_AS_LOAD_BOOT_EXTS_DOES =
            "make module! sys/load-ext-module load-extension/dispatch the-source :the-dispatcher";

    private final Evaluator evaluator;

    private final SealedLibrary library;

    private final Map<String, Class<?>> handleKindsKnown = new HashMap<>();

    ExtensionInstallation(Evaluator evaluator, SealedLibrary library) {
        this.evaluator = evaluator;
        this.library = library;
    }

    ExtensionReport installed(Extension extension) {
        try {
            install(extension);
            return new ExtensionReport(extension.name(), Optional.empty());
        } catch (Raised refused) {
            return new ExtensionReport(extension.name(), Optional.of(refused.error().errorId()));
        }
    }

    private void install(Extension extension) {
        refuseWhatDoesNotRead(extension);
        refuseNamesTheLibraryHolds(extension);
        refuseHandleKindsKnownOtherwise(extension);
        start(extension);
        ModuleValue module = theModuleMadeOf(extension);
        extension.handleKinds().forEach(kind -> handleKindsKnown.put(kind.name(), kind.holds()));
        listInTheSystemsModules(extension.name(), module);
        extension.commands().forEach(command ->
                library.register(command.name(), module.context().slotFor(Context.canonicalise(command.name())).value()));
    }

    private void refuseWhatDoesNotRead(Extension extension) {
        boolean versionIsATuple = readsAsExactlyOne(extension.version()).filter(TupleValue.class::isInstance).isPresent();
        boolean everySpecReads = extension.commands().stream()
                .allMatch(command -> readsAsExactlyOne("[" + command.spec() + "]").isPresent());
        Set<String> distinct = extension.commands().stream()
                .map(command -> Context.canonicalise(command.name()))
                .collect(Collectors.toSet());
        if (extension.name().isBlank() || !versionIsATuple || !everySpecReads
                || distinct.size() != extension.commands().size()) {
            throw theExtensionIsBad(extension);
        }
    }

    private Optional<Value> readsAsExactlyOne(String source) {
        TranscodeResult read = evaluator.read(source);
        return read.values()
                .map(AnyBlockValue::remaining)
                .filter(values -> values.size() == 1)
                .map(List::getFirst);
    }

    private Raised theExtensionIsBad(Extension extension) {
        return Raised.of(EvaluationFailure.BAD_EXTENSION, WordValue.of(extension.name().isBlank() ? "extension" : extension.name()));
    }

    private void refuseNamesTheLibraryHolds(Extension extension) {
        extension.commands().stream()
                .map(ExtensionCommand::name)
                .filter(library::holds)
                .findFirst()
                .ifPresent(taken -> {
                    throw Raised.of(EvaluationFailure.ALREADY_USED, WordValue.of(taken));
                });
    }

    private void refuseHandleKindsKnownOtherwise(Extension extension) {
        Set<String> declaredHere = new HashSet<>();
        for (HandleKind kind : extension.handleKinds()) {
            Class<?> known = handleKindsKnown.get(kind.name());
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

    private ModuleValue theModuleMadeOf(Extension extension) {
        Context working = Context.childOf(evaluator.systemContext());
        working.register("the-source", BinaryValue.ofBytes(theSourceOf(extension).getBytes(StandardCharsets.UTF_8)));
        working.register("the-dispatcher", HandleValue.function(DispatcherHandle.THE_KIND_A_DISPATCHER_IS,
                evaluator.theNextExtensionsNumber(), JavaObjectValue.of(new CommandsOfAnExtension(extension))));
        TranscodeResult read = evaluator.read(MAKING_THE_MODULE_AS_LOAD_BOOT_EXTS_DOES);
        try {
            if (evaluator.evaluateOrRaise(Binder.bindAndDefine(read.values().orElseThrow(), working), working)
                    instanceof ModuleValue made) {
                return made;
            }
        } catch (Raised notAModule) {
            throw theExtensionIsBad(extension);
        }
        throw theExtensionIsBad(extension);
    }

    private String theSourceOf(Extension extension) {
        StringBuilder source = new StringBuilder("REBOL [name: ").append(extension.name())
                .append(" version: ").append(extension.version())
                .append(" type: module exports: [")
                .append(extension.commands().stream().map(ExtensionCommand::name).collect(Collectors.joining(" ")))
                .append("]]\n");
        extension.commands().forEach(command ->
                source.append(command.name()).append(": command [").append(command.spec()).append("]\n"));
        return source.append(extension.script()).append('\n').toString();
    }

    private void listInTheSystemsModules(String name, ModuleValue module) {
        if (evaluator.systemContext().valueAt("system", "modules") instanceof ObjectValue(Context modules)) {
            modules.register(name, module);
        }
    }
}
