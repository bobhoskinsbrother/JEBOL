package org.jebol.application;

import org.jebol.adapter.host.JavaImages;
import org.jebol.adapter.host.JavaProcesses;
import org.jebol.adapter.host.JavaSockets;
import org.jebol.adapter.host.ProcessEnvironment;
import org.jebol.domain.host.ConsolePort;
import org.jebol.domain.host.HostService;
import org.jebol.domain.value.IntegerValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityPoliciesFromTheSourceTest {

    @TempDir
    Path root;

    private Interpreter interpreter;

    @BeforeEach
    void layOutAnAppBesideThingsOutsideIt() throws IOException {
        Files.createDirectories(root.resolve("app"));
        Files.createDirectories(root.resolve("outdir"));
        Files.writeString(root.resolve("app").resolve("in.txt"), "inside");
        Files.writeString(root.resolve("out.txt"), "outside");
        Files.writeString(root.resolve("outdir").resolve("o.txt"), "o");
        interpreter = grantedEverything(Bounds.standard());
        answerTo("cd %/app/");
    }

    private Interpreter grantedEverything(Bounds bounds) {
        Bounds everything = bounds;
        for (HostService service : HostService.values()) {
            everything = everything.granting(service);
        }
        return withTheHostsAdapters(Interpreter.withBounds(everything));
    }

    private Interpreter withTheHostsAdapters(Interpreter made) {
        made.useFileSystem(FileSystemPort.rootedAt(root));
        made.useEnvironment(new ProcessEnvironment());
        made.useProcesses(new JavaProcesses());
        made.useNetwork(new JavaSockets());
        made.useImages(new JavaImages());
        return made;
    }

    private String answerTo(String source) {
        return answerTo(interpreter, source);
    }

    private String answerTo(Interpreter asked, String source) {
        asked.defineFreshWordsIn(source);
        return asked.display(asked.run(source));
    }

    private String refusalOf(String source) {
        return answerTo("""
                e: try [%s]
                either error? e [reduce [e/id e/arg1]] ['no-error]""".formatted(source));
    }

    private void onlyTheAppIsAllowed() {
        answerTo("secure (compose [file throw (%/app/) allow])");
    }

    @Nested
    @DisplayName("what each level does")
    class TheLevels {

        @Test
        @DisplayName("with every policy at allow, as an embedded interpreter starts, a file outside is read")
        void allowLetsItThrough() {
            assertThat(answerTo("to string! read %../out.txt")).isEqualTo("\"outside\"");
        }

        @Test
        @DisplayName("throw raises security naming the file where it really is")
        void throwRaisesSecurity() {
            answerTo("secure [file throw]");

            assertThat(refusalOf("read %in.txt")).isEqualTo("[security %/app/in.txt]");
        }

        @Test
        @DisplayName("ask with no terminal to answer refuses, as Rebol's own CONFIRM-POLICY does")
        void askWithNoTerminalRefuses() {
            answerTo("secure [file ask]");

            assertThat(refusalOf("read %in.txt")).isEqualTo("[security %/app/in.txt]");
        }

        @Test
        @DisplayName("quit ends the run with 101")
        void quitEndsTheRun() {
            answerTo("secure [file quit]");
            interpreter.defineFreshWordsIn("read %in.txt print {after}");

            ScriptOutcome outcome = interpreter.run("read %in.txt print {after}");

            assertThat(outcome.conclusion()).isEqualTo(Conclusion.QUIT_EARLY);
            assertThat(outcome.value()).isEqualTo(IntegerValue.of(101));
        }

        @Test
        @DisplayName("quit is not something TRY can catch")
        void quitIsNotCaught() {
            answerTo("secure [file quit]");
            interpreter.defineFreshWordsIn("try [read %in.txt] print {after}");

            ScriptOutcome outcome = interpreter.run("try [read %in.txt] print {after}");

            assertThat(outcome.conclusion()).isEqualTo(Conclusion.QUIT_EARLY);
        }

        @Test
        @DisplayName("read and write are judged apart: allow read, throw write")
        void readAndWriteApart() {
            answerTo("secure [file [allow read throw write]]");

            assertThat(answerTo("to string! read %in.txt")).isEqualTo("\"inside\"");
            assertThat(refusalOf("write %in.txt {x}")).isEqualTo("[security %/app/in.txt]");
            assertThat(answerTo("to string! read %in.txt")).isEqualTo("\"inside\"");
        }
    }

    @Nested
    @DisplayName("ask puts the question to whoever is at the terminal")
    class AnsweringTheQuestion {

        private final StringBuilder printed = new StringBuilder();

        private Interpreter askingThrough(String keys) {
            Bounds everything = Bounds.standard();
            for (HostService service : HostService.values()) {
                everything = everything.granting(service);
            }
            Interpreter asking = withTheHostsAdapters(Interpreter.writingTo(printed::append, everything));
            asking.useConsole(new KeysTypedAtATerminal(keys));
            answerTo(asking, "cd %/app/ secure [file ask]");
            return asking;
        }

        private String refusalThrough(Interpreter asking, String source) {
            return answerTo(asking, """
                    e: try [%s]
                    either error? e [reduce [e/id e/arg1]] ['no-error]""".formatted(source));
        }

        @Test
        @DisplayName("yes lets it through, and says what was asked")
        void yes() {
            Interpreter asking = askingThrough("y");

            assertThat(answerTo(asking, "to string! read %in.txt")).isEqualTo("\"inside\"");
            assertThat(printed.toString()).contains("Security Request").contains("READ file");
        }

        @Test
        @DisplayName("yes is remembered as a policy, so the same file is not asked about again")
        void yesIsRemembered() {
            Interpreter asking = askingThrough("y");
            answerTo(asking, "read %in.txt");

            assertThat(answerTo(asking, "to string! read %in.txt")).isEqualTo("\"inside\"");
        }

        @Test
        @DisplayName("once lets it through this time only")
        void once() {
            Interpreter asking = askingThrough("on");

            assertThat(answerTo(asking, "to string! read %in.txt")).isEqualTo("\"inside\"");
            assertThat(refusalThrough(asking, "read %in.txt")).isEqualTo("[security %/app/in.txt]");
        }

        @Test
        @DisplayName("no refuses it")
        void no() {
            assertThat(refusalThrough(askingThrough("n"), "read %in.txt")).isEqualTo("[security %/app/in.txt]");
        }

        @Test
        @DisplayName("enter on its own is no")
        void enterIsNo() {
            assertThat(refusalThrough(askingThrough("\r"), "read %in.txt")).isEqualTo("[security %/app/in.txt]");
        }

        @Test
        @DisplayName("a key that is not an answer is passed over until one is")
        void anotherKeyIsPassedOver() {
            Interpreter asking = askingThrough("xy");

            assertThat(answerTo(asking, "to string! read %in.txt")).isEqualTo("\"inside\"");
        }

        @Test
        @DisplayName("quit ends the run with 101")
        void quit() {
            Interpreter asking = askingThrough("q");
            asking.defineFreshWordsIn("read %in.txt");

            ScriptOutcome outcome = asking.run("read %in.txt");

            assertThat(outcome.conclusion()).isEqualTo(Conclusion.QUIT_EARLY);
            assertThat(outcome.value()).isEqualTo(IntegerValue.of(101));
        }
    }

    private record KeysTypedAtATerminal(StringBuilder waiting) implements ConsolePort {

        KeysTypedAtATerminal(String keys) {
            this(new StringBuilder(keys));
        }

        @Override
        public String readLine() {
            return "";
        }

        @Override
        public String readHiddenLine() {
            return "";
        }

        @Override
        public int readKey() {
            if (waiting.isEmpty()) {
                return -1;
            }
            char next = waiting.charAt(0);
            waiting.deleteCharAt(0);
            return next;
        }

        @Override
        public boolean isATerminal() {
            return true;
        }
    }

    @Nested
    @DisplayName("a folder allowed inside a class that throws")
    class AFolderAllowed {

        @BeforeEach
        void allowOnlyTheApp() {
            onlyTheAppIsAllowed();
        }

        @Test
        @DisplayName("a file in the folder is read")
        void insideIsRead() {
            assertThat(answerTo("to string! read %in.txt")).isEqualTo("\"inside\"");
        }

        @Test
        @DisplayName("a file one folder up is refused, named as an absolute path")
        void oneUpIsRefused() {
            assertThat(refusalOf("read %../out.txt")).isEqualTo("[security %/out.txt]");
        }

        @Test
        @DisplayName("a write outside is refused and nothing is written")
        void aWriteOutsideWritesNothing() {
            assertThat(refusalOf("write %../written.txt {x}")).isEqualTo("[security %/written.txt]");
            assertThat(root.resolve("written.txt")).doesNotExist();
        }

        @Test
        @DisplayName("a write inside makes the file")
        void aWriteInside() {
            answerTo("write %new.txt {made}");

            assertThat(root.resolve("app").resolve("new.txt")).hasContent("made");
        }

        @Test
        @DisplayName("the folder itself, read as a directory, is inside it")
        void theFolderItself() {
            assertThat(answerTo("read %.")).isEqualTo("[%in.txt]");
        }

        @Test
        @DisplayName("a name that only begins like the folder is not in it")
        void aNameThatOnlyBeginsLikeIt() throws IOException {
            Files.writeString(root.resolve("apple.txt"), "no");

            assertThat(refusalOf("read %/apple.txt")).isEqualTo("[security %/apple.txt]");
        }
    }

    @Nested
    @DisplayName("which file actions are checked, as the C checks them")
    class TheFileActions {

        @BeforeEach
        void allowOnlyTheApp() {
            onlyTheAppIsAllowed();
        }

        @Test
        @DisplayName("READ/LINES and READ/PART are reads")
        void readRefinements() {
            assertThat(refusalOf("read/lines %../out.txt")).isEqualTo("[security %/out.txt]");
            assertThat(refusalOf("read/part %../out.txt 2")).isEqualTo("[security %/out.txt]");
        }

        @Test
        @DisplayName("WRITE/APPEND is a write")
        void writeAppend() {
            assertThat(refusalOf("write/append %../out.txt {more}")).isEqualTo("[security %/out.txt]");
            assertThat(root.resolve("out.txt")).hasContent("outside");
        }

        @Test
        @DisplayName("CREATE of a file is a write")
        void createAFile() {
            assertThat(refusalOf("create %../created.txt")).isEqualTo("[security %/created.txt]");
            assertThat(root.resolve("created.txt")).doesNotExist();
        }

        @Test
        @DisplayName("OPEN, OPEN/READ, OPEN/WRITE and OPEN/NEW are each refused")
        void everyOpen() {
            assertThat(refusalOf("open %../out.txt")).isEqualTo("[security %/out.txt]");
            assertThat(refusalOf("open/read %../out.txt")).isEqualTo("[security %/out.txt]");
            assertThat(refusalOf("open/write %../out.txt")).isEqualTo("[security %/out.txt]");
            assertThat(refusalOf("open/new/write %../fresh.txt")).isEqualTo("[security %/fresh.txt]");
            assertThat(root.resolve("fresh.txt")).doesNotExist();
        }

        @Test
        @DisplayName("LOAD and DO read through the same check")
        void loadAndDo() {
            assertThat(refusalOf("load %../out.txt")).isEqualTo("[security %/out.txt]");
            assertThat(refusalOf("do %../out.txt")).isEqualTo("[security %/out.txt]");
        }

        @Test
        @DisplayName("a directory is refused for reading, opening and asking about it")
        void aDirectoryRead() {
            assertThat(refusalOf("read %../outdir/")).isEqualTo("[security %/outdir/]");
            assertThat(refusalOf("open %../outdir/")).isEqualTo("[security %/outdir/]");
            assertThat(refusalOf("query %../outdir/ 'type")).isEqualTo("[security %/outdir/]");
        }

        @Test
        @DisplayName("a directory named without its slash is still a directory")
        void aDirectoryWithoutItsSlash() {
            assertThat(refusalOf("read %../outdir")).isEqualTo("[security %/outdir/]");
        }

        @Test
        @DisplayName("EXISTS? of a directory outside answers none, because it queries")
        void existsOfADirectoryOutside() {
            assertThat(answerTo("exists? %../outdir/")).isEqualTo("_");
        }

        @Test
        @DisplayName("making, deleting and renaming a directory are writes")
        void aDirectoryChanged() {
            assertThat(refusalOf("make-dir %../made/")).isEqualTo("[security %/made/]");
            assertThat(refusalOf("delete %../outdir/")).isEqualTo("[security %/outdir/]");
            assertThat(refusalOf("rename %../outdir/ %../moved/")).isEqualTo("[security %/outdir/]");
            assertThat(root.resolve("made")).doesNotExist();
            assertThat(root.resolve("outdir")).isDirectory();
        }

        @Test
        @DisplayName("DELETE of a file outside is not checked, as in R3")
        void deleteOfAFileIsNotChecked() {
            answerTo("delete %../out.txt");

            assertThat(root.resolve("out.txt")).doesNotExist();
        }

        @Test
        @DisplayName("RENAME of a file is not checked, even moving it out of the folder, as in R3")
        void renameOfAFileIsNotChecked() {
            answerTo("rename %in.txt %../moved.txt");

            assertThat(root.resolve("moved.txt")).hasContent("inside");
        }

        @Test
        @DisplayName("asking about a file outside is not checked: QUERY, SIZE?, MODIFIED? and EXISTS?")
        void askingAboutAFileIsNotChecked() {
            assertThat(answerTo("query %../out.txt 'size")).isEqualTo("7");
            assertThat(answerTo("size? %../out.txt")).isEqualTo("7");
            assertThat(answerTo("date? modified? %../out.txt")).isEqualTo("#(true)");
            assertThat(answerTo("exists? %../out.txt")).isEqualTo("file");
        }

        @Test
        @DisplayName("ECHO writes outside unchecked, as in R3")
        void echoIsNotChecked() {
            answerTo("echo %../echoed.txt print {hello} echo none");

            assertThat(root.resolve("echoed.txt")).exists();
        }

        @Test
        @DisplayName("IMAGE/SAVE writes outside unchecked, as in R3")
        void imageSaveIsNotChecked() {
            answerTo("image/save %../img.png make image! 2x2");

            assertThat(root.resolve("img.png")).exists();
        }

        @Test
        @DisplayName("a port opened before the policy tightened is read and written without a second check")
        void anOpenPortIsNotCheckedAgain() {
            answerTo("secure [file allow]");
            answerTo("p: open %../out.txt");
            answerTo("secure [file throw]");

            assertThat(answerTo("to string! read p")).isEqualTo("\"outside\"");
        }
    }

    @Nested
    @DisplayName("a file is judged by where it really leads")
    class WhereItReallyLeads {

        @BeforeEach
        void allowOnlyTheApp() {
            onlyTheAppIsAllowed();
        }

        @Test
        @DisplayName("a link in the folder pointing out of it is refused, naming where it leads")
        void aLinkPointingOut() throws IOException {
            Files.createSymbolicLink(root.resolve("app").resolve("link.txt"), root.resolve("out.txt"));

            assertThat(refusalOf("read %link.txt")).isEqualTo("[security %/out.txt]");
        }

        @Test
        @DisplayName("a link from one place in the folder to another is followed")
        void aLinkInside() throws IOException {
            Files.createDirectories(root.resolve("app").resolve("sub"));
            Files.createSymbolicLink(root.resolve("app").resolve("sub").resolve("link.txt"),
                    Path.of("..").resolve("in.txt"));

            assertThat(answerTo("to string! read %sub/link.txt")).isEqualTo("\"inside\"");
        }

        @Test
        @DisplayName("a link that dangles out of the folder cannot be written through")
        void aDanglingLinkOut() throws IOException {
            Files.createSymbolicLink(root.resolve("app").resolve("dangle.txt"),
                    root.resolve("outdir").resolve("new.txt"));

            assertThat(refusalOf("write %dangle.txt {x}")).isEqualTo("[security %/outdir/new.txt]");
            assertThat(root.resolve("outdir").resolve("new.txt")).doesNotExist();
        }

        @Test
        @DisplayName("a folder outside linked into the app is refused for what is inside it")
        void aLinkedFolder() throws IOException {
            Files.createSymbolicLink(root.resolve("app").resolve("elsewhere"), root.resolve("outdir"));

            assertThat(refusalOf("read %elsewhere/o.txt")).isEqualTo("[security %/outdir/o.txt]");
        }
    }

    @Nested
    @DisplayName("the other classes, each where the C checks it")
    class TheOtherClasses {

        @Test
        @DisplayName("opening tcp is checked against net, naming the address")
        void tcp() {
            answerTo("secure [net throw]");

            assertThat(refusalOf("open tcp://127.0.0.1:1")).isEqualTo("[security tcp://127.0.0.1:1]");
        }

        @Test
        @DisplayName("opening udp is checked against net too")
        void udp() {
            answerTo("secure [net throw]");

            assertThat(refusalOf("open udp://127.0.0.1:9")).isEqualTo("[security udp://127.0.0.1:9]");
        }

        @Test
        @DisplayName("HTTP is checked through the tcp port it opens")
        void http() {
            answerTo("secure [net throw]");

            assertThat(refusalOf("read http://127.0.0.1:1/")).isEqualTo("[security tcp://127.0.0.1:1]");
        }

        @Test
        @DisplayName("DNS is not checked")
        void dnsIsNotChecked() {
            answerTo("secure [net throw]");

            assertThat(answerTo("tuple? read dns://localhost")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("a url allowed by name is let through and the rest are not, matched without case")
        void aUrlAllowedByName() {
            answerTo("secure [net throw tcp://LOCALHOST:1 allow]");

            assertThat(refusalOf("open tcp://127.0.0.1:1")).isEqualTo("[security tcp://127.0.0.1:1]");
            assertThat(refusalOf("open tcp://localhost:1")).isEqualTo("no-error");
        }

        @Test
        @DisplayName("CALL is checked, naming the command as it was given")
        void call() {
            answerTo("secure [call throw]");

            assertThat(refusalOf("call/wait {true}")).isEqualTo("[security \"true\"]");
            assertThat(refusalOf("call/wait [{true}]")).isEqualTo("[security [\"true\"]]");
        }

        @Test
        @DisplayName("BROWSE is checked")
        void browse() {
            answerTo("secure [browse throw]");

            assertThat(refusalOf("browse http://example.com")).isEqualTo("[security http://example.com]");
        }

        @Test
        @DisplayName("GET-ENV reads and SET-ENV writes, each naming the variable as given")
        void environment() {
            answerTo("secure [envr [allow read throw write]]");

            assertThat(answerTo("string? get-env {HOME}")).isEqualTo("#(true)");
            assertThat(refusalOf("set-env {JEBOL_SECURITY} {1}")).isEqualTo("[security \"JEBOL_SECURITY\"]");
            answerTo("secure [envr throw]");
            assertThat(refusalOf("get-env {HOME}")).isEqualTo("[security \"HOME\"]");
            assertThat(refusalOf("get-env 'HOME")).isEqualTo("[security HOME]");
        }

        @Test
        @DisplayName("LIST-ENV is not checked")
        void listEnvIsNotChecked() {
            answerTo("secure [envr throw]");

            assertThat(answerTo("map? list-env")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("PROTECT is a write and UNPROTECT a read")
        void protecting() {
            answerTo("x: 1 secure [protect [allow read throw write]]");

            assertThat(refusalOf("protect 'x")).isEqualTo("[security x]");
            assertThat(answerTo("unprotect 'x")).isEqualTo("x");
            answerTo("secure [protect throw]");
            assertThat(refusalOf("unprotect 'x")).isEqualTo("[security x]");
            assertThat(answerTo("protected? 'x")).isEqualTo("#(false)");
        }

        @Test
        @DisplayName("EVOKE, STACK and TRACE are refused naming debug, having no subject")
        void debugging() {
            answerTo("secure [debug throw]");

            assertThat(refusalOf("evoke 'stack-size")).isEqualTo("[security debug]");
            assertThat(refusalOf("stack 0")).isEqualTo("[security debug]");
            assertThat(refusalOf("trace off")).isEqualTo("[security debug]");
        }

        @Test
        @DisplayName("LOAD-EXTENSION of a file is refused as a violation before it is refused as never portable")
        void loadingAnExtension() {
            answerTo("secure [extension throw]");

            assertThat(refusalOf("load-extension %x.so")).isEqualTo("[security %x.so]");
        }

        @Test
        @DisplayName("an eval limit cannot be set, as LIMIT-USAGE is gone before a script runs")
        void anEvalLimitCannotBeSet() {
            assertThat(answerTo("e: try [secure [eval 1000]] e/id")).isEqualTo("no-value");
        }
    }

    @Nested
    @DisplayName("the host's grants are the ceiling")
    class TheGrantsComeFirst {

        @Test
        @DisplayName("a service the host withheld is refused as not granted, whatever the policy says")
        void aWithheldServiceIsRefusedAsThat() {
            Interpreter withoutProcesses = withTheHostsAdapters(Interpreter.withBounds(
                    Bounds.standard().granting(HostService.FILES)));
            answerTo(withoutProcesses, "secure [call throw]");

            assertThat(answerTo(withoutProcesses, "e: try [call/wait {true}] e/id")).isEqualTo("no-service");
        }

        @Test
        @DisplayName("and allowing everything does not reach what the host held back")
        void allowDoesNotReachIt() {
            Interpreter withoutProcesses = withTheHostsAdapters(Interpreter.withBounds(
                    Bounds.standard().granting(HostService.FILES)));
            answerTo(withoutProcesses, "secure allow");

            assertThat(answerTo(withoutProcesses, "e: try [call/wait {true}] e/id")).isEqualTo("no-service");
        }
    }

    @Nested
    @DisplayName("SECURE refuses what is not a policy, as r3 does")
    class WhatIsNotAPolicy {

        @Test
        @DisplayName("a number for a file level is a security error")
        void aNumber() {
            assertThat(answerTo("e: try [secure [file 5]] reduce [e/id e/arg1 e/arg2]"))
                    .isEqualTo("[security-error file 5]");
        }

        @Test
        @DisplayName("an unknown level word is a security error")
        void anUnknownWord() {
            assertThat(answerTo("e: try [secure [file maybe]] reduce [e/id e/arg1 e/arg2]"))
                    .isEqualTo("[security-error file maybe]");
        }

        @Test
        @DisplayName("an unknown access in a block is a security error")
        void anUnknownAccess() {
            assertThat(answerTo("e: try [secure [file [allow sideways]]] reduce [e/id e/arg1 e/arg2]"))
                    .isEqualTo("[security-error file sideways]");
        }

        @Test
        @DisplayName("a string where a policy goes is refused at the door")
        void aString() {
            assertThat(answerTo("e: try [secure {x}] reduce [e/id e/arg1]"))
                    .isEqualTo("[expect-arg secure]");
        }
    }
}
