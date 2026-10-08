package org.jebol.domain.eval;

import org.jebol.domain.read.TranscodeResult;
import org.jebol.domain.read.Transcoder;
import org.jebol.domain.value.BlockValue;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.NativeValue;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.AnyStringValue;
import org.jebol.domain.value.StringValue;
import org.jebol.domain.value.Typeset;
import org.jebol.domain.value.Value;
import org.jebol.domain.value.WordValue;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class BootDeclarations {

    private static final int A_HEADER_IS_THE_WORD_REBOL_AND_ITS_BLOCK = 2;

    private static final BlockValue THE_SPEC_EVERY_DATATYPE_TEST_HAS =
            BlockValue.block(List.of(
                    StringValue.of("Returns TRUE if it is this type."),
                    WordValue.of("value"),
                    BlockValue.block(List.of(WordValue.of("any-type!")))));

    private String datatypeSpecSource = "";

    private Map<String, DatatypeSpec> datatypeSpecs;

    private String functionDeclarationSource = "";

    private Map<String, BlockValue> declaredSpecs;

    private String operatorTableSource = "";

    private String errorCatalogueSource = "";

    public List<Value> theRowsBelowTheHeaderOf(String source) {
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
        return theRowsBelowTheHeaderOf(operatorTableSource);
    }

    public List<Value> errorCatalogueRows() {
        return theRowsBelowTheHeaderOf(errorCatalogueSource);
    }

    public Optional<DatatypeSpec> specOf(Datatype datatype) {
        return Optional.ofNullable(datatypeSpecs().get(datatype.spelling()));
    }

    public BlockValue specOf(NativeValue built) {
        if (built.ownSpec().isPresent()) {
            return built.ownSpec().orElseThrow();
        }
        BlockValue declared = declaredSpecs().get(built.nativeName());
        if (declared != null) {
            return declared;
        }
        return specBlockOf(built.parameters());
    }

    public BlockValue theSpecEveryDatatypeTestHas() {
        return THE_SPEC_EVERY_DATATYPE_TEST_HAS;
    }

    private Map<String, DatatypeSpec> datatypeSpecs() {
        if (datatypeSpecs == null) {
            datatypeSpecs = Map.copyOf(specsReadFrom(datatypeSpecSource));
        }
        return datatypeSpecs;
    }

    private Map<String, DatatypeSpec> specsReadFrom(String source) {
        Map<String, DatatypeSpec> read = new LinkedHashMap<>();
        List<Value> values = theRowsBelowTheHeaderOf(source);
        for (int at = 0; at + 1 < values.size(); at++) {
            if (!(values.get(at) instanceof WordValue declaring)
                    || !(values.get(at + 1) instanceof BlockValue row)) {
                continue;
            }
            List<Value> said = row.remaining();
            if (said.size() >= 2
                    && said.get(0) instanceof AnyStringValue title
                    && said.get(1) instanceof WordValue category) {
                read.put(declaring.canonical(),
                        new DatatypeSpec(title.text(), category.canonical()));
            }
        }
        return read;
    }

    private Map<String, BlockValue> declaredSpecs() {
        if (declaredSpecs != null) {
            return declaredSpecs;
        }
        Map<String, BlockValue> found = new LinkedHashMap<>();
        try {
            TranscodeResult read = Transcoder.transcode(functionDeclarationSource);
            List<Value> values = read.values().map(BlockValue::remaining).orElse(List.of());
            for (int at = 0; at + 2 < values.size(); at++) {
                if (values.get(at) instanceof WordValue declaring
                        && declaring.datatype() == Datatype.SET_WORD
                        && values.get(at + 1) instanceof WordValue kind
                        && (kind.canonical().equals("native")
                                || kind.canonical().equals("action"))
                        && values.get(at + 2) instanceof BlockValue spec
                        && spec.datatype() == Datatype.BLOCK) {
                    found.putIfAbsent(declaring.canonical(), spec);
                }
            }
        } catch (RuntimeException unreadable) {
            found.clear();
        }
        declaredSpecs = Map.copyOf(found);
        return declaredSpecs;
    }

    private BlockValue specBlockOf(List<Parameter> parameters) {
        List<Value> spec = new ArrayList<>();
        for (Parameter parameter : parameters) {
            switch (parameter.kind()) {
                case REFINEMENT -> spec.add(
                        WordValue.of(parameter.name(), Datatype.REFINEMENT));
                case HARD_QUOTED -> spec.add(
                        WordValue.of(parameter.name(), Datatype.GET_WORD));
                case SOFT_QUOTED -> spec.add(
                        WordValue.of(parameter.name(), Datatype.LIT_WORD));
                case RETURN_TYPE -> spec.add(
                        WordValue.of("return", Datatype.SET_WORD));
                default -> spec.add(WordValue.of(parameter.name()));
            }
            if (!parameter.acceptedTypes().isEmpty()
                    && !parameter.acceptedTypes().equals(Typeset.ANY_TYPE.members())) {
                spec.add(BlockValue.block(parameter.acceptedTypes().stream()
                        .sorted(Comparator.comparing(Datatype::spelling))
                        .<Value>map(type -> WordValue.of(type.literalSpelling()))
                        .toList()));
            }
        }
        return BlockValue.block(spec);
    }
}
