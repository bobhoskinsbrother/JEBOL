package org.jebol.domain.eval.actions;

import org.jebol.domain.eval.Evaluator;
import org.jebol.domain.eval.GrantedServices;
import org.jebol.domain.eval.ports.OpenFile;
import org.jebol.domain.value.*;

import java.util.List;
import java.util.Set;

public abstract class SeriesOrFileAction extends DefaultNative implements ActionValue {

    private final GrantedServices granted;

    protected SeriesOrFileAction(GrantedServices granted) {
        this.granted = granted;
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return acceptsWhateverComesAlong("series");
    }

    protected OpenFile theFileBehind(PortValue port, Evaluator evaluator) {
        return new OpenFile(port, evaluator.files(), granted);
    }

    protected Set<Datatype> somewhereToStand() {
        return Typeset.SERIES.membersAnd(PortValue.TYPE, NoneValue.TYPE, GobValue.TYPE);
    }

    protected Set<Datatype> anOffset() {
        return Typeset.NUMBER.membersAnd(LogicValue.TYPE, PairValue.TYPE);
    }
}
