package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.Evaluator;
import org.jebol.domain.eval.FilePort;
import org.jebol.domain.eval.ProcessPort;
import org.jebol.domain.value.BinaryValue;
import org.jebol.domain.value.Context;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.EvaluationFailure;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.ObjectValue;
import org.jebol.domain.value.Raised;
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
                .filter(value -> value instanceof BinaryValue || value.datatype() == Datatype.STRING)
                .isPresent();
    }

    Value answerThrough(ProcessPort port, Evaluator evaluator) {
        ProcessPort.ProgramResult result = port.run(toStart(evaluator));
        output.ifPresent(buffer -> result.capturedOutput().ifPresent(bytes -> appendedInto(buffer, bytes)));
        errors.ifPresent(buffer -> result.capturedError().ifPresent(bytes -> appendedInto(buffer, bytes)));
        if (answersAnObject) {
            return informationObject(result, evaluator);
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

    private ProcessPort.ProgramToStart toStart(Evaluator evaluator) {
        return new ProcessPort.ProgramToStart(
                command, readByTheShell, attachedToTheHostsConsole, waits,
                inputKindOf(input), pipedBytesOf(input), fileOf(input, evaluator),
                outputKindOf(output), fileOf(output, evaluator),
                outputKindOf(errors), fileOf(errors, evaluator),
                whatTheChildInherits(evaluator),
                whereTheChildStarts(evaluator));
    }

    private Optional<String> whereTheChildStarts(Evaluator evaluator) {
        try {
            return Optional.of(evaluator.files().hostPathOf(evaluator.files().workingDirectory()));
        } catch (FilePort.Denied noFilesystem) {
            return Optional.empty();
        }
    }

    private Map<String, String> whatTheChildInherits(Evaluator evaluator) {
        try {
            return evaluator.environment().all();
        } catch (FilePort.Denied noEnvironment) {
            return Map.of();
        }
    }

    private ProcessPort.ProgramInput inputKindOf(Optional<Value> redirection) {
        if (redirection.isEmpty()) {
            return ProcessPort.ProgramInput.THE_HOSTS_OWN;
        }
        return switch (redirection.orElseThrow()) {
            case BinaryValue piped -> ProcessPort.ProgramInput.SUPPLIED_BYTES;
            case StringValue text when text.datatype() == Datatype.STRING ->
                    ProcessPort.ProgramInput.SUPPLIED_BYTES;
            case StringValue address -> ProcessPort.ProgramInput.A_FILES_CONTENTS;
            default -> ProcessPort.ProgramInput.NOTHING_AT_ALL;
        };
    }

    private ProcessPort.ProgramOutput outputKindOf(Optional<Value> redirection) {
        if (redirection.isEmpty()) {
            return ProcessPort.ProgramOutput.THE_HOSTS_OWN;
        }
        return switch (redirection.orElseThrow()) {
            case BinaryValue captured -> ProcessPort.ProgramOutput.CAPTURED;
            case StringValue text when text.datatype() == Datatype.STRING ->
                    ProcessPort.ProgramOutput.CAPTURED;
            case StringValue address -> ProcessPort.ProgramOutput.INTO_A_FILE;
            default -> ProcessPort.ProgramOutput.DISCARDED;
        };
    }

    private Optional<byte[]> pipedBytesOf(Optional<Value> redirection) {
        return redirection.flatMap(value -> switch (value) {
            case BinaryValue binary -> Optional.of(binary.octetsFromHere());
            case StringValue text when text.datatype() == Datatype.STRING ->
                    Optional.of(text.text().getBytes(StandardCharsets.UTF_8));
            default -> Optional.empty();
        });
    }

    private Optional<String> fileOf(Optional<Value> redirection, Evaluator evaluator) {
        return redirection
                .filter(value -> value.datatype() == Datatype.FILE)
                .map(value -> whereReadWouldResolveIt(((StringValue) value).text(), evaluator));
    }

    private String whereReadWouldResolveIt(String path, Evaluator evaluator) {
        try {
            return evaluator.files().hostPathOf(path);
        } catch (FilePort.Denied noDirectoryToAsk) {
            return path;
        }
    }

    private void appendedInto(Value buffer, byte[] bytes) {
        switch (buffer) {
            case BinaryValue binary -> {
                for (byte octet : bytes) {
                    binary.storage().append(octet & 0xFF);
                }
            }
            case StringValue text when text.datatype() == Datatype.STRING ->
                    new String(bytes, StandardCharsets.UTF_8).codePoints().forEach(text.storage()::append);
            default -> theBufferIsNeitherAStringNorABinary();
        }
    }

    private void theBufferIsNeitherAStringNorABinary() {
    }

    private Value informationObject(ProcessPort.ProgramResult result, Evaluator evaluator) {
        Context fields = Context.childOf(evaluator.systemContext());
        ObjectValue built = new ObjectValue(fields);
        fields.register("self", built);
        fields.register("id", IntegerValue.of(result.processNumber()));
        if (waits && result.exitCode().isPresent()) {
            fields.register("exit-code", IntegerValue.of(result.exitCode().orElseThrow()));
        }
        result.refusalMessage().ifPresent(message -> fields.register("error", StringValue.of(message)));
        return built;
    }
}
