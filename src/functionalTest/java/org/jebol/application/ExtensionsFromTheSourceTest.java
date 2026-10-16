package org.jebol.application;

import org.jebol.domain.eval.Postbox;
import org.jebol.domain.value.AnyBlockValue;
import org.jebol.domain.value.ErrorCategory;
import org.jebol.domain.value.FileValue;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.ObjectValue;
import org.jebol.domain.value.PortValue;
import org.jebol.domain.value.StringValue;
import org.jebol.domain.value.UnsetValue;
import org.jebol.domain.value.UrlValue;
import org.jebol.domain.value.Value;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExtensionsFromTheSourceTest {

    private static final int THE_MOST_ARGUMENTS_A_COMMAND_TAKES = 15;

    private final List<String> typesSeen = new ArrayList<>();

    private Value doubled(CommandCall call) {
        return IntegerValue.of(((IntegerValue) call.arguments().getFirst()).magnitude() * 2);
    }

    private Extension demo() {
        return new Extension("demo", "1.2.3", List.of(
                new ExtensionCommand("twice", "{Doubles a whole number.} value [integer!]", this::doubled),
                new ExtensionCommand("shout", "text [string!] /twice {Says it again.}", call -> {
                    String loud = ((StringValue) call.arguments().getFirst()).text().toUpperCase(Locale.ROOT);
                    return StringValue.of(call.refinements().contains("twice") ? loud + " " + loud : loud);
                }),
                new ExtensionCommand("kind-of", "value", call -> {
                    typesSeen.add(call.arguments().getFirst().datatype().literalSpelling());
                    return UnsetValue.unset();
                }),
                new ExtensionCommand("a-file", "", call -> FileValue.of("made.txt")),
                new ExtensionCommand("a-url", "", call -> UrlValue.of("http://example.com")),
                new ExtensionCommand("refuse", "", call -> {
                    throw call.failure(ErrorCategory.ACCESS, "cannot-open", FileValue.of("camera"));
                }),
                new ExtensionCommand("fall-over", "", call -> {
                    throw new IllegalStateException("the camera fell over");
                }),
                new ExtensionCommand("run-it", "code [block!]",
                        call -> call.evaluated((AnyBlockValue) call.arguments().getFirst()))));
    }

    private Interpreter with(Extension... extensions) {
        Bounds bounds = Bounds.standard();
        for (Extension extension : extensions) {
            bounds = bounds.add(extension);
        }
        return Interpreter.withBounds(bounds);
    }

    private String answerTo(Interpreter interpreter, String source) {
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private String refusalIn(Interpreter interpreter, String source) {
        return answerTo(interpreter, """
                e: try [%s]
                either error? e [reduce [e/id e/arg1 e/arg2 e/arg3]] ['no-error]""".formatted(source));
    }

    @Nested
    @DisplayName("a command is called as a built-in is")
    class CallingACommand {

        @Test
        @DisplayName("a script calls it by its name")
        void aScriptCallsIt() {
            assertThat(answerTo(with(demo()), "twice 21")).isEqualTo("42");
        }

        @Test
        @DisplayName("its datatype is command!, as an extension's words are in R3")
        void itsDatatypeIsCommand() {
            assertThat(answerTo(with(demo()), "type? :twice")).isEqualTo("#(command!)");
        }

        @Test
        @DisplayName("its spec is the one the extension wrote, help text included")
        void itsSpecIsTheOneWritten() {
            assertThat(answerTo(with(demo()), "spec-of :twice"))
                    .isEqualTo("[\"Doubles a whole number.\" value [integer!]]");
        }

        @Test
        @DisplayName("a wrong datatype is expect-arg naming the word, the argument and the datatype")
        void aWrongDatatypeIsExpectArg() {
            assertThat(refusalIn(with(demo()), "twice {x}"))
                    .isEqualTo("[expect-arg twice value #(string!)]");
        }

        @Test
        @DisplayName("a refinement reaches the command")
        void aRefinementReachesIt() {
            Interpreter interpreter = with(demo());

            assertThat(answerTo(interpreter, "shout {hi}")).isEqualTo("\"HI\"");
            assertThat(answerTo(interpreter, "shout/twice {hi}")).isEqualTo("\"HI HI\"");
        }

        @Test
        @DisplayName("values arrive as themselves: a file! as a file! and a block as a block")
        void valuesArriveAsThemselves() {
            answerTo(with(demo()), "kind-of %a.txt kind-of {a.txt} kind-of [1 2] kind-of http://x.y");

            assertThat(typesSeen).containsExactly("file!", "string!", "block!", "url!");
        }

        @Test
        @DisplayName("and go back as themselves")
        void valuesGoBackAsThemselves() {
            Interpreter interpreter = with(demo());

            assertThat(answerTo(interpreter, "reduce [a-file type? a-file a-url type? a-url]"))
                    .isEqualTo("[%made.txt #(file!) http://example.com #(url!)]");
        }

        @Test
        @DisplayName("a command raises a catalogue error with its own id and arguments")
        void aCommandRaisesACatalogueError() {
            assertThat(refusalIn(with(demo()), "refuse")).isEqualTo("[cannot-open %camera _ _]");
        }

        @Test
        @DisplayName("Java failing behind a command is command-fail carrying its message, and a script catches it")
        void aJavaFailureIsCommandFail() {
            assertThat(refusalIn(with(demo()), "fall-over"))
                    .isEqualTo("[command-fail \"the camera fell over\" _ _]");
        }

        @Test
        @DisplayName("a command evaluates a block it was given, in the interpreter it runs in")
        void aCommandEvaluatesABlock() {
            assertThat(answerTo(with(demo()), "x: 20 run-it [x + 22]")).isEqualTo("42");
        }

        @Test
        @DisplayName("fifteen arguments is the most a command takes")
        void fifteenArguments() {
            assertThat(answerTo(with(withArguments(THE_MOST_ARGUMENTS_A_COMMAND_TAKES)),
                    "many " + "1 ".repeat(THE_MOST_ARGUMENTS_A_COMMAND_TAKES)))
                    .isEqualTo(String.valueOf(THE_MOST_ARGUMENTS_A_COMMAND_TAKES));
        }

        @Test
        @DisplayName("sixteen is bad-command, as R3's MAX_RXI_ARGS makes it")
        void sixteenArguments() {
            int oneTooMany = THE_MOST_ARGUMENTS_A_COMMAND_TAKES + 1;

            assertThat(refusalIn(with(withArguments(oneTooMany)), "many " + "1 ".repeat(oneTooMany)))
                    .isEqualTo("[bad-command _ _ _]");
        }

        private Extension withArguments(int count) {
            StringBuilder spec = new StringBuilder();
            for (int at = 1; at <= count; at++) {
                spec.append("a").append(at).append(' ');
            }
            return new Extension("many-arguments", "1.0.0", List.of(new ExtensionCommand(
                    "many", spec.toString(), call -> IntegerValue.of(call.arguments().size()))));
        }
    }

    @Nested
    @DisplayName("an installed extension is a module whose words are in lib")
    class WhereItLives {

        @Test
        @DisplayName("its words are in lib")
        void itsWordsAreInLib() {
            Interpreter interpreter = with(demo());

            assertThat(answerTo(interpreter, "lib/twice 2")).isEqualTo("4");
            assertThat(answerTo(interpreter, "true? find words-of lib 'twice")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("it is listed in system/modules under its name, with its version")
        void itIsListedInSystemModules() {
            Interpreter interpreter = with(demo());

            assertThat(answerTo(interpreter, "module? select system/modules 'demo")).isEqualTo("#(true)");
            assertThat(answerTo(interpreter, "select spec-of select system/modules 'demo 'version"))
                    .isEqualTo("1.2.3");
        }

        @Test
        @DisplayName("IMPORT by name answers that module")
        void importAnswersIt() {
            assertThat(answerTo(with(demo()), "same? import 'demo select system/modules 'demo"))
                    .isEqualTo("#(true)");
        }

        @Test
        @DisplayName("every interpreter built with the same bounds gets it")
        void everyInterpreterWithTheBoundsGetsIt() {
            Bounds bounds = Bounds.standard().add(demo());

            assertThat(answerTo(Interpreter.withBounds(bounds), "twice 1")).isEqualTo("2");
            assertThat(answerTo(Interpreter.withBounds(bounds), "twice 2")).isEqualTo("4");
        }

        @Test
        @DisplayName("an interpreter whose bounds do not carry it has no such word")
        void boundsWithoutItHaveNone() {
            assertThat(refusalIn(Interpreter.create(), "twice 1")).startsWith("[no-value twice");
        }

        @Test
        @DisplayName("the host can list what was installed")
        void theHostCanListIt() {
            assertThat(with(demo()).extensions())
                    .containsExactly(new ExtensionReport("demo", Optional.empty()));
        }
    }

    @Nested
    @DisplayName("the library is sealed against extensions, not against scripts")
    class TheSeal {

        @Test
        @DisplayName("a command named like a native is refused with already-used, and the native stands")
        void aNativesNameIsRefused() {
            Extension clashing = new Extension("clash", "1.0.0", List.of(
                    new ExtensionCommand("add", "a b", call -> StringValue.of("mine"))));

            Interpreter interpreter = with(clashing);

            assertThat(interpreter.extensions()).containsExactly(
                    new ExtensionReport("clash", Optional.of("already-used")));
            assertThat(answerTo(interpreter, "add 1 2")).isEqualTo("3");
        }

        @Test
        @DisplayName("Rebol's own REBOL functions are sealed too")
        void aMezzanineNameIsRefused() {
            Extension clashing = new Extension("clash", "1.0.0", List.of(
                    new ExtensionCommand("secure", "policy", call -> UnsetValue.unset())));

            assertThat(with(clashing).extensions().getFirst().refusal()).contains("already-used");
        }

        @Test
        @DisplayName("installing is all or nothing: a good command beside a clashing one is not installed either")
        void allOrNothing() {
            Extension halfGood = new Extension("half", "1.0.0", List.of(
                    new ExtensionCommand("fine-word", "", call -> IntegerValue.of(1)),
                    new ExtensionCommand("append", "a b", call -> IntegerValue.of(2))));

            Interpreter interpreter = with(halfGood);

            assertThat(refusalIn(interpreter, "fine-word")).startsWith("[no-value fine-word");
            assertThat(answerTo(interpreter, "none? select system/modules 'half")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("between two extensions with one name, the first to install wins")
        void theFirstWins() {
            Extension first = new Extension("first", "1.0.0", List.of(
                    new ExtensionCommand("greet", "", call -> StringValue.of("first"))));
            Extension second = new Extension("second", "1.0.0", List.of(
                    new ExtensionCommand("greet", "", call -> StringValue.of("second"))));

            Interpreter interpreter = with(first, second);

            assertThat(answerTo(interpreter, "greet")).isEqualTo("\"first\"");
            assertThat(interpreter.extensions()).containsExactly(
                    new ExtensionReport("first", Optional.empty()),
                    new ExtensionReport("second", Optional.of("already-used")));
        }

        @Test
        @DisplayName("a script may still assign into lib, as in r3")
        void aScriptMayStillAssign() {
            Interpreter interpreter = with(demo());

            assertThat(answerTo(interpreter, "lib/add: func [a b] [{mine}] lib/add 1 2")).isEqualTo("\"mine\"");
        }
    }

    @Nested
    @DisplayName("an extension that cannot be installed is reported and leaves nothing behind")
    class Refusals {

        @Test
        @DisplayName("a command spec that does not read is bad-extension")
        void anUnreadableSpec() {
            Extension broken = new Extension("broken", "1.0.0", List.of(
                    new ExtensionCommand("oops", "value [integer!", call -> UnsetValue.unset())));

            assertThat(with(broken).extensions().getFirst().refusal()).contains("bad-extension");
        }

        @Test
        @DisplayName("a version that is not a tuple is bad-extension")
        void aVersionThatIsNotATuple() {
            Extension broken = new Extension("broken", "one", List.of(
                    new ExtensionCommand("fine-word", "", call -> UnsetValue.unset())));

            assertThat(with(broken).extensions().getFirst().refusal()).contains("bad-extension");
        }

        @Test
        @DisplayName("a blank name is bad-extension")
        void aBlankName() {
            Extension nameless = new Extension(" ", "1.0.0", List.of());

            assertThat(with(nameless).extensions().getFirst().refusal()).contains("bad-extension");
        }

        @Test
        @DisplayName("two commands of one name in one extension is bad-extension")
        void twoCommandsOfOneName() {
            Extension doubled = new Extension("doubled", "1.0.0", List.of(
                    new ExtensionCommand("same", "", call -> UnsetValue.unset()),
                    new ExtensionCommand("SAME", "", call -> UnsetValue.unset())));

            assertThat(with(doubled).extensions().getFirst().refusal()).contains("bad-extension");
        }

        @Test
        @DisplayName("an extension missing a part is refused when it is built, before any interpreter sees it")
        void aMissingPart() {
            assertThatThrownBy(() -> new Extension(null, "1.0.0", List.of()))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new Extension("x", null, List.of()))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new Extension("x", "1.0.0", null))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new ExtensionCommand("x", null, call -> UnsetValue.unset()))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("a start that fails is extension-init, and the commands are not installed")
        void aStartThatFails() {
            Extension noCamera = new Extension("camera", "1.0.0", List.of(
                    new ExtensionCommand("snap", "", call -> UnsetValue.unset())))
                    .startingWith(postbox -> {
                        throw new IllegalStateException("no camera on this machine");
                    });

            Interpreter interpreter = with(noCamera);

            assertThat(interpreter.extensions().getFirst().refusal()).contains("extension-init");
            assertThat(refusalIn(interpreter, "snap")).startsWith("[no-value snap");
        }

        @Test
        @DisplayName("a handle kind already known holding another class is handle-exists")
        void aHandleKindWithAnotherClass() {
            Extension first = new Extension("first", "1.0.0", List.of())
                    .withHandleKind(new HandleKind("session", String.class));
            Extension second = new Extension("second", "1.0.0", List.of())
                    .withHandleKind(new HandleKind("session", Integer.class));

            assertThat(with(first, second).extensions().getLast().refusal()).contains("handle-exists");
        }

        @Test
        @DisplayName("the same kind holding the same class is shared")
        void theSameKindIsShared() {
            Extension first = new Extension("first", "1.0.0", List.of())
                    .withHandleKind(new HandleKind("session", String.class));
            Extension second = new Extension("second", "1.0.0", List.of())
                    .withHandleKind(new HandleKind("session", String.class));

            assertThat(with(first, second).extensions().getLast().refusal()).isEmpty();
        }
    }

    @Nested
    @DisplayName("an extension's own REBOL builds schemes and codecs as Rebol's own files do")
    class ItsOwnRebol {

        @Test
        @DisplayName("a scheme made in the extension's REBOL reads through its commands")
        void aScheme() {
            Extension greeter = new Extension("greeter", "1.0.0", List.of(
                    new ExtensionCommand("greeting-for", "who [string!]",
                            call -> StringValue.of("hello " + ((StringValue) call.arguments().getFirst()).text()))))
                    .withScript("""
                            sys/make-scheme [
                                name: 'greet
                                title: "Greetings"
                                actor: [
                                    read: func [port] [greeting-for port/spec/host]
                                ]
                            ]""");

            assertThat(answerTo(with(greeter), "read greet://world")).isEqualTo("\"hello world\"");
        }

        @Test
        @DisplayName("a codec registered in the extension's REBOL decodes through its commands")
        void aCodec() {
            Extension shouting = new Extension("shouting", "1.0.0", List.of(
                    new ExtensionCommand("louder", "text [string!]",
                            call -> StringValue.of(((StringValue) call.arguments().getFirst()).text()
                                    .toUpperCase(Locale.ROOT)))))
                    .withScript("""
                            register-codec [
                                name: 'shout
                                type: 'text
                                title: "Shouting"
                                suffixes: [%.shout]
                                decode: func [data] [louder to string! data]
                            ]""");

            assertThat(answerTo(with(shouting), "decode 'shout to binary! {quiet}")).isEqualTo("\"QUIET\"");
        }
    }

    @Nested
    @DisplayName("handles carry what a command made")
    class Handles {

        private Extension sessions() {
            return new Extension("sessions", "1.0.0", List.of(
                    new ExtensionCommand("open-session", "label [string!]",
                            call -> call.handleOf("session", ((StringValue) call.arguments().getFirst()).text())),
                    new ExtensionCommand("session-label", "session [handle!]",
                            call -> StringValue.of(call.heldBy(call.arguments().getFirst(), String.class)))))
                    .withHandleKind(new HandleKind("session", String.class));
        }

        @Test
        @DisplayName("a script holds a handle of the declared kind and hands it back")
        void aHandleGoesOutAndBack() {
            Interpreter interpreter = with(sessions());

            assertThat(answerTo(interpreter, "h: open-session {front} reduce [handle? h h/type session-label h]"))
                    .isEqualTo("[#(true) session \"front\"]");
        }

        @Test
        @DisplayName("a handle of another kind is refused as invalid-handle")
        void aHandleOfAnotherKind() {
            Interpreter interpreter = with(sessions());

            assertThat(refusalIn(interpreter, "session-label rc4/key #{00}")).startsWith("[invalid-handle");
        }
    }

    @Nested
    @DisplayName("an extension posts from any thread, and WAIT takes it")
    class Posting {

        private final AtomicReference<Postbox> kept = new AtomicReference<>();

        private record AnswerAwaited(CompletableFuture<Value> future) implements Postbox.CallbackAnswer {

            @Override
            public void answered(Value value) {
                future.complete(value);
            }

            @Override
            public void failed(RuntimeException failure) {
                future.completeExceptionally(failure);
            }
        }

        private Extension keepingItsPostbox() {
            return new Extension("watcher", "1.0.0", List.of()).startingWith(kept::set);
        }

        @Test
        @Timeout(30)
        @DisplayName("an event posted from another thread wakes a WAIT on its port")
        void anEventWakesAWait() {
            Interpreter interpreter = with(keepingItsPostbox());
            answerTo(interpreter, """
                    p: make port! [scheme: 'callback]
                    p/awake: func [event] [true]""");
            PortValue port = (PortValue) interpreter.run("p").value();

            Thread.ofPlatform().start(() -> kept.get().post(port, "read"));

            assertThat(answerTo(interpreter, "same? p wait [p 20]")).isEqualTo("#(true)");
        }

        @Test
        @Timeout(30)
        @DisplayName("a callback posted from another thread runs on the script's thread and hands back its answer")
        void aCallbackRuns() throws Exception {
            Interpreter interpreter = with(keepingItsPostbox());
            answerTo(interpreter, "listener: make object! [heard: func [what] [join {heard } what]]");
            ObjectValue listener = (ObjectValue) interpreter.run("listener").value();

            AnswerAwaited answered = new AnswerAwaited(new CompletableFuture<>());
            Thread.ofPlatform().start(() -> kept.get()
                    .callBack(listener, "heard", List.of(StringValue.of("a shutter")), answered));
            answerTo(interpreter, "wait [system/ports/callback 2]");

            assertThat(answered.future().get(10, TimeUnit.SECONDS)).isEqualTo(StringValue.of("heard a shutter"));
        }

        @Test
        @Timeout(30)
        @DisplayName("a callback that fails raises in the script and hands the failure back")
        void aCallbackThatFails() {
            Interpreter interpreter = with(keepingItsPostbox());
            answerTo(interpreter, "listener: make object! [heard: func [what] [1 / 0]]");
            ObjectValue listener = (ObjectValue) interpreter.run("listener").value();

            AnswerAwaited answered = new AnswerAwaited(new CompletableFuture<>());
            kept.get().callBack(listener, "heard", List.of(StringValue.of("a shutter")), answered);

            assertThat(refusalIn(interpreter, "wait [system/ports/callback 2]")).startsWith("[zero-divide");
            assertThat(answered.future()).isCompletedExceptionally();
        }

        @Test
        @DisplayName("DO-CALLBACK of an event that carries no callback answers none")
        void doCallbackOfAnOrdinaryEvent() {
            assertThat(answerTo(Interpreter.create(), "do-callback make event! [type: 'callback]")).isEqualTo("_");
        }

        @Test
        @Timeout(30)
        @DisplayName("a WAIT with nothing woken lets its time pass and answers none")
        void aWaitWithNothingWoken() {
            Interpreter interpreter = with(keepingItsPostbox());
            answerTo(interpreter, "p: make port! [scheme: 'callback]");
            long started = System.nanoTime();

            assertThat(answerTo(interpreter, "wait [p 0.3]")).isEqualTo("_");
            assertThat(System.nanoTime() - started).isGreaterThanOrEqualTo(TimeUnit.MILLISECONDS.toNanos(300));
        }
    }
}
