package org.jebol.domain.value;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

public final class Catalogue {

    public static final Catalogue DATATYPES = new Catalogue(List.of(
            EndValue.TYPE,
            UnsetValue.TYPE,
            NoneValue.TYPE,
            LogicValue.TYPE,
            IntegerValue.TYPE,
            DecimalValue.TYPE,
            PercentValue.TYPE,
            MoneyValue.TYPE,
            CharacterValue.TYPE,
            PairValue.TYPE,
            TupleValue.TYPE,
            TimeValue.TYPE,
            DateValue.TYPE,
            BinaryValue.TYPE,
            StringValue.TYPE,
            FileValue.TYPE,
            EmailValue.TYPE,
            RefValue.TYPE,
            UrlValue.TYPE,
            TagValue.TYPE,
            BitsetValue.TYPE,
            ImageValue.TYPE,
            VectorValue.TYPE,
            BlockValue.TYPE,
            ParenValue.TYPE,
            PathValue.TYPE,
            SetPathValue.TYPE,
            GetPathValue.TYPE,
            LitPathValue.TYPE,
            HashValue.TYPE,
            MapValue.TYPE,
            Datatype.TYPE,
            TypesetValue.TYPE,
            WordValue.TYPE,
            SetWordValue.TYPE,
            GetWordValue.TYPE,
            LitWordValue.TYPE,
            RefinementValue.TYPE,
            IssueValue.TYPE,
            NativeValue.TYPE,
            ActionValue.TYPE,
            RebcodeValue.TYPE,
            CommandValue.TYPE,
            OperatorValue.TYPE,
            ClosureValue.TYPE,
            FunctionValue.TYPE,
            FrameValue.TYPE,
            ObjectValue.TYPE,
            ModuleValue.TYPE,
            ErrorValue.TYPE,
            TaskValue.TYPE,
            PortValue.TYPE,
            GobValue.TYPE,
            EventValue.TYPE,
            HandleValue.TYPE,
            StructValue.TYPE,
            LibraryValue.TYPE,
            UtypeValue.TYPE,
            JavaObjectValue.TYPE));

    private final List<Datatype> entries;

    private Catalogue(List<Datatype> entries) {
        this.entries = entries;
    }

    public List<Datatype> entries() {
        return entries;
    }

    public int positionOf(Datatype datatype) {
        return entries.indexOf(datatype) + 1;
    }

    public Optional<Datatype> named(String spelling) {
        String wanted = spelling.endsWith("!")
                ? spelling.substring(0, spelling.length() - 1)
                : spelling;
        return entries.stream()
                .filter(datatype -> datatype.spelling().equalsIgnoreCase(wanted))
                .findFirst();
    }

    public Set<Datatype> where(Predicate<Datatype> test) {
        Set<Datatype> kept = new LinkedHashSet<>();
        entries.stream().filter(test).forEach(kept::add);
        return kept;
    }
}
