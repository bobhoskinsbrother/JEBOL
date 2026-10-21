package org.jebol.domain.value;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

final class KeysAlikeApartFromCase {

    private record Folded(Datatype type, Object form) {
    }

    private final Map<Object, List<Value>> storedByFolding = new HashMap<>();

    KeysAlikeApartFromCase(Collection<Value> stored) {
        stored.forEach(this::add);
    }

    Value findFirstStoredLike(Value wanted) {
        List<Value> alike = storedByFolding.getOrDefault(fold(wanted), List.of());
        return alike.isEmpty() ? NoneValue.none() : alike.getFirst();
    }

    void add(Value stored) {
        storedByFolding.merge(fold(stored), List.of(stored), this::join);
    }

    void remove(Value stored) {
        Object folding = fold(stored);
        List<Value> remaining = storedByFolding.getOrDefault(folding, List.of()).stream()
                .filter(alike -> !alike.equals(stored))
                .toList();
        if (remaining.isEmpty()) {
            storedByFolding.remove(folding);
        } else {
            storedByFolding.put(folding, remaining);
        }
    }

    void clear() {
        storedByFolding.clear();
    }

    private List<Value> join(List<Value> earlier, List<Value> later) {
        return Stream.concat(earlier.stream(), later.stream()).toList();
    }

    private Object fold(Value key) {
        return switch (key) {
            case AnyStringValue text -> new Folded(text.datatype(), foldTheText(text.text()));
            case AnyWordValue word -> new Folded(word.datatype(), word.canonical());
            case CharacterValue(int codepoint) -> new Folded(key.datatype(), Character.toLowerCase(codepoint));
            default -> key;
        };
    }

    private String foldTheText(String text) {
        StringBuilder folded = new StringBuilder(text.length());
        text.codePoints().forEach(codepoint ->
                folded.appendCodePoint(Character.toLowerCase(Character.toUpperCase(codepoint))));
        return folded.toString();
    }
}
