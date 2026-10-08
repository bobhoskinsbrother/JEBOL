package org.jebol.domain.eval.actions;

import org.jebol.domain.eval.BootDeclarations;
import org.jebol.domain.eval.DatatypeSpec;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.eval.VectorQuery;
import org.jebol.domain.value.*;

import java.util.List;
import java.util.Set;

public class ReflectAction extends DefaultNative implements ActionValue {

    private final BootDeclarations declarations;

    public ReflectAction(BootDeclarations declarations) {
        this.declarations = declarations;
    }

    @Override
    public String nativeName() {
        return "reflect";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(Parameter.required("value"),
                Parameter.required("field", Set.of(Datatype.WORD)));
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) ->
                reflected(arguments.getFirst(), (WordValue) arguments.get(1));
    }

    private Value reflected(Value subject, WordValue field) {
        return switch (subject) {
            case UnsetValue nothing -> throw Raised.cannotUse(nothing, this);
            case VectorValue vector -> reflectedFrom(vector, field);
            case DatatypeValue(Datatype represents) -> declarations.specOf(represents)
                    .map(described -> reflectedFrom(described, field.canonical()))
                    .orElseGet(NoneValue::none);
            case NativeValue built -> reflectedFrom(built, field.canonical());
            case OperatorValue operator -> reflectedFromBehind(operator, field);
            case Value anythingElse -> anythingElse.reflected(field);
        };
    }

    private Value reflectedFrom(VectorValue vector, WordValue field) {
        return field.canonical().equals("spec")
                ? VectorQuery.specOf(vector)
                : VectorQuery.field(vector, field.canonical()).orElseThrow(
                        () -> Raised.of(EvaluationFailure.INVALID_ARG, field));
    }

    private Value reflectedFrom(DatatypeSpec described, String field) {
        return switch (field) {
            case "title" -> StringValue.of(described.title());
            case "type" -> WordValue.of(described.category());
            case "spec" -> new ObjectValue(titleAndType(described));
            default -> NoneValue.none();
        };
    }

    private Context titleAndType(DatatypeSpec described) {
        Context fields = Context.root();
        fields.register("title", StringValue.of(described.title()));
        fields.register("type", WordValue.of(described.category()));
        return fields;
    }

    private Value reflectedFrom(NativeValue built, String field) {
        return switch (field) {
            case "spec" -> declarations.specOf(built);
            case "words" -> declarations.specOf(built).declaredParameters();
            case "types" -> built.typesets();
            default -> NoneValue.none();
        };
    }

    private Value reflectedFromBehind(OperatorValue operator, WordValue field) {
        return switch (operator.underlying()) {
            case NativeValue behind -> reflectedFrom(behind, field.canonical());
            case Value behind -> behind.reflected(field);
        };
    }
}
