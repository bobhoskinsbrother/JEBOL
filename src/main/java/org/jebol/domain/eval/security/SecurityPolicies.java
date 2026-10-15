package org.jebol.domain.eval.security;

import org.jebol.domain.value.AnyBlockValue;
import org.jebol.domain.value.AnyStringValue;
import org.jebol.domain.value.AnyWordValue;
import org.jebol.domain.value.BinaryValue;
import org.jebol.domain.value.Context;
import org.jebol.domain.value.EvaluationFailure;
import org.jebol.domain.value.FileValue;
import org.jebol.domain.value.Molder;
import org.jebol.domain.value.ObjectValue;
import org.jebol.domain.value.Raised;
import org.jebol.domain.value.TupleValue;
import org.jebol.domain.value.Value;
import org.jebol.domain.value.WordValue;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

public final class SecurityPolicies {

    private final Value policies;

    private final boolean fileNamesFoldCase;

    public SecurityPolicies(Value policies) {
        this(policies, false);
    }

    public SecurityPolicies(Value policies, boolean fileNamesFoldCase) {
        this.policies = policies;
        this.fileNamesFoldCase = fileNamesFoldCase;
    }

    public PolicyLevel levelFor(PolicyClass kind, PolicyAccess access, Optional<Value> subject) {
        int written = theTupleFor(kind, subject).octetAt(access.ordinal() + 1);
        PolicyLevel[] levels = PolicyLevel.values();
        return written < levels.length ? levels[written] : PolicyLevel.ALLOW;
    }

    private TupleValue theTupleFor(PolicyClass kind, Optional<Value> subject) {
        Value held = theFieldFor(kind);
        if (held instanceof TupleValue tuple) {
            return tuple;
        }
        if (!(held instanceof AnyBlockValue entries)) {
            throw malformed(held);
        }
        return theTupleAmong(entries.remaining(), kind, subject);
    }

    private Value theFieldFor(PolicyClass kind) {
        if (policies instanceof ObjectValue(Context fields) && fields.hasItsOwnSlotFor(kind.word())) {
            return fields.ownSlotFor(kind.word()).value();
        }
        throw malformed(WordValue.of(kind.word()));
    }

    private TupleValue theTupleAmong(List<Value> entries, PolicyClass kind, Optional<Value> subject) {
        Optional<TupleValue> theDefault = Optional.empty();
        for (int at = 0; at < entries.size(); at += 2) {
            Value key = entries.get(at);
            if (at + 1 >= entries.size() || !(entries.get(at + 1) instanceof TupleValue flags)) {
                throw malformed(key);
            }
            if (key instanceof WordValue) {
                theDefault = Optional.of(flags);
            } else if (isAName(key) && subject.isPresent()) {
                if (covers(key, subject.get())) {
                    return flags;
                }
            } else {
                throw malformed(key);
            }
        }
        return theDefault.orElseThrow(() -> Raised.of(EvaluationFailure.SECURITY,
                subject.orElseGet(() -> WordValue.of(kind.word()))));
    }

    private boolean isAName(Value key) {
        return key instanceof AnyStringValue || key instanceof BinaryValue;
    }

    private boolean covers(Value name, Value subject) {
        boolean foldCase = !(name instanceof FileValue) || fileNamesFoldCase;
        int[] named = theTextOf(name).codePoints().toArray();
        int[] asked = theTextOf(subject).codePoints().toArray();
        if (named.length > asked.length) {
            return false;
        }
        for (int at = 0; at < named.length; at++) {
            if (!theSameCharacter(named[at], asked[at], foldCase)) {
                return false;
            }
        }
        return true;
    }

    private boolean theSameCharacter(int named, int asked, boolean foldCase) {
        return foldCase
                ? Character.toLowerCase(named) == Character.toLowerCase(asked)
                : named == asked;
    }

    private String theTextOf(Value value) {
        return switch (value) {
            case AnyStringValue text -> text.text();
            case BinaryValue bytes -> new String(bytes.octetsFromHere(), StandardCharsets.UTF_8);
            case AnyWordValue word -> word.spelling();
            default -> Molder.form(value);
        };
    }

    private Raised malformed(Value naming) {
        return Raised.of(EvaluationFailure.SECURITY_ERROR, naming);
    }
}
