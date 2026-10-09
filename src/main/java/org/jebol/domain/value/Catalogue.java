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
            JavaObjectValue.TYPE),
            List.of(
                    TypesetValue.ANY_TYPE,
                    TypesetValue.NUMBER,
                    TypesetValue.SCALAR,
                    TypesetValue.SERIES,
                    TypesetValue.ANY_STRING,
                    TypesetValue.ANY_BLOCK,
                    TypesetValue.ANY_PATH,
                    TypesetValue.ANY_WORD,
                    TypesetValue.ANY_FUNCTION,
                    TypesetValue.ANY_OBJECT,
                    TypesetValue.IMMEDIATE,
                    TypesetValue.COPYABLE,
                    TypesetValue.INTERNAL));

    private final List<Datatype> entries;
    private final List<TypesetValue> standardTypesets;

    private Catalogue(List<Datatype> entries, List<TypesetValue> standardTypesets) {
        this.entries = entries;
        this.standardTypesets = standardTypesets;
    }

    public List<Datatype> entries() {
        return entries;
    }

    public int positionOf(Datatype datatype) {
        return entries.indexOf(datatype) + 1;
    }

    public Optional<Datatype> named(String spelling) {
        String wanted = withoutTheMark(spelling);
        return entries.stream()
                .filter(datatype -> datatype.spelling().equalsIgnoreCase(wanted))
                .findFirst();
    }

    public List<TypesetValue> standardTypesets() {
        return standardTypesets;
    }

    public Optional<TypesetValue> typesetNamed(String spelling) {
        String wanted = withoutTheMark(spelling);
        return standardTypesets().stream()
                .filter(typeset -> typeset.spelling().filter(wanted::equalsIgnoreCase).isPresent())
                .findFirst();
    }

    private String withoutTheMark(String spelling) {
        return spelling.endsWith("!")
                ? spelling.substring(0, spelling.length() - 1)
                : spelling;
    }

    public Set<Datatype> where(Predicate<Datatype> test) {
        Set<Datatype> kept = new LinkedHashSet<>();
        entries.stream().filter(test).forEach(kept::add);
        return kept;
    }
}
