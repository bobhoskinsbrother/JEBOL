package org.jebol.domain.value;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class Context {

    private static final Context UNBOUND = new Context(null, true);

    private final Map<String, ContextSlot> slotsByCanonicalName = new LinkedHashMap<>();
    private final Context parent;
    private final boolean unbound;
    private boolean loopFrameWhichContextQuestionCannotReach;
    private Value ownedByFunction;
    private boolean callEnded;
    private boolean onlyThroughACallThatIsRunning;
    private Context supersededBy;
    private boolean closedToNewNames;
    private Optional<ContextSlot> itsOwnSelf = Optional.empty();

    private static final String THE_WORD_AN_OBJECT_ANSWERS_TO_FOR_ITSELF = "self";

    private Context(Context parent, boolean unbound) {
        this.parent = parent;
        this.unbound = unbound;
    }

    public static Context root() {
        return new Context(null, false);
    }

    public static Context childOf(Context parent) {
        if (parent == null) {
            throw new IllegalArgumentException("a child context needs a parent");
        }
        return new Context(parent, false);
    }

    public static Context theWordsAFunctionDeclares() {
        Context declared = new Context(null, false);
        declared.onlyThroughACallThatIsRunning = true;
        return declared;
    }

    public static Context loopFrameOf(Context parent) {
        Context frame = childOf(parent);
        frame.loopFrameWhichContextQuestionCannotReach = true;
        return frame;
    }

    public static Context unbound() {
        return UNBOUND;
    }

    public static String canonicalise(String spelling) {
        return spelling.toLowerCase(Locale.ROOT);
    }

    public boolean isALoopFrame() {
        return loopFrameWhichContextQuestionCannotReach;
    }

    public void markAsCallFrameOf(Value function) {
        this.ownedByFunction = function;
    }

    public Value functionOwningThisFrame() {
        return supersededBy != null ? frameThatResolvesForThisOne().functionOwningThisFrame() : ownedByFunction;
    }

    public void supersededBy(Context newer) {
        this.supersededBy = newer;
    }

    public void markCallEnded() {
        this.callEnded = true;
    }

    public boolean callHasEnded() {
        if (supersededBy != null) {
            return frameThatResolvesForThisOne().callHasEnded();
        }
        return callEnded || onlyThroughACallThatIsRunning;
    }

    public boolean isUnbound() {
        return unbound;
    }

    public boolean isClosedToNewNames() {
        return closedToNewNames;
    }

    public void closeToNewNames(boolean closed) {
        this.closedToNewNames = closed;
    }

    public boolean knows(String canonicalName) {
        if (unbound || noCallIsLendingItAFrame()) {
            return false;
        }
        if (supersededBy != null) {
            return frameThatResolvesForThisOne().knows(canonicalName);
        }
        return slotsByCanonicalName.containsKey(canonicalName)
                || itsOwnSelfAnswersTo(canonicalName)
                || (parent != null && parent.knows(canonicalName));
    }

    public void pointItsOwnSelfAt(Value object) {
        ContextSlot self = new ContextSlot(this,
                THE_WORD_AN_OBJECT_ANSWERS_TO_FOR_ITSELF, THE_WORD_AN_OBJECT_ANSWERS_TO_FOR_ITSELF);
        self.setValue(object);
        itsOwnSelf = Optional.of(self);
    }

    public boolean hasItsOwnSlotFor(String canonicalName) {
        return slotsByCanonicalName.containsKey(canonicalName);
    }

    public boolean bindsAWordSpelt(String canonicalName) {
        return holds(canonicalName) || itsOwnSelfAnswersTo(canonicalName);
    }

    public boolean hasItsOwnSelf() {
        return itsOwnSelf.isPresent();
    }

    private boolean itsOwnSelfAnswersTo(String canonicalName) {
        return itsOwnSelf.isPresent() && canonicalName.equals(THE_WORD_AN_OBJECT_ANSWERS_TO_FOR_ITSELF);
    }

    public boolean holds(String canonicalName) {
        ContextSlot slot = unbound || noCallIsLendingItAFrame() ? null : slotsByCanonicalName.get(canonicalName);
        return slot != null && !slot.isHidden();
    }

    public boolean declaresItRelatively(String canonicalName) {
        return onlyThroughACallThatIsRunning && slotsByCanonicalName.containsKey(canonicalName);
    }

    public Context holderOf(String canonicalName) {
        if (supersededBy != null) {
            return frameThatResolvesForThisOne().holderOf(canonicalName);
        }
        if (holds(canonicalName) || itsOwnSelfAnswersTo(canonicalName)) {
            return this;
        }
        if (!unbound && parent != null) {
            return parent.holderOf(canonicalName);
        }
        throw new IllegalStateException("no context holds \"" + canonicalName + "\"; ask knows() first");
    }

    public ContextSlot slotFor(String canonicalName) {
        if (unbound) {
            throw new IllegalStateException("the unbound context holds no slots; ask knows() first");
        }
        if (supersededBy != null) {
            return frameThatResolvesForThisOne().slotFor(canonicalName);
        }
        ContextSlot slot = slotsByCanonicalName.get(canonicalName);
        if (slot != null) {
            return slot;
        }
        if (itsOwnSelfAnswersTo(canonicalName)) {
            return itsOwnSelf.orElseThrow();
        }
        if (parent != null) {
            return parent.slotFor(canonicalName);
        }
        throw new IllegalStateException("no slot for \"" + canonicalName + "\"; ask knows() first");
    }

    public ContextSlot register(String spelling) {
        if (unbound) {
            throw new IllegalStateException("the unbound context cannot be extended");
        }
        if (supersededBy != null) {
            return frameThatResolvesForThisOne().register(spelling);
        }
        String canonical = canonicalise(spelling);
        ContextSlot existing = slotsByCanonicalName.get(canonical);
        if (existing != null) {
            return existing;
        }
        ContextSlot created = new ContextSlot(this, spelling, canonical);
        slotsByCanonicalName.put(canonical, created);
        return created;
    }

    public ContextSlot register(String spelling, Value value) {
        ContextSlot slot = register(spelling);
        slot.setValue(value);
        return slot;
    }

    public ContextSlot ownSlotFor(String canonicalName) {
        ContextSlot slot = slotsByCanonicalName.get(canonicalName);
        if (slot == null) {
            throw new IllegalStateException("no field \"" + canonicalName + "\" here; ask holds() first");
        }
        return slot;
    }

    public Value valueAt(String first, String... fieldsAfter) {
        Value reached = knows(first) ? slotFor(first).value() : NoneValue.none();
        for (String field : fieldsAfter) {
            if (!(reached instanceof ObjectValue(Context object)) || !object.holds(field)) {
                return NoneValue.none();
            }
            reached = object.ownSlotFor(field).value();
        }
        return reached;
    }

    public Value systemFunctionNamed(String name) {
        if (!(valueAt("system", "contexts", "sys") instanceof ObjectValue(Context sys))) {
            throw Raised.of(EvaluationFailure.NOT_DEFINED, name);
        }
        if (!sys.knows(name)) {
            throw Raised.of(EvaluationFailure.BAD_SYS_FUNC, UnsetValue.unset());
        }
        Value held = sys.slotFor(name).value();
        if (!isAFunctionTheInterpreterCanCall(held)) {
            throw Raised.of(EvaluationFailure.BAD_SYS_FUNC, held);
        }
        return held;
    }

    public Context withoutTheFieldsHoldingNothing() {
        Context kept = Context.root();
        for (ContextSlot slot : slots()) {
            boolean holdsNothing = slot.value() instanceof NoneValue
                    || slot.value() instanceof UnsetValue;
            if (!holdsNothing) {
                kept.register(slot.spelling(), slot.value());
            }
        }
        return kept;
    }

    public boolean holdsNothingButSelf() {
        return slots().isEmpty();
    }

    public Map<String, Value> fieldsExcludingSelf() {
        Map<String, Value> fields = new LinkedHashMap<>();
        slotsByCanonicalName.forEach((name, slot) -> {
            if (!slot.isHidden()) {
                fields.put(name, slot.value());
            }
        });
        return fields;
    }

    public int fieldCount() {
        return slotsByCanonicalName.size();
    }

    public int slotCount() {
        return slotsByCanonicalName.size();
    }

    public List<Value> setWordsAndValues() {
        return slots().stream().flatMap(slot -> Stream.of(SetWordValue.of(slot.spelling()), slot.value())).toList();
    }

    public AnyBlockValue setWordsAndValuesOnLines() {
        AnyBlockValue block = BlockValue.block(setWordsAndValues());
        block.putEachPairOnALine();
        return block;
    }

    public AnyBlockValue wordsExcludingSelf() {
        return BlockValue.block(fieldsExcludingSelf().keySet().stream()
                .<Value>map(spelling -> WordValue.of(spelling).boundTo(this)).toList());
    }

    public AnyBlockValue valuesExcludingSelf() {
        return BlockValue.block(List.copyOf(fieldsExcludingSelf().values()));
    }

    public List<ContextSlot> slots() {
        return slotsByCanonicalName.values().stream().filter(slot -> !slot.isHidden()).collect(Collectors.toCollection(ArrayList::new));
    }

    public Set<String> ownFieldNames() {
        return slots().stream().map(ContextSlot::canonical).collect(Collectors.toSet());
    }

    public Set<String> theNamesItBinds() {
        Set<String> bound = new HashSet<>(ownFieldNames());
        itsOwnSelf.ifPresent(self -> bound.add(THE_WORD_AN_OBJECT_ANSWERS_TO_FOR_ITSELF));
        return bound;
    }

    public List<ContextSlot> everySlot() {
        return new ArrayList<>(slotsByCanonicalName.values());
    }

    public List<Value> boundWordsAndValues() {
        return slots().stream()
                .<Value>mapMulti((slot, accept) -> {
                    accept.accept(WordValue.of(slot.spelling()).boundTo(this));
                    accept.accept(slot.value());
                })
                .toList();
    }

    @Override
    public String toString() {
        return unbound ? "Context(unbound)" : "Context(" + slotCount() + " slots)";
    }

    private Context frameThatResolvesForThisOne() {
        Context frame = this;
        while (frame.supersededBy != null) {
            frame = frame.supersededBy;
        }
        return frame;
    }

    private boolean noCallIsLendingItAFrame() {
        return onlyThroughACallThatIsRunning && supersededBy == null;
    }

    private boolean isAFunctionTheInterpreterCanCall(Value held) {
        return held instanceof DefinedFunctionValue || held instanceof NativeValue || held instanceof OperatorValue;
    }
}
