package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.Evaluator;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class VersionNative extends DefaultNative {

    private static final String VERSION_TEXT = "3.22.5";

    private static final int[] VERSION_PARTS = {3, 22, 5};

    private static final String THE_LANGUAGE = "Rebol";

    private static final String BETWEEN_LANGUAGE_AND_PRODUCT = "/";

    private static final String BETWEEN_FIELDS = " ";

    private static final String AN_UNKNOWN_FIELD = "none";

    private static final int BUILD_FIELDS_ONLY_A_C_BUILD_KNOWS = 11;

    @Override
    public String nativeName() {
        return "version";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of();
    }

    @Override
    public Set<String> refinementsDeclaredApart() {
        return Set.of("data");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> StringValue.of(refinements.contains("data")
                ? loadableDescription(evaluator)
                : THE_LANGUAGE + BETWEEN_LANGUAGE_AND_PRODUCT + theSystemSays(evaluator, "product")
                        + BETWEEN_FIELDS + VERSION_TEXT);
    }

    public Value numbered() {
        return TupleValue.of(VERSION_PARTS);
    }

    private String loadableDescription(Evaluator evaluator) {
        List<String> fields = new ArrayList<>(List.of(
                THE_LANGUAGE,
                theSystemSays(evaluator, "product"),
                VERSION_TEXT,
                theSystemSays(evaluator, "platform")));
        fields.addAll(Collections.nCopies(BUILD_FIELDS_ONLY_A_C_BUILD_KNOWS, AN_UNKNOWN_FIELD));
        return String.join(BETWEEN_FIELDS, fields);
    }

    private String theSystemSays(Evaluator evaluator, String field) {
        return Molder.form(evaluator.systemContext().valueAt("system", field));
    }
}
