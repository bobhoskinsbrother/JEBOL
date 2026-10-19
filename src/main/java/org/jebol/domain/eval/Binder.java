package org.jebol.domain.eval;

import org.jebol.domain.value.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


public final class Binder {

    private Binder() {
    }

    public static <Kind extends DefinedFunctionValue> Kind withItsBodyBound(Kind made) {
        made.declaredWords().markAsCallFrameOf(made);
        Set<String> declared = listTheNamesDeclaredBy(made);
        declared.forEach(made.declaredWords()::register);
        bindEachInPlace(made.body(), made.declaredWords(), declared);
        return made;
    }

    public static FunctionValue bindTheFunctionsBody(
            AnyBlockValue spec, AnyBlockValue body, Context closedOver) {

        return withItsBodyBound(new FunctionValue(
                spec,
                body,
                FunctionSpec.readTheParametersIn(spec),
                FunctionSpec.readTheLocalNamesIn(spec),
                closedOver));
    }

    public static Value cloneAndRebind(Value value, Set<Context> from, Context into) {
        return switch (value) {
            case AnyWordValue word -> word.isBound() && from.contains(word.binding())
                    ? word.bindTo(into)
                    : word;
            case AnyBlockValue block -> {
                List<Value> items = new ArrayList<>();
                for (Value item : block.remaining()) {
                    items.add(cloneAndRebind(item, from, into));
                }
                yield block.wrap(items);
            }
            case AnyStringValue text -> text.wrap(text.text());
            case BinaryValue bytes -> bytes.copyOfTheFirst(bytes.lengthFromHere());
            case MapValue map -> {
                MapValue cloned = map.copy();
                for (Value key : cloned.keys()) {
                    cloned.put(key, cloneAndRebind(cloned.select(key), from, into));
                }
                yield cloned;
            }
            case DefinedFunctionValue function -> withItsBodyBound(function.makeTheSameKindRunning(
                    (AnyBlockValue) cloneAndRebind(function.body(), from, into), into));
            default -> value;
        };
    }

    private static Set<String> listTheNamesDeclaredBy(DefinedFunctionValue function) {
        Set<String> declared = new HashSet<>();
        function.parameters().forEach(
                parameter -> declared.add(Context.canonicalise(parameter.name())));
        function.localNames().forEach(
                name -> declared.add(Context.canonicalise(name)));
        return declared;
    }

    public static AnyBlockValue bind(AnyBlockValue block, Context context) {
        List<Value> bound = new ArrayList<>(block.lengthFromHere());
        for (Value item : block.remaining()) {
            bound.add(bindValue(item, context));
        }
        return layOutLike(block, new BlockStorage(bound));
    }

    private static AnyBlockValue layOutLike(AnyBlockValue older, BlockStorage bound) {
        bound.takeLineBreaksFrom(older.storage(), older.index());
        return older.wrap(bound);
    }

    public static AnyBlockValue bindInPlace(AnyBlockValue block, Context context) {
        for (int at = 0; at < block.lengthFromHere(); at++) {
            int where = block.index() + at;
            Value bound = bindValue(block.storage().at(where), context);
            if (bound instanceof AnyWordValue word) {
                block.storage().rebindAt(where, word);
            } else {
                block.storage().set(where, bound);
            }
        }
        return block;
    }

    public static AnyBlockValue bindWhatTheTargetHoldsItself(
            AnyBlockValue block, Context target) {

        return bindWhatTheTargetHoldsItself(block, target, ALL_THE_WAY_DOWN);
    }

    public static AnyBlockValue bindWhatTheTargetHoldsItself(
            AnyBlockValue block, Context target, boolean deeply) {

        for (int at = 0; at < block.lengthFromHere(); at++) {
            int where = block.index() + at;
            Value item = block.storage().at(where);
            if (item instanceof AnyWordValue word) {
                if (target.bindsAWordSpelt(word.canonical())) {
                    block.storage().rebindAt(where, word.bindTo(target));
                }
            } else if (deeply) {
                bindIfTheTargetHoldsIt(item, target);
            }
        }
        return block;
    }

    public static final boolean ALL_THE_WAY_DOWN = true;

    public static final boolean THE_TOP_LEVEL_ONLY = false;

    public static AnyBlockValue bindACopyOfWhatTheTargetHoldsItself(
            AnyBlockValue block, Context target) {

        return bindACopyOfWhatTheTargetHoldsItself(block, target, ALL_THE_WAY_DOWN);
    }

    public static AnyBlockValue bindACopyOfWhatTheTargetHoldsItself(
            AnyBlockValue block, Context target, boolean deeply) {

        return bindWhatTheTargetHoldsItself(copyDeeply(block), target, deeply);
    }

    private static AnyBlockValue copyDeeply(AnyBlockValue block) {
        List<Value> copied = new ArrayList<>(block.lengthFromHere());
        for (Value item : block.remaining()) {
            copied.add(item instanceof AnyBlockValue nested ? copyDeeply(nested) : item);
        }
        return layOutLike(block, new BlockStorage(copied));
    }

    private static Value bindIfTheTargetHoldsIt(Value value, Context target) {
        return switch (value) {
            case AnyWordValue word when target.bindsAWordSpelt(word.canonical()) ->
                    word.bindTo(target);
            case AnyWordValue word -> word;
            case AnyBlockValue nested -> bindWhatTheTargetHoldsItself(nested, target);
            case MapValue map -> {
                for (Value key : map.keys()) {
                    map.put(key, bindIfTheTargetHoldsIt(map.select(key), target));
                }
                yield map;
            }
            default -> value;
        };
    }

    public static AnyBlockValue bindOnly(
            AnyBlockValue block, Context context, Set<String> names) {

        List<Value> bound = new ArrayList<>(block.lengthFromHere());
        for (Value item : block.remaining()) {
            bound.add(bindValueOnly(item, context, names));
        }
        return layOutLike(block, new BlockStorage(bound));
    }

    public static void bindEachInPlace(
            AnyBlockValue block, Context context, Set<String> names) {

        bindEachInPlace(block, context, names,
                java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>()));
    }

    private static void bindEachInPlace(AnyBlockValue block, Context context,
                                        Set<String> names, Set<Object> alreadyWalked) {

        if (!alreadyWalked.add(block.storage())) {
            return;
        }
        for (int at = block.index(); at <= block.storageLength(); at++) {
            switch (block.storage().at(at)) {
                case AnyWordValue word when names.contains(word.canonical()) ->
                        block.storage().rebindAt(at, word.bindTo(context));
                case AnyBlockValue nested ->
                        bindEachInPlace(nested, context, names, alreadyWalked);
                case MapValue map -> {
                    for (Value key : map.keys()) {
                        map.put(key, bindIfDeclared(
                                map.select(key), context, names, alreadyWalked));
                    }
                }
                default -> { }
            }
        }
    }

    public static AnyBlockValue rebindWhatNamedTheFunction(
            AnyBlockValue block, Context from, Context to) {

        List<Value> bound = new ArrayList<>(block.lengthFromHere());
        for (Value item : block.remaining()) {
            bound.add(rebindOneThatNamedIt(item, from, to));
        }
        return layOutLike(block, new BlockStorage(bound));
    }

    private static Value rebindOneThatNamedIt(Value value, Context from, Context to) {
        return switch (value) {
            case AnyWordValue word when word.isBound() && word.binding() == from ->
                    word.bindTo(to);
            case AnyWordValue word -> word;
            case AnyBlockValue nested -> rebindWhatNamedTheFunction(nested, from, to);
            case MapValue map -> {
                for (Value key : map.keys()) {
                    map.put(key, rebindOneThatNamedIt(map.select(key), from, to));
                }
                yield map;
            }
            default -> value;
        };
    }

    private static Value bindIfDeclared(Value held, Context context,
                                        Set<String> names, Set<Object> alreadyWalked) {

        if (held instanceof AnyWordValue word && names.contains(word.canonical())) {
            return word.bindTo(context);
        }
        if (held instanceof AnyBlockValue nested) {
            bindEachInPlace(nested, context, names, alreadyWalked);
        }
        return held;
    }

    private static Value bindValueOnly(
            Value value, Context context, Set<String> names) {

        return switch (value) {
            case AnyWordValue word when names.contains(word.canonical()) ->
                    word.bindTo(context.contains(word.canonical())
                            ? context.findTheHolderOf(word.canonical())
                            : context);
            case AnyWordValue word -> word;
            case AnyBlockValue nested -> bindOnly(nested, context, names);
            case MapValue map -> {
                for (Value key : map.keys()) {
                    map.put(key, bindValueOnly(map.select(key), context, names));
                }
                yield map;
            }
            default -> value;
        };
    }

    private static Value bindValue(Value value, Context context) {
        return switch (value) {
            case AnyWordValue word -> context.contains(word.canonical())
                    ? word.bindTo(context.findTheHolderOf(word.canonical()))
                    : word;
            case AnyBlockValue block -> bind(block, context);
            case MapValue map -> {
                for (Value key : map.keys()) {
                    map.put(key, bindValue(map.select(key), context));
                }
                yield map;
            }
            default -> value;
        };
    }

    public static AnyBlockValue bindAndDefine(AnyBlockValue block, Context context) {
        for (Value item : block.remaining()) {
            defineWordsIn(item, context);
        }
        return bind(block, context);
    }

    private static void defineWordsIn(Value value, Context context) {
        switch (value) {
            case AnyWordValue word -> {
                if (!context.contains(word.canonical())) {
                    context.register(word.spelling());
                }
            }
            case AnyBlockValue nested -> {
                for (Value item : nested.remaining()) {
                    defineWordsIn(item, context);
                }
            }
            case MapValue map -> {
                for (Value stored : map.values()) {
                    defineWordsIn(stored, context);
                }
            }
            default -> {
            }
        }
    }
}
