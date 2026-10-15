package org.jebol.domain.value;

import java.util.List;
import java.util.Set;

public abstract class ArithmeticNative extends DefaultNative {

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
        return each(names, Set.of(IntegerValue.TYPE, DecimalValue.TYPE));
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
}
