package org.jebol.domain.value;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class Context {

    private static final Context UNBOUND = new Context(null, true);

    private final SlotTable slotsByCanonicalName;
    private final Context parent;
    private final boolean unbound;
    private boolean loopFrameWhichContextQuestionCannotReach;
    private Value ownedByFunction;
    private boolean onlyThroughCallThatIsRunning;
    private Context supersededBy;
    private boolean closedToNewNames;
    private Optional<ContextSlot> itsOwnSelf = Optional.empty();

    private static final String WORD_OBJECT_ANSWERS_TO_FOR_ITSELF = "self";

    private Context(Context parent, boolean unbound) {
        this(parent, unbound, new SlotTable());
    }

    private Context(Context parent, boolean unbound, SlotTable slots) {
        this.parent = parent;
        this.unbound = unbound;
        this.slotsByCanonicalName = slots;
    }

    public static Context root() {
        return new Context(null, false);
    }

    public static Context makeChildOf(Context parent) {
        if (parent == null) {
            throw new IllegalArgumentException("a child context needs a parent");
        }
        return new Context(parent, false);
    }

    public static Context callFrameOf(Context parent, List<String> spellings, List<String> canonicalNames) {
        if (parent == null) {
            throw new IllegalArgumentException("a call frame needs a parent");
        }
        Context frame = new Context(parent, false, new SlotTable(spellings.size()));
        for (int position = 0; position < spellings.size(); position++) {
            ContextSlot slot = new ContextSlot(frame, spellings.get(position), canonicalNames.get(position));
            slot.setValue(NoneValue.none());
            frame.slotsByCanonicalName.add(canonicalNames.get(position), slot);
        }
        return frame;
    }

    public ContextSlot ownSlotAt(int position) {
        return slotsByCanonicalName.at(position);
    }

    public static Context collectWordsFunctionDeclares() {
        Context declared = new Context(null, false);
        declared.onlyThroughCallThatIsRunning = true;
        return declared;
    }

    public static Context loopFrameOf(Context parent) {
        Context frame = makeChildOf(parent);
        frame.loopFrameWhichContextQuestionCannotReach = true;
        return frame;
    }

    public static Context unbound() {
        return UNBOUND;
    }

    private static final Map<String, String> ONE_NAME_PER_SPELLING = new ConcurrentHashMap<>();

    public static String canonicalise(String spelling) {
        return ONE_NAME_PER_SPELLING.computeIfAbsent(
                spelling.toLowerCase(Locale.ROOT), folded -> folded);
    }

    public boolean isALoopFrame() {
        return loopFrameWhichContextQuestionCannotReach;
    }

    public void markAsCallFrameOf(Value function) {
        this.ownedByFunction = function;
    }

    public Value functionOwningThisFrame() {
        return supersededBy != null ? findResolvingFrame().functionOwningThisFrame() : ownedByFunction;
    }

    public void giveWayTo(Context newer) {
        this.supersededBy = newer;
    }

    public boolean isFunctionFrame() {
        return ownedByFunction != null;
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

    public boolean contains(String canonicalName) {
        if (unbound || lacksLentFrame()) {
            return false;
        }
        if (supersededBy != null) {
            return findResolvingFrame().contains(canonicalName);
        }
        return slotsByCanonicalName.containsKey(canonicalName)
                || answersToItsOwnSelf(canonicalName)
                || (parent != null && parent.contains(canonicalName));
    }

    public void pointItsOwnSelfAt(Value object) {
        ContextSlot self = new ContextSlot(this,
                WORD_OBJECT_ANSWERS_TO_FOR_ITSELF, WORD_OBJECT_ANSWERS_TO_FOR_ITSELF);
        self.setValue(object);
        itsOwnSelf = Optional.of(self);
    }

    public boolean hasItsOwnSlotFor(String canonicalName) {
        return slotsByCanonicalName.containsKey(canonicalName);
    }

    public boolean bindsWordSpelt(String canonicalName) {
        return holds(canonicalName) || answersToItsOwnSelf(canonicalName);
    }

    public boolean answersAlreadyTo(String canonicalName) {
        return hasItsOwnSlotFor(canonicalName) || answersToItsOwnSelf(canonicalName);
    }

    public boolean hasItsOwnSelf() {
        return itsOwnSelf.isPresent();
    }

    public boolean isItsOwnSelf(ContextSlot slot) {
        return itsOwnSelf.filter(self -> self == slot).isPresent();
    }

    public void refuseFieldSpeltLikeItsOwnSelf(Value named) {
        if (named instanceof AnyWordValue word && answersToItsOwnSelf(word.canonical())) {
            throw Raised.of(EvaluationFailure.SELF_PROTECTED);
        }
    }

    private boolean answersToItsOwnSelf(String canonicalName) {
        return itsOwnSelf.isPresent() && canonicalName.equals(WORD_OBJECT_ANSWERS_TO_FOR_ITSELF);
    }

    public boolean holds(String canonicalName) {
        ContextSlot slot = unbound || lacksLentFrame() ? null : slotsByCanonicalName.get(canonicalName);
        return slot != null && !slot.isHidden();
    }

    public boolean declaresItRelatively(String canonicalName) {
        return onlyThroughCallThatIsRunning && slotsByCanonicalName.containsKey(canonicalName);
    }

    public Context contextHolding(String canonicalName) {
        if (supersededBy != null) {
            return findResolvingFrame().contextHolding(canonicalName);
        }
        if (holds(canonicalName) || answersToItsOwnSelf(canonicalName)) {
            return this;
        }
        if (!unbound && parent != null) {
            return parent.contextHolding(canonicalName);
        }
        throw new IllegalStateException("no context holds \"" + canonicalName + "\"; ask knows() first");
    }

    public ContextSlot findSlotFor(String canonicalName) {
        if (unbound) {
            throw new IllegalStateException("the unbound context holds no slots; ask knows() first");
        }
        if (supersededBy != null) {
            return findResolvingFrame().findSlotFor(canonicalName);
        }
        ContextSlot slot = slotsByCanonicalName.get(canonicalName);
        if (slot != null) {
            return slot;
        }
        if (answersToItsOwnSelf(canonicalName)) {
            return itsOwnSelf.orElseThrow();
        }
        if (parent != null) {
            return parent.findSlotFor(canonicalName);
        }
        throw new IllegalStateException("no slot for \"" + canonicalName + "\"; ask knows() first");
    }

    public ContextSlot findSlotOrNone(String canonicalName) {
        if (unbound || lacksLentFrame()) {
            return null;
        }
        if (supersededBy != null) {
            return findResolvingFrame().findSlotOrNone(canonicalName);
        }
        ContextSlot slot = slotsByCanonicalName.get(canonicalName);
        if (slot != null) {
            return slot;
        }
        if (answersToItsOwnSelf(canonicalName)) {
            return itsOwnSelf.orElse(null);
        }
        return parent != null ? parent.findSlotOrNone(canonicalName) : null;
    }

    public ContextSlot register(String spelling) {
        if (unbound) {
            throw new IllegalStateException("the unbound context cannot be extended");
        }
        if (supersededBy != null) {
            return findResolvingFrame().register(spelling);
        }
        String canonical = canonicalise(spelling);
        ContextSlot existing = slotsByCanonicalName.get(canonical);
        if (existing != null) {
            return existing;
        }
        ContextSlot created = new ContextSlot(this, spelling, canonical);
        slotsByCanonicalName.add(canonical, created);
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

    public Value readValueAt(String first, String... fieldsAfter) {
        Value reached = contains(first) ? findSlotFor(first).value() : NoneValue.none();
        for (String field : fieldsAfter) {
            if (!(reached instanceof ObjectValue(Context object)) || !object.holds(field)) {
                return NoneValue.none();
            }
            reached = object.ownSlotFor(field).value();
        }
        return reached;
    }

    public Value findSystemFunctionNamed(String name) {
        if (!(readValueAt("system", "contexts", "sys") instanceof ObjectValue(Context sys))) {
            throw Raised.of(EvaluationFailure.NOT_DEFINED, name);
        }
        if (!sys.contains(name)) {
            throw Raised.of(EvaluationFailure.BAD_SYS_FUNC, UnsetValue.unset());
        }
        Value held = sys.findSlotFor(name).value();
        if (!isFunctionInterpreterCanCall(held)) {
            throw Raised.of(EvaluationFailure.BAD_SYS_FUNC, held);
        }
        return held;
    }

    public Context withoutFieldsHoldingNothing() {
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

    public Map<String, Value> listFieldsExceptSelf() {
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
        AnyBlockValue block = BlockValue.of(setWordsAndValues());
        block.putEachPairOnLine();
        return block;
    }

    public AnyBlockValue wordsExcludingSelf() {
        return BlockValue.of(listFieldsExceptSelf().keySet().stream()
                .<Value>map(spelling -> WordValue.of(spelling).bindTo(this)).toList());
    }

    public AnyBlockValue valuesExcludingSelf() {
        return BlockValue.of(List.copyOf(listFieldsExceptSelf().values()));
    }

    public List<ContextSlot> slots() {
        return slotsByCanonicalName.values().stream().filter(slot -> !slot.isHidden()).collect(Collectors.toCollection(ArrayList::new));
    }

    public Set<String> ownFieldNames() {
        return slots().stream().map(ContextSlot::canonical).collect(Collectors.toSet());
    }

    public Set<String> listNamesItBinds() {
        Set<String> bound = new HashSet<>(ownFieldNames());
        itsOwnSelf.ifPresent(self -> bound.add(WORD_OBJECT_ANSWERS_TO_FOR_ITSELF));
        return bound;
    }

    public List<ContextSlot> listEverySlot() {
        return new ArrayList<>(slotsByCanonicalName.values());
    }

    public List<Value> listBoundWordsAndValues() {
        return slots().stream()
                .<Value>mapMulti((slot, accept) -> {
                    accept.accept(WordValue.of(slot.spelling()).bindTo(this));
                    accept.accept(slot.value());
                })
                .toList();
    }

    @Override
    public String toString() {
        return unbound ? "Context(unbound)" : "Context(" + slotCount() + " slots)";
    }

    private Context findResolvingFrame() {
        Context frame = this;
        while (frame.supersededBy != null) {
            frame = frame.supersededBy;
        }
        return frame;
    }

    private boolean lacksLentFrame() {
        return onlyThroughCallThatIsRunning && supersededBy == null;
    }

    private boolean isFunctionInterpreterCanCall(Value held) {
        return held instanceof DefinedFunctionValue || held instanceof NativeValue || held instanceof OperatorValue;
    }
}
