package org.jebol.domain.value.sets;

import org.jebol.domain.value.Value;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;

public record MembersKept(SetOperation how, int stride, BiPredicate<Value, Value> same) {

    public List<Value> from(List<Value> ourMembers, List<Value> theirMembers) {
        List<List<Value>> ours = groupIntoRecords(ourMembers);
        List<List<Value>> theirs = groupIntoRecords(theirMembers);
        List<List<Value>> kept = new ArrayList<>();
        for (List<Value> candidate : ours) {
            if (how.keepsFromFirstSet(holds(theirs, candidate)) && isNew(kept, candidate)) {
                kept.add(candidate);
            }
        }
        if (how.takesFromTheSecondSet()) {
            for (List<Value> candidate : theirs) {
                if (how.keepsFromTheSecondSet(holds(ours, candidate))
                        && isNew(kept, candidate)) {
                    kept.add(candidate);
                }
            }
        }
        return kept.stream().flatMap(List::stream).toList();
    }

    private List<List<Value>> groupIntoRecords(List<Value> items) {
        List<List<Value>> records = new ArrayList<>();
        for (int at = 0; at < items.size(); at += stride) {
            records.add(items.subList(at, Math.min(at + stride, items.size())));
        }
        return records;
    }

    private boolean isNew(List<List<Value>> kept, List<Value> candidate) {
        return !holds(kept, candidate);
    }

    private boolean holds(List<List<Value>> records, List<Value> candidate) {
        return records.stream().anyMatch(each -> isSameRecord(each, candidate));
    }

    private boolean isSameRecord(List<Value> ours, List<Value> theirs) {
        if (ours.isEmpty() || theirs.isEmpty()) {
            return ours.isEmpty() && theirs.isEmpty();
        }
        return same.test(ours.getFirst(), theirs.getFirst());
    }
}
