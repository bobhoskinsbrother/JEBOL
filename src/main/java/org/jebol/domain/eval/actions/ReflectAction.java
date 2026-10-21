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
        return List.of(Parameter.of("value"),
                Parameter.of("field", Set.of(WordValue.TYPE)));
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) ->
                reflect(arguments.getFirst(), (AnyWordValue) arguments.get(1));
    }

    private Value reflect(Value subject, AnyWordValue field) {
        return switch (subject) {
            case UnsetValue nothing -> throw Raised.cannotUse(nothing, this);
            case VectorValue vector -> reflectFrom(vector, field);
            case Datatype represents -> declarations.findDatatypeSpec(represents)
                    .map(described -> reflectFrom(described, field.canonical()))
                    .orElseGet(NoneValue::none);
            case NativeValue built -> reflectFrom(built, field.canonical());
            case OperatorValue operator -> reflectFromBehind(operator, field);
            case Value anythingElse -> anythingElse.reflect(field);
        };
    }

    private Value reflectFrom(VectorValue vector, AnyWordValue field) {
        return field.canonical().equals("spec")
                ? VectorQuery.vectorSpec(vector)
                : VectorQuery.readField(vector, field.canonical()).orElseThrow(
                        () -> Raised.of(EvaluationFailure.INVALID_ARG, field));
    }

    private Value reflectFrom(DatatypeSpec described, String field) {
        return switch (field) {
            case "title" -> StringValue.of(described.title());
            case "type" -> WordValue.of(described.category());
            case "spec" -> new ObjectValue(describeTitleAndType(described));
            default -> NoneValue.none();
        };
    }

    private Context describeTitleAndType(DatatypeSpec described) {
        Context fields = Context.root();
        fields.register("title", StringValue.of(described.title()));
        fields.register("type", WordValue.of(described.category()));
        return fields;
    }

    private Value reflectFrom(NativeValue built, String field) {
        return switch (field) {
            case "spec" -> declarations.specFor(built);
            case "words" -> declarations.specFor(built).declaredParameters();
            case "types" -> built.listTypesets();
            default -> NoneValue.none();
        };
    }

    private Value reflectFromBehind(OperatorValue operator, AnyWordValue field) {
        return switch (operator.underlying()) {
            case NativeValue behind -> reflectFrom(behind, field.canonical());
            case Value behind -> behind.reflect(field);
        };
    }
}
