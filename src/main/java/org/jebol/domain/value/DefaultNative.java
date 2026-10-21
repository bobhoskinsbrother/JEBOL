package org.jebol.domain.value;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public abstract non-sealed class DefaultNative implements NativeValue {

    private Optional<AnyBlockValue> declaredSpec = Optional.empty();
    private Optional<List<Parameter>> declaredParameters = Optional.empty();

    public abstract List<Parameter> parametersAsWritten();

    @Override
    public final List<Parameter> parameters() {
        return declaredParameters.orElseGet(this::parametersAsWritten);
    }

    @Override
    public Set<String> refinementsDeclaredApart() {
        return Set.of();
    }

    @Override
    public final Optional<AnyBlockValue> ownSpec() {
        return declaredSpec;
    }

    public final void declare(AnyBlockValue spec, List<Parameter> declared) {
        this.declaredSpec = Optional.of(spec);
        this.declaredParameters = Optional.of(List.copyOf(declared));
    }

    @Override
    public String toString() {
        return "native " + nativeName() + "/" + countArity();
    }

    protected List<Parameter> acceptsAnyType(String... names) {
        return declareEach(names, TypesetValue.ANY_TYPE.members());
    }

    protected Set<Datatype> listThePartLimitTypes() {
        return Stream.concat(
                        TypesetValue.NUMBER.membersAnd(PairValue.TYPE).stream(),
                        TypesetValue.SERIES.members().stream())
                .collect(Collectors.toUnmodifiableSet());
    }

    protected Set<Datatype> listTheDuplicateCountTypes() {
        return TypesetValue.NUMBER.membersAnd(PairValue.TYPE);
    }

    protected List<Parameter> acceptsWhateverComesAlong(String... names) {
        List<Parameter> parameters = new ArrayList<>();
        for (String name : names) {
            parameters.add(Parameter.of(name));
        }
        return parameters;
    }

    protected Optional<Value> readTheArgumentOf(String refinement, int which,
                                                List<Value> arguments, Set<String> asked) {

        if (!asked.contains(refinement)) {
            return Optional.empty();
        }
        int at = 0;
        int seenOfThisRefinement = 0;
        for (Parameter parameter : parameters()) {
            if (!arrivesInThisCall(parameter, asked)) {
                continue;
            }
            if (belongsTo(parameter, refinement) && seenOfThisRefinement++ == which) {
                return at < arguments.size() ? Optional.of(arguments.get(at)) : Optional.empty();
            }
            at++;
        }
        return Optional.empty();
    }

    private boolean arrivesInThisCall(Parameter parameter, Set<String> asked) {
        return parameter.consumesAnArgument()
                && parameter.owningRefinement().map(asked::contains).orElse(true);
    }

    private boolean belongsTo(Parameter parameter, String refinement) {
        return parameter.owningRefinement().filter(refinement::equals).isPresent();
    }

    protected Value refuseArgument(Value given, String declaredArgument) {
        throw new Raised(ErrorValue.of(
                EvaluationFailure.EXPECT_ARG.category(),
                EvaluationFailure.EXPECT_ARG.errorId(),
                nativeName() + " does not allow " + given.datatype().literalSpelling()
                        + " for its " + declaredArgument + " argument",
                WordValue.of(nativeName()),
                WordValue.of(declaredArgument),
                given.datatype()));
    }

    protected Value refuseDatatype(Value given) {
        throw Raised.cannotUse(given, this);
    }

    protected List<Parameter> declareEach(String[] names, Set<Datatype> allowed) {
        List<Parameter> parameters = new ArrayList<>();
        for (String name : names) {
            parameters.add(Parameter.of(name, allowed));
        }
        return parameters;
    }
}
