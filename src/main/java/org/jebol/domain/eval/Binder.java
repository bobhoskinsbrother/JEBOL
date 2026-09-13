package org.jebol.domain.eval;

import org.jebol.domain.value.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;


public final class Binder {

    private Binder() {
    }

    public static BlockValue bind(BlockValue block, Context context) {
        List<Value> bound = new ArrayList<>(block.lengthFromHere());
        for (Value item : block.remaining()) {
            bound.add(bindValue(item, context));
        }
        return laidOutLike(block, new BlockStorage(bound));
    }

    private static BlockValue laidOutLike(BlockValue older, BlockStorage bound) {
        bound.takeLineBreaksFrom(older.storage(), older.index());
        return new BlockValue(bound, 1, older.datatype());
    }

    public static BlockValue bindInPlace(BlockValue block, Context context) {
        for (int at = 0; at < block.lengthFromHere(); at++) {
            int where = block.index() + at;
            Value bound = bindValue(block.storage().at(where), context);
            if (bound instanceof WordValue word) {
                block.storage().rebindAt(where, word);
            } else {
                block.storage().set(where, bound);
            }
        }
        return block;
    }

    public static BlockValue bindWhatTheTargetHoldsItself(
            BlockValue block, Context target) {

        return bindWhatTheTargetHoldsItself(block, target, ALL_THE_WAY_DOWN);
    }

    public static BlockValue bindWhatTheTargetHoldsItself(
            BlockValue block, Context target, boolean deeply) {

        for (int at = 0; at < block.lengthFromHere(); at++) {
            int where = block.index() + at;
            Value item = block.storage().at(where);
            if (item instanceof WordValue word) {
                if (target.holds(word.canonical())) {
                    block.storage().rebindAt(where, word.boundTo(target));
                }
            } else if (deeply) {
                boundIfTheTargetHoldsIt(item, target);
            }
        }
        return block;
    }

    public static final boolean ALL_THE_WAY_DOWN = true;

    public static final boolean THE_TOP_LEVEL_ONLY = false;

    public static BlockValue bindACopyOfWhatTheTargetHoldsItself(
            BlockValue block, Context target) {

        return bindACopyOfWhatTheTargetHoldsItself(block, target, ALL_THE_WAY_DOWN);
    }

    public static BlockValue bindACopyOfWhatTheTargetHoldsItself(
            BlockValue block, Context target, boolean deeply) {

        return bindWhatTheTargetHoldsItself(aDeepCopyOf(block), target, deeply);
    }

    private static BlockValue aDeepCopyOf(BlockValue block) {
        List<Value> copied = new ArrayList<>(block.lengthFromHere());
        for (Value item : block.remaining()) {
            copied.add(item instanceof BlockValue nested ? aDeepCopyOf(nested) : item);
        }
        return laidOutLike(block, new BlockStorage(copied));
    }

    private static Value boundIfTheTargetHoldsIt(Value value, Context target) {
        return switch (value) {
            case WordValue word when target.holds(word.canonical()) ->
                    word.boundTo(target);
            case WordValue word -> word;
            case BlockValue nested -> bindWhatTheTargetHoldsItself(nested, target);
            case MapValue map -> {
                for (Value key : map.keys()) {
                    map.put(key, boundIfTheTargetHoldsIt(map.select(key), target));
                }
                yield map;
            }
            default -> value;
        };
    }

    public static BlockValue bindOnly(
            BlockValue block, Context context, Set<String> names) {

        List<Value> bound = new ArrayList<>(block.lengthFromHere());
        for (Value item : block.remaining()) {
            bound.add(bindValueOnly(item, context, names));
        }
        return laidOutLike(block, new BlockStorage(bound));
    }

    public static void bindEachInPlace(
            BlockValue block, Context context, Set<String> names) {

        bindEachInPlace(block, context, names,
                java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>()));
    }

    private static void bindEachInPlace(BlockValue block, Context context,
            Set<String> names, Set<Object> alreadyWalked) {

        if (!alreadyWalked.add(block.storage())) {
            return;
        }
        for (int at = block.index(); at <= block.storageLength(); at++) {
            switch (block.storage().at(at)) {
                case WordValue word when names.contains(word.canonical()) ->
                        block.storage().rebindAt(at, word.boundTo(context));
                case BlockValue nested ->
                        bindEachInPlace(nested, context, names, alreadyWalked);
                case MapValue map -> {
                    for (Value key : map.keys()) {
                        map.put(key, boundIfDeclared(
                                map.select(key), context, names, alreadyWalked));
                    }
                }
                default -> { }
            }
        }
    }

    public static BlockValue rebindWhatNamedTheFunction(
            BlockValue block, Context from, Context to) {

        List<Value> bound = new ArrayList<>(block.lengthFromHere());
        for (Value item : block.remaining()) {
            bound.add(rebindOneThatNamedIt(item, from, to));
        }
        return laidOutLike(block, new BlockStorage(bound));
    }

    private static Value rebindOneThatNamedIt(Value value, Context from, Context to) {
        return switch (value) {
            case WordValue word when word.isBound() && word.binding() == from ->
                    word.boundTo(to);
            case WordValue word -> word;
            case BlockValue nested -> rebindWhatNamedTheFunction(nested, from, to);
            case MapValue map -> {
                for (Value key : map.keys()) {
                    map.put(key, rebindOneThatNamedIt(map.select(key), from, to));
                }
                yield map;
            }
            default -> value;
        };
    }

    private static Value boundIfDeclared(Value held, Context context,
            Set<String> names, Set<Object> alreadyWalked) {

        if (held instanceof WordValue word && names.contains(word.canonical())) {
            return word.boundTo(context);
        }
        if (held instanceof BlockValue nested) {
            bindEachInPlace(nested, context, names, alreadyWalked);
        }
        return held;
    }

    private static Value bindValueOnly(
            Value value, Context context, Set<String> names) {

        return switch (value) {
            case WordValue word when names.contains(word.canonical()) ->
                    word.boundTo(context.knows(word.canonical())
                            ? context.holderOf(word.canonical())
                            : context);
            case WordValue word -> word;
            case BlockValue nested -> bindOnly(nested, context, names);
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
            case WordValue word -> context.knows(word.canonical())
                    ? word.boundTo(context.holderOf(word.canonical()))
                    : word;
            case BlockValue block -> bind(block, context);
            case MapValue map -> {
                for (Value key : map.keys()) {
                    map.put(key, bindValue(map.select(key), context));
                }
                yield map;
            }
            default -> value;
        };
    }

    public static BlockValue bindAndDefine(BlockValue block, Context context) {
        for (Value item : block.remaining()) {
            defineWordsIn(item, context);
        }
        return bind(block, context);
    }

    private static void defineWordsIn(Value value, Context context) {
        switch (value) {
            case WordValue word -> {
                if (!context.knows(word.canonical())) {
                    context.define(word.spelling());
                }
            }
            case BlockValue nested -> {
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
