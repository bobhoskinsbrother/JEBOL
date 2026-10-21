package org.jebol.domain.eval;

import org.jebol.domain.read.TranscodeResult;
import org.jebol.domain.read.Transcoder;
import org.jebol.domain.value.AnyBlockValue;
import org.jebol.domain.value.BlockValue;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.NativeValue;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.AnyStringValue;
import org.jebol.domain.value.StringValue;
import org.jebol.domain.value.TypesetValue;
import org.jebol.domain.value.Value;
import org.jebol.domain.value.AnyWordValue;
import org.jebol.domain.value.GetWordValue;
import org.jebol.domain.value.LitWordValue;
import org.jebol.domain.value.RefinementValue;
import org.jebol.domain.value.SetWordValue;
import org.jebol.domain.value.WordValue;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class BootDeclarations {

    private static final int A_HEADER_IS_THE_WORD_REBOL_AND_ITS_BLOCK = 2;

    private static final AnyBlockValue THE_SPEC_EVERY_DATATYPE_TEST_HAS =
            BlockValue.of(List.of(
                    StringValue.of("Returns TRUE if it is this type."),
                    WordValue.of("value"),
                    BlockValue.of(List.of(WordValue.of("any-type!")))));

    private String datatypeSpecSource = "";

    private Map<String, DatatypeSpec> datatypeSpecs;

    private String functionDeclarationSource = "";

    private Map<String, AnyBlockValue> declaredSpecs;

    private String operatorTableSource = "";

    private String errorCatalogueSource = "";

    public List<Value> readTheRowsBelowTheHeaderOf(String source) {
        try {
            List<Value> values =
                    Transcoder.transcode(source).values().orElseThrow().remaining();
            return values.subList(
                    Math.min(A_HEADER_IS_THE_WORD_REBOL_AND_ITS_BLOCK, values.size()),
                    values.size());
        } catch (RuntimeException unreadable) {
            return List.of();
        }
    }

    public void useDatatypeSpecs(String source) {
        this.datatypeSpecSource = source;
        this.datatypeSpecs = null;
    }

    public void useFunctionDeclarations(String source) {
        this.functionDeclarationSource = source;
        this.declaredSpecs = null;
    }

    public void useOperatorTable(String source) {
        this.operatorTableSource = source;
    }

    public void useErrorCatalogue(String source) {
        this.errorCatalogueSource = source;
    }

    public List<Value> operatorRows() {
        return readTheRowsBelowTheHeaderOf(operatorTableSource);
    }

    public List<Value> errorCatalogueRows() {
        return readTheRowsBelowTheHeaderOf(errorCatalogueSource);
    }

    public Optional<DatatypeSpec> findTheSpecOf(Datatype datatype) {
        return Optional.ofNullable(readTheDatatypeSpecs().get(datatype.spelling()));
    }

    public AnyBlockValue findTheSpecOf(NativeValue built) {
        if (built.ownSpec().isPresent()) {
            return built.ownSpec().orElseThrow();
        }
        AnyBlockValue declared = readTheDeclaredSpecs().get(built.nativeName());
        if (declared != null) {
            return declared;
        }
        return makeTheSpecBlockOf(built.parameters());
    }

    public AnyBlockValue makeSpecEveryDatatypeTestHas() {
        return THE_SPEC_EVERY_DATATYPE_TEST_HAS;
    }

    private Map<String, DatatypeSpec> readTheDatatypeSpecs() {
        if (datatypeSpecs == null) {
            datatypeSpecs = Map.copyOf(readTheSpecsFrom(datatypeSpecSource));
        }
        return datatypeSpecs;
    }

    private Map<String, DatatypeSpec> readTheSpecsFrom(String source) {
        Map<String, DatatypeSpec> read = new LinkedHashMap<>();
        List<Value> values = readTheRowsBelowTheHeaderOf(source);
        for (int at = 0; at + 1 < values.size(); at++) {
            if (!(values.get(at) instanceof AnyWordValue declaring)
                    || !(values.get(at + 1) instanceof AnyBlockValue row)) {
                continue;
            }
            List<Value> said = row.remaining();
            if (said.size() >= 2
                    && said.get(0) instanceof AnyStringValue title
                    && said.get(1) instanceof AnyWordValue category) {
                read.put(declaring.canonical(),
                        new DatatypeSpec(title.text(), category.canonical()));
            }
        }
        return read;
    }

    private Map<String, AnyBlockValue> readTheDeclaredSpecs() {
        if (declaredSpecs != null) {
            return declaredSpecs;
        }
        Map<String, AnyBlockValue> found = new LinkedHashMap<>();
        try {
            TranscodeResult read = Transcoder.transcode(functionDeclarationSource);
            List<Value> values = read.values().map(AnyBlockValue::remaining).orElse(List.of());
            for (int at = 0; at + 2 < values.size(); at++) {
                if (values.get(at) instanceof SetWordValue declaring
                        && values.get(at + 1) instanceof AnyWordValue kind
                        && (kind.canonical().equals("native")
                                || kind.canonical().equals("action"))
                        && values.get(at + 2) instanceof BlockValue spec) {
                    found.putIfAbsent(declaring.canonical(), spec);
                }
            }
        } catch (RuntimeException unreadable) {
            found.clear();
        }
        declaredSpecs = Map.copyOf(found);
        return declaredSpecs;
    }

    private AnyBlockValue makeTheSpecBlockOf(List<Parameter> parameters) {
        List<Value> spec = new ArrayList<>();
        for (Parameter parameter : parameters) {
            switch (parameter.kind()) {
                case REFINEMENT -> spec.add(RefinementValue.of(parameter.name()));
                case HARD_QUOTED -> spec.add(GetWordValue.of(parameter.name()));
                case SOFT_QUOTED -> spec.add(LitWordValue.of(parameter.name()));
                case RETURN_TYPE -> spec.add(SetWordValue.of("return"));
                default -> spec.add(WordValue.of(parameter.name()));
            }
            if (!parameter.acceptedTypes().isEmpty()
                    && !parameter.acceptedTypes().equals(TypesetValue.ANY_TYPE.members())) {
                spec.add(BlockValue.of(parameter.acceptedTypes().stream()
                        .sorted(Comparator.comparing(Datatype::spelling))
                        .<Value>map(type -> WordValue.of(type.literalSpelling()))
                        .toList()));
            }
        }
        return BlockValue.of(spec);
    }
}
