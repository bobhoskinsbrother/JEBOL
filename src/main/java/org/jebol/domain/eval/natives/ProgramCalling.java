package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.Evaluator;
import org.jebol.domain.host.FilePort;
import org.jebol.domain.host.ProcessPort;
import org.jebol.domain.value.BinaryValue;
import org.jebol.domain.value.Context;
import org.jebol.domain.value.EvaluationFailure;
import org.jebol.domain.value.FileValue;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.ObjectValue;
import org.jebol.domain.value.Raised;
import org.jebol.domain.value.AnyStringValue;
import org.jebol.domain.value.StringValue;
import org.jebol.domain.value.Value;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

final class ProgramCalling {

    private final List<String> command;
    private final boolean readByTheShell;
    private final boolean attachedToTheHostsConsole;
    private final boolean waits;
    private final boolean answersAnObject;
    private final Optional<Value> input;
    private final Optional<Value> output;
    private final Optional<Value> errors;

    ProgramCalling(List<String> command, Set<String> refinements,
            Optional<Value> input, Optional<Value> output, Optional<Value> errors) {
        this.command = command;
        this.readByTheShell = refinements.contains("shell");
        this.attachedToTheHostsConsole = refinements.contains("console");
        this.answersAnObject = refinements.contains("info");
        this.input = input;
        this.output = output;
        this.errors = errors;
        this.waits = refinements.contains("wait")
                || isASeries(input) || isASeries(output) || isASeries(errors);
    }

    private boolean isASeries(Optional<Value> redirection) {
        return redirection
                .filter(value -> value instanceof BinaryValue || value instanceof StringValue)
                .isPresent();
    }

    Value answerThrough(ProcessPort port, Evaluator evaluator) {
        ProcessPort.ProgramResult result = port.run(toStart(evaluator));
        output.ifPresent(buffer -> result.capturedOutput().ifPresent(bytes -> appendInto(buffer, bytes)));
        errors.ifPresent(buffer -> result.capturedError().ifPresent(bytes -> appendInto(buffer, bytes)));
        if (answersAnObject) {
            return makeInformationObject(result, evaluator);
        }
        if (result.refusalMessage().isPresent()) {
            throw Raised.of(EvaluationFailure.CALL_FAIL, StringValue.of(result.refusalMessage().orElseThrow()));
        }
        if (waits && result.exitCode().isEmpty()) {
            throw Raised.of(EvaluationFailure.CALL_FAIL,
                    "the host waited for the program and answered no exit code");
        }
        return IntegerValue.of(waits ? result.exitCode().orElseThrow() : result.processNumber());
    }

    private ProcessPort.Program toStart(Evaluator evaluator) {
        return new ProcessPort.Program(
                command, readByTheShell, attachedToTheHostsConsole, waits,
                chooseTheInputKind(input), readThePipedBytes(input), readTheFile(input, evaluator),
                chooseTheOutputKind(output), readTheFile(output, evaluator),
                chooseTheOutputKind(errors), readTheFile(errors, evaluator),
                listWhatTheChildInherits(evaluator),
                findWhereTheChildStarts(evaluator));
    }

    private Optional<String> findWhereTheChildStarts(Evaluator evaluator) {
        try {
            return Optional.of(evaluator.ports().files().hostPath(evaluator.ports().files().workingDirectory()));
        } catch (FilePort.Denied noFilesystem) {
            return Optional.empty();
        }
    }

    private Map<String, String> listWhatTheChildInherits(Evaluator evaluator) {
        try {
            return evaluator.ports().environment().readAll();
        } catch (FilePort.Denied noEnvironment) {
            return Map.of();
        }
    }

    private ProcessPort.ProgramInput chooseTheInputKind(Optional<Value> redirection) {
        if (redirection.isEmpty()) {
            return ProcessPort.ProgramInput.HOSTS_OWN;
        }
        return switch (redirection.orElseThrow()) {
            case BinaryValue piped -> ProcessPort.ProgramInput.SUPPLIED_BYTES;
            case StringValue typed -> ProcessPort.ProgramInput.SUPPLIED_BYTES;
            case AnyStringValue address -> ProcessPort.ProgramInput.FILE_CONTENTS;
            default -> ProcessPort.ProgramInput.NOTHING_AT_ALL;
        };
    }

    private ProcessPort.ProgramOutput chooseTheOutputKind(Optional<Value> redirection) {
        if (redirection.isEmpty()) {
            return ProcessPort.ProgramOutput.HOSTS_OWN;
        }
        return switch (redirection.orElseThrow()) {
            case BinaryValue captured -> ProcessPort.ProgramOutput.CAPTURED;
            case StringValue typed -> ProcessPort.ProgramOutput.CAPTURED;
            case AnyStringValue address -> ProcessPort.ProgramOutput.INTO_FILE;
            default -> ProcessPort.ProgramOutput.DISCARDED;
        };
    }

    private Optional<byte[]> readThePipedBytes(Optional<Value> redirection) {
        return redirection.flatMap(value -> switch (value) {
            case BinaryValue binary -> Optional.of(binary.octetsFromHere());
            case StringValue text ->
                    Optional.of(text.text().getBytes(StandardCharsets.UTF_8));
            default -> Optional.empty();
        });
    }

    private Optional<String> readTheFile(Optional<Value> redirection, Evaluator evaluator) {
        return redirection
                .filter(FileValue.class::isInstance)
                .map(value -> resolveAsReadWould(((FileValue) value).text(), evaluator));
    }

    private String resolveAsReadWould(String path, Evaluator evaluator) {
        try {
            return evaluator.ports().files().hostPath(path);
        } catch (FilePort.Denied noDirectoryToAsk) {
            return path;
        }
    }

    private void appendInto(Value buffer, byte[] bytes) {
        switch (buffer) {
            case BinaryValue binary -> {
                for (byte octet : bytes) {
                    binary.storage().append(octet & 0xFF);
                }
            }
            case StringValue text ->
                    new String(bytes, StandardCharsets.UTF_8).codePoints().forEach(text.storage()::append);
            default -> refuseABufferThatIsNeitherAStringNorABinary();
        }
    }

    private void refuseABufferThatIsNeitherAStringNorABinary() {
    }

    private Value makeInformationObject(ProcessPort.ProgramResult result, Evaluator evaluator) {
        Context fields = Context.makeChildOf(evaluator.systemContext());
        ObjectValue built = new ObjectValue(fields);
        fields.pointItsOwnSelfAt(built);
        fields.register("id", IntegerValue.of(result.processNumber()));
        if (waits && result.exitCode().isPresent()) {
            fields.register("exit-code", IntegerValue.of(result.exitCode().orElseThrow()));
        }
        result.refusalMessage().ifPresent(message -> fields.register("error", StringValue.of(message)));
        return built;
    }
}
