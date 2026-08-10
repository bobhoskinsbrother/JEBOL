package org.jebol;
import org.jebol.application.Interpreter;
import org.junit.jupiter.api.Test;
class Probe {
    @Test
    void probe() {
        String[] cases = {
            "b: [a b] parse b [some [word! insert 9]] mold b",
            "b: [a b] parse b [some [change word! 9]] mold b",
            "b: [a b] parse b [some [word! insert (9)]] mold b",
            "b: [a b] parse b [some [pos: word! insert 9]] mold b",
            "v: 7 b: [a b] parse b [some [word! insert v]] mold b",
            "b: [a b] parse b [some [change word! 'x]] mold b",
        };
        for (String s : cases) {
            Interpreter i = Interpreter.create();
            i.defineFreshWordsIn(s);
            String a;
            try { a = i.display(i.run(s)); } catch (RuntimeException f) { a = "THREW " + f.getMessage(); }
            System.out.printf("  %-48s ==> %s%n", s.substring(0, Math.min(46, s.length())), a);
        }
    }
}
