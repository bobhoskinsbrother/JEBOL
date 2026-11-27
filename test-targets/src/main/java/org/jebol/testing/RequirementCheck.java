package org.jebol.testing;

import java.util.Optional;
import java.util.Set;

public interface RequirementCheck {

    Set<Needs> answers();

    Optional<String> shortfall(Needs need);

    boolean requirementsMustBeMet();
}
