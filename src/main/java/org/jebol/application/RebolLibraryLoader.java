package org.jebol.application;

import org.jebol.domain.eval.Binder;
import org.jebol.domain.eval.Evaluator;
import org.jebol.domain.eval.Outcome;
import org.jebol.domain.read.LibraryFile;
import org.jebol.domain.read.LibraryFileHeader;
import org.jebol.domain.read.TranscodeResult;
import org.jebol.domain.value.AnyBlockValue;
import org.jebol.domain.value.AnyWordValue;
import org.jebol.domain.value.BlockValue;
import org.jebol.domain.value.Context;
import org.jebol.domain.value.ErrorValue;
import org.jebol.domain.value.ModuleValue;
import org.jebol.domain.value.ObjectValue;
import org.jebol.domain.value.TextLines;
import org.jebol.domain.value.Value;
import org.jebol.domain.value.WordValue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

final class RebolLibraryLoader {

    private static final String DECLARATION = "the-declaration";
    private static final String BOOT_MEZZ = "boot-mezz";
    private static final String INTO_SYS = "-> sys";

    private final RebolFiles rebolFiles;
    private final Context systemContext;
    private final Context systemInternals;
    private final SystemPaths systemPaths;
    private final Consumer<String> runStep;
    private final Map<String, String> borrowedLoadFailures = new LinkedHashMap<>();
    private boolean schemesAreRegistered;

    RebolLibraryLoader(RebolFiles rebolFiles, Context systemContext,
                       Context systemInternals, Consumer<String> runStep) {
        this.rebolFiles = rebolFiles;
        this.systemContext = systemContext;
        this.systemInternals = systemInternals;
        this.systemPaths = new SystemPaths(systemContext);
        this.runStep = runStep;
    }

    void supplySymbolsAndModules(Evaluator evaluator) {
        evaluator.symbols().useBootSymbols(rebolFiles.bootSymbolsTxt().textSource());
        evaluator.useBundledModules(this::readBundledModule);
    }

    void declareSystemObject(Evaluator evaluator) {
        AnyBlockValue sysobjs = rebolFiles.sysobj().readLibraryFile().body();
        Context sysobjsContext = Context.makeChildOf(systemContext);
        declareSetWordsOf(sysobjs, sysobjsContext, AssignmentMayLand.HERE_ONLY_SHADOWING_WHATEVER_IS_ABOVE);
        AnyBlockValue systemObjects = rebolFiles.systemObject().readLibraryFile().body();
        Context systemObjectsContext = Context.makeChildOf(systemContext);
        systemObjectsContext.register(DECLARATION, withoutFileLineBreaks(Binder.bind(sysobjs, sysobjsContext)));
        declareSetWordsOf(systemObjects, systemObjectsContext, AssignmentMayLand.HERE_OR_IN_WHATEVER_IS_ABOVE);
        Outcome outcome = evaluator.evaluate(Binder.bind(systemObjects, systemObjectsContext), systemObjectsContext);
        if (outcome instanceof Outcome.Raised(ErrorValue failure)) {
            throw new IllegalStateException("the system object would not build from Rebol's declaration: " + failure);
        }
    }

    void load(Evaluator evaluator) {
        runBootStep(rebolFiles.modules());
        declareEverySystemWordBeforeBindingAny();
        declareEveryLibraryWordBeforeBindingAny();
        declareWordsPreludeSets();
        for (String entry : borrowedFileNames()) {
            loadBorrowed(evaluator, entry);
        }
        runBootStep(rebolFiles.qoiCodec());
    }

    void registerSchemes() {
        if (schemesAreRegistered || !systemInternals.contains("make-scheme")) {
            return;
        }
        schemesAreRegistered = true;
        runBootStep(rebolFiles.schemes());
    }

    void handOverToStart() {
        runBootStep(rebolFiles.handOverToStart());
        leaveBannerForStartToBuild();
    }

    void placeModulesDirectory() {
        runBootStep(rebolFiles.modulesDirectory());
    }

    Map<String, String> borrowedLoadFailures() {
        return Map.copyOf(borrowedLoadFailures);
    }

    private void loadBorrowed(Evaluator evaluator, String entry) {
        String name = readFileNameIn(entry);
        RebolFile borrowed = rebolFiles.mezzDir(name);
        if (borrowed.equals(rebolFiles.protMysql())) {
            registerSchemes();
        }
        if (!borrowed.exists()) {
            return;
        }
        TranscodeResult read = borrowed.readTranscoded();
        if (read.values().isEmpty()) {
            borrowedLoadFailures.put(name, read.error().orElseThrow().toString());
            return;
        }
        LibraryFile file = LibraryFile.readFrom(read.values().orElseThrow());
        LibraryFileHeader header = file.header();
        AnyBlockValue body = file.body();
        Outcome outcome = header.declaresModule() || isProtocolAndSoModuleWhateverItsHeaderSays(name)
                ? loadAsModule(evaluator, body, header)
                : entry.endsWith(INTO_SYS)
                        ? loadAsSystemFile(evaluator, body)
                        : loadInto(evaluator, body, systemContext);
        if (outcome instanceof Outcome.Raised(ErrorValue failure)) {
            borrowedLoadFailures.put(name, failure.toString());
        }
        if (borrowed.equals(rebolFiles.baseFuncs())) {
            loadPrelude(evaluator);
        }
    }

    private void runBootStep(RebolFile step) {
        runStep.accept(step.textSource());
    }

    private AnyBlockValue withoutFileLineBreaks(AnyBlockValue block) {
        for (int at = block.index(); at <= block.storageLength(); at++) {
            block.storage().setLineBreakAt(at, false);
        }
        for (Value each : block.remaining()) {
            if (each instanceof AnyBlockValue nested) {
                withoutFileLineBreaks(nested);
            }
        }
        return block;
    }

    private AnyBlockValue readPrelude() {
        return rebolFiles.prelude().readLibraryFile().body();
    }

    private void declareWordsPreludeSets() {
        declareSetWordsOf(readPrelude(), systemContext, AssignmentMayLand.HERE_OR_IN_WHATEVER_IS_ABOVE);
    }

    private void loadPrelude(Evaluator evaluator) {
        AnyBlockValue body = readPrelude();
        Outcome outcome = evaluator.evaluate(Binder.bind(body, systemContext), systemContext);
        if (outcome instanceof Outcome.Raised(ErrorValue failure)) {
            throw new IllegalStateException("the prelude failed to load: " + failure);
        }
    }

    private Outcome loadAsSystemFile(Evaluator evaluator, AnyBlockValue body) {
        declareSetWordsOf(body, systemInternals, AssignmentMayLand.HERE_ONLY_SHADOWING_WHATEVER_IS_ABOVE);
        return evaluator.evaluate(Binder.bind(body, systemInternals), systemInternals);
    }

    private void declareEveryLibraryWordBeforeBindingAny() {
        for (String entry : borrowedFileNames()) {
            String name = readFileNameIn(entry);
            if (entry.endsWith(INTO_SYS) || isProtocolAndSoModuleWhateverItsHeaderSays(name)) {
                continue;
            }
            RebolFile borrowed = rebolFiles.mezzDir(name);
            if (borrowed.isLibraryFile() && !borrowed.readLibraryFile().header().declaresModule()) {
                declareSetWordsOf(borrowed.readLibraryFile().body(), systemContext,
                        AssignmentMayLand.HERE_OR_IN_WHATEVER_IS_ABOVE);
            }
        }
    }

    private void declareEverySystemWordBeforeBindingAny() {
        for (String entry : borrowedFileNames()) {
            if (!entry.endsWith(INTO_SYS)) {
                continue;
            }
            RebolFile borrowed = rebolFiles.mezzDir(readFileNameIn(entry));
            if (borrowed.isLibraryFile()) {
                declareSetWordsOf(borrowed.readLibraryFile().body(), systemInternals,
                        AssignmentMayLand.HERE_ONLY_SHADOWING_WHATEVER_IS_ABOVE);
            }
        }
    }

    private enum AssignmentMayLand {
        HERE_ONLY_SHADOWING_WHATEVER_IS_ABOVE,
        HERE_OR_IN_WHATEVER_IS_ABOVE
    }

    private void declareSetWordsOf(AnyBlockValue body, Context into, AssignmentMayLand mayLand) {
        for (AnyWordValue word : body.setWordsFromHere()) {
            if (!isAlreadyAnswered(into, word.canonical(), mayLand)) {
                into.register(word.spelling());
            }
        }
    }

    private boolean isAlreadyAnswered(Context into, String canonical, AssignmentMayLand mayLand) {
        return mayLand == AssignmentMayLand.HERE_ONLY_SHADOWING_WHATEVER_IS_ABOVE
                ? into.holds(canonical)
                : into.contains(canonical);
    }

    private Outcome loadInto(Evaluator evaluator, AnyBlockValue body, Context target) {
        declareSetWordsOf(body, target, AssignmentMayLand.HERE_OR_IN_WHATEVER_IS_ABOVE);
        return evaluator.evaluate(Binder.bind(body, target), target);
    }

    private Outcome loadAsModule(Evaluator evaluator, AnyBlockValue body, LibraryFileHeader header) {
        Context own = Context.makeChildOf(systemContext);
        for (String exported : header.exportedNames()) {
            if (!own.holds(exported)) {
                own.register(exported);
            }
        }
        declareSetWordsOf(body, own, AssignmentMayLand.HERE_ONLY_SHADOWING_WHATEVER_IS_ABOVE);
        Outcome outcome = evaluator.evaluate(Binder.bind(body, own), own);
        if (outcome instanceof Outcome.Raised) {
            return outcome;
        }
        for (String exported : header.exportedNames()) {
            if (own.holds(exported)) {
                systemContext.register(exported, own.ownSlotFor(exported).value());
            }
        }
        registerModule(header, own);
        return outcome;
    }

    private void registerModule(LibraryFileHeader header, Context own) {
        String name = header.moduleName();
        if (name.isEmpty() || !(systemPaths.follow("system", "modules") instanceof ObjectValue(Context context))) {
            return;
        }
        Context spec = Context.root();
        spec.register("name", WordValue.of(name));
        spec.register("type", WordValue.of("module"));
        spec.register("exports", BlockValue.of(header.exportedNames().stream().<Value>map(WordValue::of).toList()));
        context.register(name, new ModuleValue(own, new ObjectValue(spec)));
    }

    private boolean isProtocolAndSoModuleWhateverItsHeaderSays(String fileName) {
        return fileName.startsWith("prot-");
    }

    private List<String> borrowedFileNames() {
        return TextLines.of(rebolFiles.orderTxt().textSource()).stream()
                .map(String::strip)
                .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                .toList();
    }

    private String readFileNameIn(String entry) {
        int marker = entry.indexOf("->");
        return marker < 0 ? entry : entry.substring(0, marker).strip();
    }

    private Optional<byte[]> readBundledModule(String name) {
        RebolFile module = rebolFiles.modulesDir(name);
        return module.exists() ? Optional.of(module.readBytes()) : Optional.empty();
    }

    private void leaveBannerForStartToBuild() {
        if (!(systemInternals.readValueAt(BOOT_MEZZ) instanceof AnyBlockValue protecting)) {
            return;
        }
        RebolFile banner = rebolFiles.mezzBanner();
        if (!banner.isLibraryFile()) {
            return;
        }
        List<Value> runFirst = new ArrayList<>(banner.readLibraryFile().body().remaining());
        runFirst.addAll(protecting.remaining());
        systemInternals.register(BOOT_MEZZ, BlockValue.of(runFirst));
    }
}
