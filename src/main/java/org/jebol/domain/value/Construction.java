package org.jebol.domain.value;

public interface Construction {

    Value make(Datatype datatype, Value specification);

    Value makeFunction(AnyBlockValue spec, AnyBlockValue body);

    static Construction refused() {
        return Refused.INSTANCE;
    }

    final class Refused implements Construction {

        private static final Refused INSTANCE = new Refused();

        private Refused() {
        }

        @Override
        public Value make(Datatype datatype, Value specification) {
            throw new IllegalStateException(
                    "nothing here makes a " + datatype.literalSpelling());
        }

        @Override
        public Value makeFunction(AnyBlockValue spec, AnyBlockValue body) {
            throw new IllegalStateException("nothing here makes a function");
        }
    }
}
