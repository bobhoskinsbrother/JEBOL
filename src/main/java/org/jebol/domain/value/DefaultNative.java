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

    public final void declaredBy(AnyBlockValue spec, List<Parameter> declared) {
        this.declaredSpec = Optional.of(spec);
        this.declaredParameters = Optional.of(List.copyOf(declared));
    }

    @Override
    public String toString() {
        return "native " + nativeName() + "/" + arity();
    }

    protected List<Parameter> acceptsAllNumbers(String... names) {
        return each(names, TypesetValue.NUMBER.membersAnd(
                MoneyValue.TYPE,
                PairValue.TYPE,
                TupleValue.TYPE,
                TimeValue.TYPE,
                DateValue.TYPE,
                CharacterValue.TYPE,
                VectorValue.TYPE
        ));
    }

    protected List<Parameter> acceptsOnlyNumbers(String... names) {
        return each(names, TypesetValue.NUMBER.members());
    }

    protected List<Parameter> acceptsWholeNumbers(String... names) {
        return each(names, Set.of(IntegerValue.TYPE));
    }

    protected List<Parameter> acceptsWholeNumbersAndDecimals(String... names) {
        return each(names, Set.of(
                IntegerValue.TYPE, DecimalValue.TYPE, PercentValue.TYPE));
    }

    protected List<Parameter> acceptsAnythingMeasurable(String... names) {
        return each(names, TypesetValue.NUMBER.membersAnd(
                MoneyValue.TYPE, TimeValue.TYPE, PairValue.TYPE));
    }

    protected List<Parameter> acceptsAnythingWithARange(String... names) {
        return each(names, TypesetValue.NUMBER.membersAnd(
                TupleValue.TYPE, PairValue.TYPE, MoneyValue.TYPE));
    }

    protected List<Parameter> acceptsAnythingDivisible(String... names) {
        return each(names, TypesetValue.NUMBER.membersAnd(
                MoneyValue.TYPE, CharacterValue.TYPE, TimeValue.TYPE));
    }

    protected List<Parameter> acceptsAnythingWithASign(String... names) {
        return each(names, TypesetValue.NUMBER.membersAnd(
                PairValue.TYPE, MoneyValue.TYPE, TimeValue.TYPE, BitsetValue.TYPE));
    }

    protected List<Parameter> acceptsAnythingMadeOfBits(String... names) {
        return each(names, Set.of(
                LogicValue.TYPE, IntegerValue.TYPE, CharacterValue.TYPE, TupleValue.TYPE,
                BinaryValue.TYPE, BitsetValue.TYPE, TypesetValue.TYPE,
                Datatype.TYPE, PairValue.TYPE, VectorValue.TYPE));
    }

    protected List<Parameter> acceptsAnyType(String... names) {
        return each(names, TypesetValue.ANY_TYPE.members());
    }

    protected Set<Datatype> aPartLimit() {
        return Stream.concat(
                        TypesetValue.NUMBER.membersAnd(PairValue.TYPE).stream(),
                        TypesetValue.SERIES.members().stream())
                .collect(Collectors.toUnmodifiableSet());
    }

    protected Set<Datatype> aDuplicateCount() {
        return TypesetValue.NUMBER.membersAnd(PairValue.TYPE);
    }

    protected List<Parameter> acceptsWhateverComesAlong(String... names) {
        List<Parameter> parameters = new ArrayList<>();
        for (String name : names) {
            parameters.add(Parameter.required(name));
        }
        return parameters;
    }

    protected Optional<Value> argumentOf(String refinement, int which,
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

    protected Value refuseTheArgument(Value given, String declaredArgument) {
        throw new Raised(ErrorValue.about(
                EvaluationFailure.EXPECT_ARG.category(),
                EvaluationFailure.EXPECT_ARG.errorId(),
                nativeName() + " does not allow " + given.datatype().literalSpelling()
                        + " for its " + declaredArgument + " argument",
                WordValue.of(nativeName()),
                WordValue.of(declaredArgument),
                given.datatype()));
    }

    protected Value refuseTheDatatype(Value given) {
        throw Raised.cannotUse(given, this);
    }

    private List<Parameter> each(String[] names, Set<Datatype> allowed) {
        List<Parameter> parameters = new ArrayList<>();
        for (String name : names) {
            parameters.add(Parameter.required(name, allowed));
        }
        return parameters;
    }
}
