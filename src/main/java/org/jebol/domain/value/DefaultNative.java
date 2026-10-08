package org.jebol.domain.value;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public abstract non-sealed class DefaultNative implements NativeValue {

    private Optional<BlockValue> declaredSpec = Optional.empty();
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
    public final Optional<BlockValue> ownSpec() {
        return declaredSpec;
    }

    public final void declaredBy(BlockValue spec, List<Parameter> declared) {
        this.declaredSpec = Optional.of(spec);
        this.declaredParameters = Optional.of(List.copyOf(declared));
    }

    @Override
    public String toString() {
        return "native " + nativeName() + "/" + arity();
    }

    protected List<Parameter> acceptsAllNumbers(String... names) {
        return each(names, Typeset.NUMBER.membersAnd(
                Datatype.MONEY,
                Datatype.PAIR,
                Datatype.TUPLE,
                Datatype.TIME,
                Datatype.DATE,
                Datatype.CHAR,
                Datatype.VECTOR
        ));
    }

    protected List<Parameter> acceptsOnlyNumbers(String... names) {
        return each(names, Typeset.NUMBER.members());
    }

    protected List<Parameter> acceptsWholeNumbers(String... names) {
        return each(names, Set.of(Datatype.INTEGER));
    }

    protected List<Parameter> acceptsWholeNumbersAndDecimals(String... names) {
        return each(names, Set.of(
                Datatype.INTEGER, Datatype.DECIMAL, Datatype.PERCENT));
    }

    protected List<Parameter> acceptsAnythingMeasurable(String... names) {
        return each(names, Typeset.NUMBER.membersAnd(
                Datatype.MONEY, Datatype.TIME, Datatype.PAIR));
    }

    protected List<Parameter> acceptsAnythingWithARange(String... names) {
        return each(names, Typeset.NUMBER.membersAnd(
                Datatype.TUPLE, Datatype.PAIR, Datatype.MONEY));
    }

    protected List<Parameter> acceptsAnythingDivisible(String... names) {
        return each(names, Typeset.NUMBER.membersAnd(
                Datatype.MONEY, Datatype.CHAR, Datatype.TIME));
    }

    protected List<Parameter> acceptsAnythingWithASign(String... names) {
        return each(names, Typeset.NUMBER.membersAnd(
                Datatype.PAIR, Datatype.MONEY, Datatype.TIME, Datatype.BITSET));
    }

    protected List<Parameter> acceptsAnythingMadeOfBits(String... names) {
        return each(names, Set.of(
                Datatype.LOGIC, Datatype.INTEGER, Datatype.CHAR, Datatype.TUPLE,
                Datatype.BINARY, Datatype.BITSET, Datatype.TYPESET,
                Datatype.DATATYPE, Datatype.PAIR, Datatype.VECTOR));
    }

    protected List<Parameter> acceptsAnyType(String... names) {
        return each(names, Typeset.ANY_TYPE.members());
    }

    protected Set<Datatype> aPartLimit() {
        return Stream.concat(
                        Typeset.NUMBER.membersAnd(Datatype.PAIR).stream(),
                        Arrays.stream(Datatype.values()).filter(Datatype::isSeries))
                .collect(Collectors.toUnmodifiableSet());
    }

    protected Set<Datatype> aDuplicateCount() {
        return Typeset.NUMBER.membersAnd(Datatype.PAIR);
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
                DatatypeValue.of(given.datatype())));
    }

    protected Value refuseTheDatatype(Value given) {
        throw Raised.cannotUse(given, nativeName());
    }

    private List<Parameter> each(String[] names, Set<Datatype> allowed) {
        List<Parameter> parameters = new ArrayList<>();
        for (String name : names) {
            parameters.add(Parameter.required(name, allowed));
        }
        return parameters;
    }
}
