package org.jebol.domain.eval.actions;

import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.ActionValue;
import org.jebol.domain.value.DefaultNative;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.Typeset;

import java.util.List;
import java.util.Set;

public abstract class SeriesSearchAction extends DefaultNative implements ActionValue {

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(Parameter.required("series"),
                Parameter.required("value", Typeset.ANY_TYPE.members()),
                Parameter.belongingTo("part", "range", aPartLimit()),
                Parameter.belongingTo("with", "wild", Set.of(Datatype.STRING)),
                Parameter.belongingTo("skip", "size", Set.of(Datatype.INTEGER)));
    }
}
