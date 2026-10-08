package org.jebol.domain.eval;

import org.jebol.domain.eval.crypto.EllipticCurveKey;
import org.jebol.domain.eval.natives.CipherNative;
import org.jebol.domain.eval.natives.ResizeNative;
import org.jebol.domain.eval.ports.Ports;
import org.jebol.domain.value.*;

import java.util.Arrays;
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
            MapValue registeredStructLayouts, String operatingSystemName) {
        this.lib = lib;
        this.internals = Context.childOf(lib.context());
        this.declarations = declarations;
        this.ports = ports;
        this.encodings = encodings;
        this.registeredStructLayouts = registeredStructLayouts;
        lib.context().register("system", system(operatingSystemName));
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

    private ObjectValue system(String operatingSystemName) {
        Context system = Context.root();
        system.register("catalog", catalog());
        system.register("options", options());
        system.register("state", state());
        system.register("version", lib.version().numbered());
        system.register("platform", WordValue.of(operatingSystemName));
        system.register("product", WordValue.of("core"));
        system.register("license", NoneValue.none());
        system.register("modules", modules());
        system.register("codecs", codecs());
        system.register("contexts", contexts());
        return new ObjectValue(system);
    }

    private Value contexts() {
        Context contexts = Context.root();
        contexts.register("lib", new ObjectValue(lib.context()));
        contexts.register("sys", new ObjectValue(internals));
        contexts.register("root", NoneValue.none());
        return new ObjectValue(contexts);
    }

    private Value codecs() {
        Context codecs = Context.root();
        for (int at = 0; at < Codecs.REGISTERED.size(); at++) {
            String codec = Codecs.REGISTERED.get(at);
            codecs.register(codec, HandleValue.function("codec", CODEC_HANDLE_IDENTITY + at, WordValue.of(codec)));
        }
        return new ObjectValue(codecs);
    }

    private Value modules() {
        Context modules = Context.root();
        modules.register("help", NoneValue.none());
        return new ObjectValue(modules);
    }

    private Value state() {
        Context policies = Context.root();
        for (String policy : new String[]{"file", "net", "eval", "memory", "secure", "protect", "debug", "envr", "call", "browse", "extension"}) {
            policies.register(policy, TupleValue.of(0, 0, 0));
        }
        runState.register("policies", new ObjectValue(policies));
        for (String field : new String[]{"note", "confirm-policy", "control?", "shift?", "alt?", "quit?"}) {
            runState.register(field, NoneValue.none());
        }
        runState.register("wait-list", BlockValue.block(List.of()));
        runState.register("last-error", NoneValue.none());
        runState.register("last-result", NoneValue.none());
        return new ObjectValue(runState);
    }

    private Value options() {
        Context options = Context.root();
        for (String field : new String[]{"boot", "path", "home", "data", "modules", "flags", "script", "args", "do-arg", "import", "debug", "secure", "version", "boot-level", "domain-name", "module-paths", "result-types"}) {
            options.register(field, NoneValue.none());
        }
        options.register("flags", BlockValue.block(List.of(LogicValue.yes())));
        options.register("home", FileValue.of(System.getProperty("user.home", "") + "/"));
        options.register("boot", NoneValue.none());
        options.register("path", FileValue.of(System.getProperty("user.dir", "") + "/"));
        options.register("data", FileValue.of(System.getProperty("user.home", "") + "/.jebol/"));
        return new ObjectValue(options);
    }

    private Value errors() {
        Context errors = Context.root();
        List<Value> catalogued = declarations.errorCatalogueRows();
        for (int at = 0; at + 1 < catalogued.size(); at += 2) {
            if (!(catalogued.get(at) instanceof WordValue category) || category.datatype() != Datatype.SET_WORD || !(catalogued.get(at + 1) instanceof BlockValue body)) {
                continue;
            }
            Context inside = Context.root();
            List<Value> fields = body.remaining();
            for (int pair = 0; pair + 1 < fields.size(); pair += 2) {
                if (fields.get(pair) instanceof WordValue name && name.datatype() == Datatype.SET_WORD) {
                    inside.register(name.spelling(), fields.get(pair + 1));
                }
            }
            errors.register(category.spelling(), new ObjectValue(inside));
        }
        return new ObjectValue(errors);
    }

    private Value catalog() {
        Context catalog = Context.root();
        catalog.register("datatypes", BlockValue.block(Arrays.stream(Datatype.values()).map(datatype -> (Value) DatatypeValue.of(datatype)).toList()));
        catalog.register("structs", registeredStructLayouts);
        catalog.register("actions", lib.actionsInOrder());
        catalog.register("natives", lib.nativesInOrder());
        catalog.register("ciphers", BlockValue.block(ports.cryptPort().catalogue()));
        catalog.register("filters", BlockValue.block(ResizeNative.THE_FILTERS.stream().<Value>map(WordValue::of).toList()));
        catalog.register("elliptic-curves", BlockValue.block(EllipticCurveKey.curveNamesInTheCataloguesOrder().stream().<Value>map(WordValue::of).toList()));
        catalog.register("handles", BlockValue.block(List.of(WordValue.of(CipherNative.RC4_HANDLE_TYPE), WordValue.of(CipherNative.DHM_HANDLE_TYPE), WordValue.of(CipherNative.RSA_HANDLE_TYPE), WordValue.of(CipherNative.ECDH_HANDLE_TYPE), WordValue.of("codec"))));
        catalog.register("checksums", BlockValue.block(encodings.checksumMethods().stream().<Value>map(WordValue::of).toList()));
        catalog.register("compressions", BlockValue.block(Encodings.COMPRESSIONS.stream().<Value>map(WordValue::of).toList()));
        catalog.register("file-types", BlockValue.block(List.of(FileValue.of(".txt"), WordValue.of("text"), FileValue.of(".html"), WordValue.of("markup"), FileValue.of(".htm"), WordValue.of("markup"), FileValue.of(".qoi"), WordValue.of("qoi"))));
        catalog.register("errors", errors());
        return new ObjectValue(catalog);
    }
}
