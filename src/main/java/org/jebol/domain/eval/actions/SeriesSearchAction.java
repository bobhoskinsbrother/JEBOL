package org.jebol.domain.eval.actions;

import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.StringValue;
import org.jebol.domain.value.ActionValue;
import org.jebol.domain.value.DefaultNative;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.TypesetValue;

import java.util.List;
import java.util.Set;

public abstract class SeriesSearchAction extends DefaultNative implements ActionValue {

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(Parameter.of("series"),
                Parameter.of("value", TypesetValue.ANY_TYPE.members()),
                Parameter.belongTo("part", "range", listPartLimitTypes()),
                Parameter.belongTo("with", "wild", Set.of(StringValue.TYPE)),
                Parameter.belongTo("skip", "size", Set.of(IntegerValue.TYPE)));
    }
}
