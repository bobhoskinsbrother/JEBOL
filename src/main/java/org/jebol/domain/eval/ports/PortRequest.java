package org.jebol.domain.eval.ports;

import org.jebol.domain.value.Value;

import java.util.Optional;
import java.util.Set;

public record PortRequest(Optional<Value> part, Optional<Value> seek, Set<String> refinements) {
}
