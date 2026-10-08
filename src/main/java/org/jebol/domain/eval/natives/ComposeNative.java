package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.Evaluator;
import org.jebol.domain.eval.MapActions;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.*;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public class ComposeNative extends DefaultNative {

    private static final boolean GOING_DEEP = true;

    @Override
    public String nativeName() {
        return "compose";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(Parameter.required("block"),
                Parameter.belongingTo("into", "out", Typeset.ANY_BLOCK.members()));
    }

    @Override
    public Set<String> refinementsDeclaredApart() {
        return Set.of("only", "deep", "into");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            Value template = arguments.getFirst();
            boolean keepingBlocksWhole = refinements.contains("only");
            boolean goingDeep = refinements.contains("deep");
            if (template instanceof MapValue map) {
                return composedMap(map, evaluator, context, keepingBlocksWhole, goingDeep);
            }
            Optional<Value> target = argumentOf("into", 0, arguments, refinements);
            if (!(template instanceof BlockValue block)) {
                return target.isEmpty()
                        ? template
                        : spliced(BlockValue.block(List.of(template)), (BlockValue) target.get());
            }
            BlockValue built = composed(block, evaluator, context, keepingBlocksWhole, goingDeep);
            return target.isEmpty() ? built : spliced(built, (BlockValue) target.get());
        };
    }

    private Value spliced(BlockValue built, BlockValue target) {
        List<Value> items = built.remaining();
        target.storage().spliceInAt(target.index(), items, built.storage(), built.index());
        return target.atIndex(target.index() + items.size());
    }

    private BlockValue composed(BlockValue template, Evaluator evaluator, Context context,
            boolean keepingBlocksWhole, boolean goingDeep) {
        BlockStorage built = new BlockStorage();
        int reading = template.index();
        for (Value item : template.remaining()) {
            boolean asWritten = true;
            if (!(item instanceof BlockValue paren) || paren.datatype() != Datatype.PAREN) {
                built.append(composedItem(item, evaluator, context, keepingBlocksWhole,
                        goingDeep));
            } else {
                asWritten = false;
                spliceWhatTheParenProduces(built, paren, evaluator, context, keepingBlocksWhole);
            }
            if (asWritten && template.storage().breaksLineAt(reading)) {
                built.setLineBreakAt(built.length(), true);
            }
            reading++;
        }
        return new BlockValue(built, 1, Datatype.BLOCK);
    }

    private Value composedItem(Value item, Evaluator evaluator, Context context,
            boolean keepingBlocksWhole, boolean goingDeep) {
        if (goingDeep && item instanceof BlockValue nested
                && nested.datatype() == Datatype.BLOCK) {
            return composed(nested, evaluator, context, keepingBlocksWhole, GOING_DEEP);
        }
        if (goingDeep && item instanceof MapValue nested) {
            return composedMap(nested, evaluator, context, keepingBlocksWhole, GOING_DEEP);
        }
        return goingDeep ? aBlockShapeCopiedWhole(item) : item;
    }

    private void spliceWhatTheParenProduces(BlockStorage built, BlockValue paren,
            Evaluator evaluator, Context context, boolean keepingBlocksWhole) {
        for (Value produced : evaluator.evaluateEachOrRaise(paren.as(Datatype.BLOCK), context)) {
            if (produced instanceof UnsetValue) {
                continue;
            }
            if (!keepingBlocksWhole && produced instanceof BlockValue spliced
                    && spliced.datatype() == Datatype.BLOCK) {
                built.spliceInAt(built.length() + 1, spliced.remaining(),
                        spliced.storage(), spliced.index());
            } else {
                built.append(produced);
            }
        }
    }

    private Value aBlockShapeCopiedWhole(Value item) {
        return item instanceof BlockValue shaped
                ? new BlockValue(new BlockStorage(shaped.remaining()), 1, shaped.datatype())
                : item;
    }

    private MapValue composedMap(MapValue template, Evaluator evaluator, Context context,
            boolean keepingBlocksWhole, boolean goingDeep) {
        return new MapActions(template).composedThrough(held -> {
            if (held instanceof BlockValue paren && paren.datatype() == Datatype.PAREN) {
                return evaluator.evaluateOrRaise(paren.as(Datatype.BLOCK), context);
            }
            if (goingDeep && held instanceof BlockValue nested
                    && nested.datatype() == Datatype.BLOCK) {
                return composed(nested, evaluator, context, keepingBlocksWhole, GOING_DEEP);
            }
            if (goingDeep && held instanceof MapValue nested) {
                return composedMap(nested, evaluator, context, keepingBlocksWhole, GOING_DEEP);
            }
            return held;
        });
    }
}
