package org.jebol.application;

import org.jebol.domain.eval.Binder;
import org.jebol.domain.eval.Evaluator;
import org.jebol.domain.eval.SealedLibrary;
import org.jebol.domain.eval.ports.Ports;
import org.jebol.domain.read.TranscodeResult;
import org.jebol.domain.value.AnyBlockValue;
import org.jebol.domain.value.AnyWordValue;
import org.jebol.domain.value.BinaryValue;
import org.jebol.domain.value.Context;
import org.jebol.domain.value.DispatcherHandle;
import org.jebol.domain.value.EvaluationFailure;
import org.jebol.domain.value.HandleValue;
import org.jebol.domain.value.JavaObjectValue;
import org.jebol.domain.value.ModuleValue;
import org.jebol.domain.value.ObjectValue;
import org.jebol.domain.value.Raised;
import org.jebol.domain.value.SetWordValue;
import org.jebol.domain.value.TupleValue;
import org.jebol.domain.value.Value;
import org.jebol.domain.value.WordValue;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
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

    private static final String STORE_DELAYED_EXTENSION_REBOL_STRING = """
            extension: load-extension/dispatch the-source :the-dispatcher
            set [header: body:] sys/load-header/only/required extension/lib-boot
            header/name: the-name
            append system/modules reduce [to set-word! the-name reduce [header extension]]""";

    private static final String HEADER_WORD = "rebol";

    private static final String EXPORTS_FIELD = "exports";

    private final Evaluator evaluator;

    private final SealedLibrary library;

    private final Ports ports;

    private final Map<String, Class<?>> knownTypes = new HashMap<>();

    ExtensionInstallation(Evaluator evaluator, SealedLibrary library, Ports ports) {
        this.evaluator = evaluator;
        this.library = library;
        this.ports = ports;
    }

    ExtensionReport installAndReport(Extension extension) {
        try {
            install(extension);
            return new ExtensionReport(extension.name(), Optional.empty());
        } catch (Raised refused) {
            return new ExtensionReport(extension.name(), Optional.of(refused.error().errorId()));
        }
    }

    void installBundled(Extension extension) {
        ExtensionReport report = installAndReport(extension);
        if (!report.installed()) {
            throw new IllegalStateException("the bundled " + extension.name() + " extension was refused: "
                    + report.refusal().orElseThrow());
        }
    }

    private void install(Extension extension) {
        refuseIfUnreadable(extension);
        List<String> namesForLib = extension.waitsForImport() ? readHeaderExports(extension) : listCommandNames(extension);
        refuseNamesLibHolds(namesForLib);
        refuseKnownHandleTypes(extension);
        start(extension);
        if (extension.waitsForImport()) {
            storeUntilImport(extension);
        } else {
            ModuleValue module = makeModule(extension);
            registerSystemModules(extension.name(), module);
            namesForLib.forEach(name ->
                    library.register(name, module.context().findSlotFor(Context.canonicalise(name)).value()));
        }
        extension.handleTypes().forEach(type -> knownTypes.put(type.name(), type.holds()));
    }

    private List<String> listCommandNames(Extension extension) {
        return extension.commands().stream().map(ExtensionCommand::name).toList();
    }

    private void refuseIfUnreadable(Extension extension) {
        boolean isTuple = readFirstValue(extension.version()).filter(TupleValue.class::isInstance).isPresent();
        boolean isEverySpecPresent = extension.commands().stream()
                .allMatch(command -> readFirstValue("[" + command.spec() + "]").isPresent());
        Set<String> distinctCommandNames = extension.commands().stream()
                .map(command -> Context.canonicalise(command.name()))
                .collect(Collectors.toSet());
        boolean isHeaderReadable = !extension.waitsForImport() || readHeaderFields(extension).isPresent();
        if (extension.name().isBlank() || !isTuple || !isEverySpecPresent || !isHeaderReadable ||
                distinctCommandNames.size() != extension.commands().size()) {
            throw refuseBadExtension(extension);
        }
    }

    private Optional<Value> readFirstValue(String source) {
        return readValues(source)
                .filter(values -> values.size() == 1)
                .map(List::getFirst);
    }

    private Optional<List<Value>> readValues(String source) {
        TranscodeResult read = evaluator.read(source);
        return read.values().map(AnyBlockValue::remaining);
    }

    private Optional<List<Value>> readHeaderFields(Extension extension) {
        return readValues(extension.header())
                .filter(values -> values.size() == 2
                        && values.getFirst() instanceof WordValue word
                        && word.canonical().equals(HEADER_WORD)
                        && values.getLast() instanceof AnyBlockValue)
                .map(values -> ((AnyBlockValue) values.getLast()).remaining());
    }

    private List<String> readHeaderExports(Extension extension) {
        List<Value> fields = readHeaderFields(extension).orElseThrow();
        List<String> exported = new ArrayList<>();
        for (int at = 0; at + 1 < fields.size(); at++) {
            if (fields.get(at) instanceof SetWordValue field && field.canonical().equals(EXPORTS_FIELD)
                    && fields.get(at + 1) instanceof AnyBlockValue words) {
                words.remaining().stream()
                        .filter(AnyWordValue.class::isInstance)
                        .map(word -> ((AnyWordValue) word).spelling())
                        .forEach(exported::add);
            }
        }
        return exported;
    }

    private Raised refuseBadExtension(Extension extension) {
        return Raised.of(EvaluationFailure.BAD_EXTENSION, WordValue.of(extension.name().isBlank() ? "extension" : extension.name()));
    }

    private void refuseNamesLibHolds(List<String> names) {
        names.stream()
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

    private void storeUntilImport(Extension extension) {
        Context context = makeDispatchingContext(extension, writeRebolSource(extension.header(), extension));
        context.register("the-name", WordValue.of(extension.name()));
        try {
            evaluateOrRaise(evaluator.read(STORE_DELAYED_EXTENSION_REBOL_STRING), context);
        } catch (Raised unloadable) {
            throw refuseBadExtension(extension);
        }
    }

    private ModuleValue makeModule(Extension extension) {
        Context context = makeDispatchingContext(extension, writeRebolSource(extension));
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

    private Context makeDispatchingContext(Extension extension, String source) {
        Context context = Context.makeChildOf(evaluator.systemContext());
        context.register("the-source", BinaryValue.ofBytes(source.getBytes(StandardCharsets.UTF_8)));
        context.register("the-dispatcher", HandleValue.wrapFunction(DispatcherHandle.KIND_DISPATCHER_IS,
                evaluator.numberNextExtension(), JavaObjectValue.of(new ExtensionCommands(extension, ports))));
        return context;
    }

    private Value evaluateOrRaise(TranscodeResult read, Context context) {
        return evaluator.evaluateOrRaise(Binder.bindAndDefine(read.values().orElseThrow(), context), context);
    }

    private String writeRebolSource(Extension extension) {
        String header = "REBOL [name: " + extension.name()
                + " version: " + extension.version()
                + " type: module exports: ["
                + extension.commands().stream().map(ExtensionCommand::name).collect(Collectors.joining(" "))
                + "]]";
        return writeRebolSource(header, extension);
    }

    private String writeRebolSource(String header, Extension extension) {
        StringBuilder source = new StringBuilder(header).append('\n');
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
