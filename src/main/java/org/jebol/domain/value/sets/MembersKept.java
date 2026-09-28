package org.jebol.domain.value.sets;

import org.jebol.domain.value.Value;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;

public record MembersKept(SetOperation how, int stride, BiPredicate<Value, Value> same) {

    public List<Value> from(List<Value> ourMembers, List<Value> theirMembers) {
        List<List<Value>> ours = inRecords(ourMembers);
        List<List<Value>> theirs = inRecords(theirMembers);
        List<List<Value>> kept = new ArrayList<>();
        for (List<Value> candidate : ours) {
            if (how.theFirstSetKeeps(holds(theirs, candidate)) && isNew(kept, candidate)) {
                kept.add(candidate);
            }
        }
        if (how.theSecondSetContributes()) {
            for (List<Value> candidate : theirs) {
                if (how.theSecondSetKeeps(holds(ours, candidate))
                        && isNew(kept, candidate)) {
                    kept.add(candidate);
                }
            }
        }
        return kept.stream().flatMap(List::stream).toList();
    }

    private List<List<Value>> inRecords(List<Value> items) {
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
        return records.stream().anyMatch(each -> sameRecord(each, candidate));
    }

    private boolean sameRecord(List<Value> ours, List<Value> theirs) {
        if (ours.isEmpty() || theirs.isEmpty()) {
            return ours.isEmpty() && theirs.isEmpty();
        }
        return same.test(ours.getFirst(), theirs.getFirst());
    }
}
