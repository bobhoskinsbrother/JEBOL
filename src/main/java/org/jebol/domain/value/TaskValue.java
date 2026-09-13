package org.jebol.domain.value;

import java.util.List;

/**
 * A task: a header of five fixed fields and a block of code to run.
 *
 * <p>Made from a block, and the block is read the way MAKE MODULE! reads one.
 * A block whose first item is a block is a spec and a body, and the spec's
 * set-words fill the header; anything else is a body alone, and the header is
 * the five names with nothing in them. {@code Make_Module_Spec} builds that
 * header either way, which is why the fields are the same five whatever was
 * written.
 *
 * <p>What a task does with its body is the host's business rather than the
 * language's: {@code Do_Task} hands it to {@code OS_Create_Thread}. Everything
 * a script can read off a task -- its fields, its molded form, whether DO
 * answers it -- is here; whether anything runs is in spec/natives.allium.
 */
public record TaskValue(Context context, BlockValue body) implements Value {

    /** The five names a task's header always has, in the order it has them. */
    public static final List<String> THE_FIELDS_A_TASK_HAS =
            List.of("title", "header", "parent", "path", "args");

    public TaskValue {
        if (context == null || context.isUnbound()) {
            throw new IllegalArgumentException("a task needs a real context");
        }
        if (body == null) {
            throw new IllegalArgumentException("a task needs a body, even an empty one");
        }
    }

    /** A task whose header holds nothing, which is what an empty spec gives. */
    public static TaskValue running(BlockValue body) {
        Context header = Context.root();
        THE_FIELDS_A_TASK_HAS.forEach(name -> header.set(name, NoneValue.none()));
        return new TaskValue(header, body);
    }

    @Override
    public Datatype datatype() {
        return Datatype.TASK;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof TaskValue task
                && context.fieldsExcludingSelf().equals(
                        task.context.fieldsExcludingSelf());
    }

    @Override
    public int hashCode() {
        return context.fieldsExcludingSelf().hashCode();
    }

    @Override
    public String toString() {
        return "task of " + context.slotCount() + " fields";
    }
}
