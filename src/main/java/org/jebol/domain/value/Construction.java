package org.jebol.domain.value;

public interface Construction {

    Value madeOf(Datatype datatype, Value specification);

    Value functionMadeFrom(AnyBlockValue spec, AnyBlockValue body);

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
        public Value functionMadeFrom(AnyBlockValue spec, AnyBlockValue body) {
            throw new IllegalStateException("nothing here makes a function");
        }
    }
}
