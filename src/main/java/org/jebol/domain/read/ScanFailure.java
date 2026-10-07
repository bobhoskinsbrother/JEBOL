package org.jebol.domain.read;

import org.jebol.domain.value.Value;

import java.io.Serial;
import java.util.List;
import java.util.Optional;

final class ScanFailure extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final transient SyntaxFailure failure;

    private final transient List<Value> arguments;

    private final transient Optional<String> near;

    private final boolean relaxable;

    ScanFailure(SyntaxFailure failure) {
        this(failure, List.of(), Optional.empty());
    }

    ScanFailure(SyntaxFailure failure, List<Value> arguments, Optional<String> near) {
        this(failure, arguments, near, false);
    }

    private ScanFailure(SyntaxFailure failure, List<Value> arguments, Optional<String> near, boolean relaxable) {
        super(failure.description(), null, false, false);
        this.failure = failure;
        this.arguments = arguments;
        this.near = near;
        this.relaxable = relaxable;
    }

    ScanFailure relaxable() {
        return new ScanFailure(failure, arguments, near, true);
    }

    boolean isRelaxable() {
        return relaxable;
    }

    TranscodeResult.Failure asAFailure() {
        return new TranscodeResult.Failure(failure, arguments, near);
    }
}
