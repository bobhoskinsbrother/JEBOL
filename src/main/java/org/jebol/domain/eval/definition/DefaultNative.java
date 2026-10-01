package org.jebol.domain.eval.definition;

import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.EvaluationFailure;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.Raised;
import org.jebol.domain.value.Typeset;
import org.jebol.domain.value.Value;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public abstract class DefaultNative implements NativeDefinition {

    @Override
    public Set<String> refinements() {
        return Set.of();
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

    protected List<Parameter> acceptsWhateverComesAlong(String... names) {
        List<Parameter> parameters = new ArrayList<>();
        for (String name : names) {
            parameters.add(Parameter.required(name));
        }
        return parameters;
    }

    protected Value refuseTheArgument(Value given, String wanted) {
        throw Raised.of(EvaluationFailure.EXPECT_ARG,
                name() + " wanted a " + wanted + ", not a "
                        + given.datatype().literalSpelling());
    }

    protected Value refuseTheDatatype(Value given) {
        throw Raised.cannotUse(given, name());
    }

    private List<Parameter> each(String[] names, Set<Datatype> allowed) {
        List<Parameter> parameters = new ArrayList<>();
        for (String name : names) {
            parameters.add(Parameter.required(name, allowed));
        }
        return parameters;
    }
}
