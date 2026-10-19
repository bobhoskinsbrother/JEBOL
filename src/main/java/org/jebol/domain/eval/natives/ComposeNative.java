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
        return List.of(Parameter.of("block"),
                Parameter.belongTo("into", "out", TypesetValue.ANY_BLOCK.members()));
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
                return composeAMap(map, evaluator, context, keepingBlocksWhole, goingDeep);
            }
            Optional<Value> target = readTheArgumentOf("into", 0, arguments, refinements);
            if (!(template instanceof AnyBlockValue block)) {
                return target.isEmpty()
                        ? template
                        : splice(BlockValue.of(List.of(template)), (AnyBlockValue) target.get());
            }
            AnyBlockValue built = compose(block, evaluator, context, keepingBlocksWhole, goingDeep);
            return target.isEmpty() ? built : splice(built, (AnyBlockValue) target.get());
        };
    }

    private Value splice(AnyBlockValue built, AnyBlockValue target) {
        List<Value> items = built.remaining();
        target.storage().spliceInAt(target.index(), items, built.storage(), built.index());
        return target.atIndex(target.index() + items.size());
    }

    private AnyBlockValue compose(AnyBlockValue template, Evaluator evaluator, Context context,
                                  boolean keepingBlocksWhole, boolean goingDeep) {
        BlockStorage built = new BlockStorage();
        int reading = template.index();
        for (Value item : template.remaining()) {
            boolean asWritten = true;
            if (!(item instanceof ParenValue paren)) {
                built.append(composeAnItem(item, evaluator, context, keepingBlocksWhole,
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
        return BlockValue.wrapTheStorage(built);
    }

    private Value composeAnItem(Value item, Evaluator evaluator, Context context,
                                boolean keepingBlocksWhole, boolean goingDeep) {
        if (goingDeep && item instanceof BlockValue nested) {
            return compose(nested, evaluator, context, keepingBlocksWhole, GOING_DEEP);
        }
        if (goingDeep && item instanceof MapValue nested) {
            return composeAMap(nested, evaluator, context, keepingBlocksWhole, GOING_DEEP);
        }
        return goingDeep ? copyABlockShapeWhole(item) : item;
    }

    private void spliceWhatTheParenProduces(BlockStorage built, ParenValue paren,
            Evaluator evaluator, Context context, boolean keepingBlocksWhole) {
        for (Value produced : evaluator.evaluateEachOrRaise(paren.asBlock(), context)) {
            if (produced instanceof UnsetValue) {
                continue;
            }
            if (!keepingBlocksWhole && produced instanceof BlockValue spliced) {
                built.spliceInAt(built.length() + 1, spliced.remaining(),
                        spliced.storage(), spliced.index());
            } else {
                built.append(produced);
            }
        }
    }

    private Value copyABlockShapeWhole(Value item) {
        return item instanceof AnyBlockValue shaped
                ? shaped.wrap(new BlockStorage(shaped.remaining()))
                : item;
    }

    private MapValue composeAMap(MapValue template, Evaluator evaluator, Context context,
                                 boolean keepingBlocksWhole, boolean goingDeep) {
        return new MapActions(template).composeThrough(held -> {
            if (held instanceof ParenValue paren) {
                return evaluator.evaluateOrRaise(paren.asBlock(), context);
            }
            if (goingDeep && held instanceof BlockValue nested) {
                return compose(nested, evaluator, context, keepingBlocksWhole, GOING_DEEP);
            }
            if (goingDeep && held instanceof MapValue nested) {
                return composeAMap(nested, evaluator, context, keepingBlocksWhole, GOING_DEEP);
            }
            return held;
        });
    }
}
