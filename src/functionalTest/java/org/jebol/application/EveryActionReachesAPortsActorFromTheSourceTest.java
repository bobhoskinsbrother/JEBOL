package org.jebol.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EveryActionReachesAPortsActorFromTheSourceTest {

    private static final String A_SCHEME_WRITTEN_IN_REBOL = """
            heard: 'nothing

            sys/make-scheme [
                name: 'probe
                title: "Probe"
                actor: [
                    insert: func [port value /part range /only /dup count][
                        heard: reduce ['insert value part range only dup count] port]
                    append: func [port value /part range /only /dup count][
                        heard: reduce ['append value part range only dup count] port]
                    change: func [port value /part range /only /dup count][
                        heard: reduce ['change value part range] port]
                    find: func [port value /part range /only /case /same /any
                            /with wild /skip size /last /reverse /tail /match][
                        heard: reduce ['find value part range tail match] port]
                    select: func [port value /part range /only /case /same /any
                            /with wild /skip size /last /reverse][
                        heard: reduce ['select value part range] port]
                    clear: func [port][heard: reduce ['clear] port]
                    copy: func [port /part range /deep /types kinds][
                        heard: reduce ['copy part range deep types kinds] port]
                    read: func [port /part length /seek index /string /binary /lines /all][
                        heard: reduce ['read part length seek index string binary lines all] port]
                    write: func [port data /part length /seek index /append
                            /allow access /lines /binary /all][
                        heard: reduce ['write data part length seek index append] port]
                    open: func [port /new /read /write /seek /allow access][
                        heard: reduce ['open new read write seek allow access] port]
                    close: func [port][heard: reduce ['close] port]
                    open?: func [port][heard: reduce ['open?] true]
                    query: func [port field /mode][heard: reduce ['query field mode] port]
                    modify: func [port field value][heard: reduce ['modify field value] port]
                    update: func [port][heard: reduce ['update] port]
                    create: func [port][heard: reduce ['create] port]
                    delete: func [port][heard: reduce ['delete] port]
                    rename: func [from to][heard: reduce ['rename to] from]
                    flush: func [port][heard: reduce ['flush] port]
                    length?: func [port][heard: reduce ['length?] 0]
                    pick: func [port index][heard: reduce ['pick index] port]
                    poke: func [port index value][heard: reduce ['poke index value] port]
                    put: func [port key value /case /skip size][
                        heard: reduce ['put key value] port]
                    remove: func [port /part range /key key-arg][
                        heard: reduce ['remove part range key key-arg] port]
                    head: func [port][heard: reduce ['head] port]
                    tail: func [port][heard: reduce ['tail] port]
                    head?: func [port][heard: reduce ['head?] true]
                    tail?: func [port][heard: reduce ['tail?] false]
                    past?: func [port][heard: reduce ['past?] false]
                    next: func [port][heard: reduce ['next] port]
                    back: func [port][heard: reduce ['back] port]
                    skip: func [port offset][heard: reduce ['skip offset] port]
                    at: func [port index][heard: reduce ['at index] port]
                    atz: func [port index][heard: reduce ['atz index] port]
                    index?: func [port /xy][heard: reduce ['index? xy] 1]
                    indexz?: func [port /xy][heard: reduce ['indexz? xy] 0]
                    reflect: func [port field][heard: reduce ['reflect field] port]
                ]
            ]

            p: open probe://
            whatTheActorHeard: func [code][heard: 'nothing do code heard]
            idOf: func [code][
                set/any 'answer try code
                either error? :answer [answer/id] ['no-error]
            ]
            """;

    private static String answerTo(String source) {
        Interpreter interpreter = AnInterpreter.create();
        String whole = A_SCHEME_WRITTEN_IN_REBOL + source;
        interpreter.defineNewWordsIn(whole);
        return interpreter.display(interpreter.run(whole));
    }

    @Test
    @DisplayName("opening a scheme written in REBOL calls the actor's own OPEN")
    void callsTheActorsOpen() {
        assertThat(answerTo("heard")).isEqualTo("[open _ _ _ _ _ _]");
    }

    @Test
    @DisplayName("the verbs JEBOL once wired to a series instead all reach the actor")
    void reachesTheActorForTheVerbsThatUsedToRefuse() {
        assertThat(answerTo("""
                reduce [
                    first whatTheActorHeard [insert p 1]
                    first whatTheActorHeard [append p 1]
                    first whatTheActorHeard [change p 1]
                    first whatTheActorHeard [clear p]
                    first whatTheActorHeard [find p 1]
                    first whatTheActorHeard [flush p]
                ]""")).isEqualTo("[insert append change clear find flush]");
    }

    @Test
    @DisplayName("and so do the navigation verbs, which answered cannot-use or expect-arg")
    void reachesTheActorForNavigation() {
        assertThat(answerTo("""
                reduce [
                    first whatTheActorHeard [head p]
                    first whatTheActorHeard [tail p]
                    first whatTheActorHeard [head? p]
                    first whatTheActorHeard [tail? p]
                    first whatTheActorHeard [past? p]
                    first whatTheActorHeard [next p]
                    first whatTheActorHeard [back p]
                    first whatTheActorHeard [skip p 2]
                    first whatTheActorHeard [at p 2]
                    first whatTheActorHeard [atz p 2]
                    first whatTheActorHeard [index? p]
                    first whatTheActorHeard [indexz? p]
                ]""")).isEqualTo(
                "[head tail head? tail? past? next back skip at atz index? indexz?]");
    }

    @Test
    @DisplayName("every other action the actor declares reaches it too")
    void reachesTheActorForTheRest() {
        assertThat(answerTo("""
                reduce [
                    first whatTheActorHeard [read p]
                    first whatTheActorHeard [write p {x}]
                    first whatTheActorHeard [copy p]
                    first whatTheActorHeard [close p]
                    first whatTheActorHeard [open? p]
                    first whatTheActorHeard [query p none]
                    first whatTheActorHeard [modify p 'a 1]
                    first whatTheActorHeard [update p]
                    first whatTheActorHeard [create p]
                    first whatTheActorHeard [delete p]
                    first whatTheActorHeard [rename p %x]
                    first whatTheActorHeard [length? p]
                    first whatTheActorHeard [pick p 2]
                    first whatTheActorHeard [poke p 1 2]
                    first whatTheActorHeard [put p 1 2]
                    first whatTheActorHeard [select p 1]
                    first whatTheActorHeard [remove p]
                ]""")).isEqualTo("[read write copy close open? query modify update create "
                + "delete rename length? pick poke put select remove]");
    }

    @Test
    @DisplayName("an action the actor does not declare is no-port-action, naming the verb")
    void refusesAnUndeclaredActionByName() {
        assertThat(answerTo("""
                set/any 'e try [take p]
                reduce [e/id e/arg1]""")).isEqualTo("[no-port-action take:]");
    }

    @Test
    @DisplayName("and the actor is not called at all for one it does not declare")
    void leavesTheActorUncalledForAnUndeclaredAction() {
        assertThat(answerTo("whatTheActorHeard [try [take p]]")).isEqualTo("nothing");
    }

    @Test
    @DisplayName("MAKE, TO and REFLECT do not reach the actor, even one declaring REFLECT")
    void keepsMakeToAndReflectAwayFromTheActor() {
        assertThat(answerTo("""
                reduce [
                    whatTheActorHeard [try [make p []]]
                    whatTheActorHeard [try [to p 1]]
                    whatTheActorHeard [try [reflect p 'spec]]
                ]""")).isEqualTo("[nothing nothing nothing]");
    }

    @Test
    @DisplayName("nor does a datatype test, which looks at the value rather than sending to it")
    void keepsADatatypeTestAwayFromTheActor() {
        assertThat(answerTo("""
                reduce [
                    whatTheActorHeard [port? p]
                    whatTheActorHeard [series? p]
                    port? p
                ]""")).isEqualTo("[nothing nothing #(true)]");
    }

    @Test
    @DisplayName("COPY/PART reaches the actor with PART true and the length it was given")
    void handsOverThePartLengthOfACopy() {
        assertThat(answerTo("whatTheActorHeard [copy/part p 10]"))
                .isEqualTo("[copy #(true) 10 _ _ _]");
    }

    @Test
    @DisplayName("and COPY's other refinements arrive beside it")
    void handsOverEveryRefinementOfACopy() {
        assertThat(answerTo("""
                reduce [
                    whatTheActorHeard [copy/deep p]
                    whatTheActorHeard [copy/part/deep p 4]
                ]""")).isEqualTo("[[copy _ _ #(true) _ _] [copy #(true) 4 #(true) _ _]]");
    }

    @Test
    @DisplayName("OPEN's refinements reach the actor, where only the port used to")
    void handsOverEveryRefinementOfAnOpen() {
        assertThat(answerTo("whatTheActorHeard [open/new/read probe://]"))
                .isEqualTo("[open #(true) #(true) _ _ _ _]");
    }

    @Test
    @DisplayName("WRITE's refinements reach the actor beside the data")
    void handsOverEveryRefinementOfAWrite() {
        assertThat(answerTo("""
                reduce [
                    whatTheActorHeard [write/append p {x}]
                    whatTheActorHeard [write/part p {x} 2]
                ]""")).isEqualTo("""
                [[write "x" _ _ _ _ #(true)] [write "x" #(true) 2 _ _ _]]""");
    }

    @Test
    @DisplayName("INSERT, APPEND, FIND and REMOVE hand over their refinement arguments")
    void handsOverTheRefinementArgumentsOfTheSeriesVerbs() {
        assertThat(answerTo("""
                reduce [
                    whatTheActorHeard [insert/part p 1 2]
                    whatTheActorHeard [insert/dup p 1 3]
                    whatTheActorHeard [append/only p [1]]
                    whatTheActorHeard [find/part p 1 7]
                    whatTheActorHeard [find/match/tail p 1]
                    whatTheActorHeard [remove/part p 2]
                ]""")).isEqualTo("[[insert 1 #(true) 2 _ _ _] [insert 1 _ _ _ #(true) 3] "
                + "[append [1] _ _ #(true) _ _] [find 1 #(true) 7 _ _] "
                + "[find 1 _ _ #(true) #(true)] [remove #(true) 2 _ _]]");
    }

    @Test
    @DisplayName("READ's two value-taking refinements arrive with the right value each")
    void handsOverBothOfReadsRefinementArguments() {
        assertThat(answerTo("whatTheActorHeard [read/seek/part p 5 10]"))
                .isEqualTo("[read #(true) 10 #(true) 5 _ _ _ _]");
    }

    @Test
    @DisplayName("a refinement is matched to the actor's by name, not by where it is declared")
    void matchesARefinementByNameRatherThanPosition() {
        assertThat(answerTo("""
                sys/make-scheme [
                    name: 'reordered
                    title: "Reordered"
                    actor: [
                        open: func [port][port]
                        read: func [port /binary /seek index /lines /part length][
                            heard: reduce ['read seek index part length] port]
                    ]
                ]
                q: open reordered://
                whatTheActorHeard [read/seek/part q 5 10]"""))
                .isEqualTo("[read #(true) 5 #(true) 10]");
    }

    @Test
    @DisplayName("an actor function's locals are left alone, not filled from the call")
    void leavesTheActorsLocalsAlone() {
        assertThat(answerTo("""
                sys/make-scheme [
                    name: 'withlocals
                    title: "With locals"
                    actor: [
                        open: func [port][port]
                        read: func [port /part length /local kept][
                            kept: 'untouched
                            heard: reduce [part length kept] port]
                    ]
                ]
                q: open withlocals://
                whatTheActorHeard [read/part q 3]"""))
                .isEqualTo("[#(true) 3 untouched]");
    }

    @Test
    @DisplayName("an actor taking fewer parameters than the action was given keeps the first")
    void dropsWhatAShorterActorFunctionDoesNotDeclare() {
        assertThat(answerTo("""
                sys/make-scheme [
                    name: 'shortactor
                    title: "Short actor"
                    actor: [
                        open: func [port][port]
                        read: func [port][heard: reduce ['read port? port] port]
                    ]
                ]
                q: open shortactor://
                whatTheActorHeard [read/part/binary q 3]"""))
                .isEqualTo("[read #(true)]");
    }

    @Test
    @DisplayName("an actor taking more parameters than the action gives gets none for the rest")
    void fillsWhatALongerActorFunctionDeclaresWithNone() {
        assertThat(answerTo("""
                sys/make-scheme [
                    name: 'longactor
                    title: "Long actor"
                    actor: [
                        open: func [port][port]
                        length?: func [port spare][heard: reduce ['length? spare] 0]
                    ]
                ]
                q: open longactor://
                whatTheActorHeard [length? q]"""))
                .isEqualTo("[length? _]");
    }

    @Test
    @DisplayName("the dispatch never meets an actor field that is not a function: MAKE-SCHEME "
            + "refuses one, naming the field")
    void refusesASchemeWhoseActorFieldIsNotAFunction() {
        assertThat(answerTo("""
                set/any 'e try [sys/make-scheme [name: 'held title: "Held" actor: [read: 5]]]
                reduce [
                    e/id
                    e/arg1
                    idOf [sys/make-scheme [name: 'empty title: "Empty" actor: [read: none]]]
                ]""")).isEqualTo("[invalid-arg read: invalid-arg]");
    }

    @Test
    @DisplayName("the actor's own answer is what the call answers, whatever it is")
    void answersWhateverTheActorAnswers() {
        assertThat(answerTo("""
                sys/make-scheme [
                    name: 'answering
                    title: "Answering"
                    actor: [
                        open: func [port][port]
                        copy: func [port /part range /deep /types kinds][{the body}]
                        length?: func [port][42]
                    ]
                ]
                q: open answering://
                reduce [copy q length? q]""")).isEqualTo("""
                ["the body" 42]""");
    }

    @Test
    @DisplayName("a port whose actor is none answers none to every action, not just the wired ones")
    void answersNoneFromAPortWithNoActor() {
        assertThat(answerTo("""
                sys/make-scheme [name: 'actorless title: "Actorless"]
                bare: make port! [scheme: 'actorless]
                reduce [
                    idOf [length? bare]
                    length? bare
                    head bare
                    insert bare 1
                    read bare
                ]""")).isEqualTo("[no-error _ _ _ _]");
    }

    @Test
    @DisplayName("a port whose spec is not an object is invalid-port for every action")
    void refusesAPortWhoseSpecIsNotAnObject() {
        assertThat(answerTo("""
                p/spec: 5
                reduce [
                    idOf [length? p]
                    idOf [head p]
                    idOf [insert p 1]
                    idOf [read p]
                ]""")).isEqualTo("[invalid-port invalid-port invalid-port invalid-port]");
    }

    @Test
    @DisplayName("an action whose first argument cannot be a port is still refused by its spec")
    void keepsRefusingAPortWhereTheActionDeclaresNone() {
        assertThat(answerTo("""
                reduce [
                    idOf [sort p]
                    idOf [reverse p]
                    idOf [swap p p]
                    idOf [trim p]
                ]""")).isEqualTo("[expect-arg expect-arg expect-arg expect-arg]");
    }

    @Test
    @DisplayName("a url naming the scheme reaches the actor for every verb that takes one")
    void reachesTheActorThroughAUrl() {
        assertThat(answerTo("""
                reduce [
                    first whatTheActorHeard [open probe://x]
                    first whatTheActorHeard [read probe://x]
                    first whatTheActorHeard [write probe://x {d}]
                    first whatTheActorHeard [query probe://x none]
                    first whatTheActorHeard [create probe://x]
                    first whatTheActorHeard [delete probe://x]
                    first whatTheActorHeard [rename probe://x %y]
                ]""")).isEqualTo("[open read write query create delete rename]");
    }

    @Test
    @DisplayName("QUERY through a url reaches the actor's own QUERY, not the scheme's INFO names")
    void reachesTheActorsQueryThroughAUrl() {
        assertThat(answerTo("""
                reduce [
                    whatTheActorHeard [query probe://x none]
                    whatTheActorHeard [query probe://x 'size]
                ]""")).isEqualTo("[[query _ _] [query size _]]");
    }

    @Test
    @DisplayName("a block of a specification reaches the actor the same way a url does")
    void reachesTheActorThroughASpecificationBlock() {
        assertThat(answerTo("""
                reduce [
                    first whatTheActorHeard [open [scheme: 'probe]]
                    first whatTheActorHeard [read [scheme: 'probe]]
                    first whatTheActorHeard [query [scheme: 'probe] none]
                ]""")).isEqualTo("[open read query]");
    }

    @Test
    @DisplayName("a refinement given through a url reaches the actor with its argument")
    void handsOverARefinementGivenThroughAUrl() {
        assertThat(answerTo("whatTheActorHeard [read/part probe://x 4]"))
                .isEqualTo("[read #(true) 4 _ _ _ _ _ _]");
    }
}
