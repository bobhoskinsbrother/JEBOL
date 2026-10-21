package org.jebol.domain.eval;

import org.jebol.domain.eval.crypto.EllipticCurveKey;
import org.jebol.domain.eval.natives.CipherNative;
import org.jebol.domain.eval.natives.ResizeNative;
import org.jebol.domain.eval.ports.Ports;
import org.jebol.domain.host.MachinePort;
import org.jebol.domain.value.*;

import java.util.List;

public final class SystemObject {

    private static final int CODEC_HANDLE_IDENTITY = 1000;

    private final LibContext lib;
    private final Context internals;
    private final Context runState = Context.root();
    private final BootDeclarations declarations;
    private final Ports ports;
    private final Encodings encodings;
    private final MapValue registeredStructLayouts;

    public SystemObject(LibContext lib, BootDeclarations declarations, Ports ports, Encodings encodings,
            MapValue registeredStructLayouts, MachinePort machine) {
        this.lib = lib;
        this.internals = Context.makeChildOf(lib.context());
        this.declarations = declarations;
        this.ports = ports;
        this.encodings = encodings;
        this.registeredStructLayouts = registeredStructLayouts;
        lib.context().register("system", makeSystem(machine));
    }

    public Context lib() {
        return lib.context();
    }

    public Context internals() {
        return internals;
    }

    public void forgetStartupState() {
        runState.register("last-error", NoneValue.none());
        runState.register("last-result", NoneValue.none());
    }

    private ObjectValue makeSystem(MachinePort machine) {
        Context system = Context.root();
        system.register("catalog", makeCatalog());
        system.register("options", makeOptions(machine));
        system.register("state", makeState());
        system.register("version", lib.version().numbered());
        system.register("platform", WordValue.of(machine.operatingSystem()));
        system.register("product", WordValue.of("core"));
        system.register("license", NoneValue.none());
        system.register("modules", makeModules());
        system.register("codecs", makeCodecs());
        system.register("contexts", makeContexts());
        return new ObjectValue(system);
    }

    private Value makeContexts() {
        Context contexts = Context.root();
        contexts.register("lib", new ObjectValue(lib.context()));
        contexts.register("sys", new ObjectValue(internals));
        contexts.register("root", NoneValue.none());
        return new ObjectValue(contexts);
    }

    private Value makeCodecs() {
        Context codecs = Context.root();
        for (int at = 0; at < Codecs.REGISTERED.size(); at++) {
            String codec = Codecs.REGISTERED.get(at);
            codecs.register(codec, HandleValue.wrapFunction("codec", CODEC_HANDLE_IDENTITY + at, WordValue.of(codec)));
        }
        return new ObjectValue(codecs);
    }

    private Value makeModules() {
        Context modules = Context.root();
        modules.register("help", NoneValue.none());
        return new ObjectValue(modules);
    }

    private Value makeState() {
        Context policies = Context.root();
        for (String policy : new String[]{"file", "net", "eval", "memory", "secure", "protect", "debug", "envr", "call", "browse", "extension"}) {
            policies.register(policy, TupleValue.of(0, 0, 0));
        }
        runState.register("policies", new ObjectValue(policies));
        for (String field : new String[]{"note", "confirm-policy", "control?", "shift?", "alt?", "quit?"}) {
            runState.register(field, NoneValue.none());
        }
        runState.register("wait-list", BlockValue.of(List.of()));
        runState.register("last-error", NoneValue.none());
        runState.register("last-result", NoneValue.none());
        return new ObjectValue(runState);
    }

    private Value makeOptions(MachinePort machine) {
        Context options = Context.root();
        String[] fields = {"boot", "path", "home", "data", "modules", "flags", "script", "args", "do-arg",
                "import", "debug", "secure", "version", "boot-level", "domain-name", "module-paths", "result-types"};
        for (String field : fields) {
            options.register(field, NoneValue.none());
        }
        options.register("flags", BlockValue.of(List.of(LogicValue.yes())));
        String home = asDirectory(machine.homeDirectory());
        options.register("home", FileValue.of(home));
        options.register("boot", NoneValue.none());
        options.register("path", FileValue.of(asDirectory(machine.currentDirectory())));
        options.register("data", FileValue.of(home + ".jebol/"));
        return new ObjectValue(options);
    }

    private String asDirectory(String path) {
        return path.endsWith("/") ? path : path + "/";
    }

    private Value makeErrors() {
        Context errors = Context.root();
        List<Value> catalogued = declarations.errorCatalogueRows();
        for (int at = 0; at + 1 < catalogued.size(); at += 2) {
            if (!(catalogued.get(at) instanceof SetWordValue category) || !(catalogued.get(at + 1) instanceof AnyBlockValue body)) {
                continue;
            }
            Context inside = Context.root();
            List<Value> fields = body.remaining();
            for (int pair = 0; pair + 1 < fields.size(); pair += 2) {
                if (fields.get(pair) instanceof SetWordValue name) {
                    inside.register(name.spelling(), fields.get(pair + 1));
                }
            }
            errors.register(category.spelling(), new ObjectValue(inside));
        }
        return new ObjectValue(errors);
    }

    private Value makeCatalog() {
        Context catalog = Context.root();
        catalog.register("datatypes", BlockValue.of(List.copyOf(Catalogue.DATATYPES.entries())));
        catalog.register("structs", registeredStructLayouts);
        catalog.register("actions", lib.actionsInOrder());
        catalog.register("natives", lib.nativesInOrder());
        catalog.register("ciphers", BlockValue.of(ports.cryptPort().catalogue()));
        catalog.register("filters", BlockValue.of(ResizeNative.THE_FILTERS.stream().<Value>map(WordValue::of).toList()));
        catalog.register("elliptic-curves", BlockValue.of(EllipticCurveKey.curveNamesInTheCataloguesOrder().stream().<Value>map(WordValue::of).toList()));
        catalog.register("handles", BlockValue.of(List.of(WordValue.of(CipherNative.RC4_HANDLE_TYPE), WordValue.of(CipherNative.DHM_HANDLE_TYPE), WordValue.of(CipherNative.RSA_HANDLE_TYPE), WordValue.of(CipherNative.ECDH_HANDLE_TYPE), WordValue.of("codec"))));
        catalog.register("checksums", BlockValue.of(encodings.listTheChecksumMethods().stream().<Value>map(WordValue::of).toList()));
        catalog.register("compressions", BlockValue.of(Encodings.COMPRESSIONS.stream().<Value>map(WordValue::of).toList()));
        catalog.register("file-types", BlockValue.of(List.of(FileValue.of(".txt"), WordValue.of("text"), FileValue.of(".html"), WordValue.of("markup"), FileValue.of(".htm"), WordValue.of("markup"), FileValue.of(".qoi"), WordValue.of("qoi"))));
        catalog.register("errors", makeErrors());
        return new ObjectValue(catalog);
    }
}
