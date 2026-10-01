package org.jebol.domain.read;

import org.jebol.domain.value.BlockValue;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.Value;

public interface Construction {

    Value madeOf(Datatype datatype, Value specification);

    Value functionMadeFrom(BlockValue spec, BlockValue body);

    static Construction refused() {
        return Refused.INSTANCE;
    }

    final class Refused implements Construction {

        private static final Refused INSTANCE = new Refused();

        private Refused() {
        }

        @Override
        public Value madeOf(Datatype datatype, Value specification) {
            throw new IllegalStateException(
                    "nothing here makes a " + datatype.literalSpelling());
        }

        @Override
        public Value functionMadeFrom(BlockValue spec, BlockValue body) {
            throw new IllegalStateException("nothing here makes a function");
        }
    }
}
