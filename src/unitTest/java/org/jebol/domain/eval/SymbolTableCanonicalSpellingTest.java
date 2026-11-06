package org.jebol.domain.eval;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SymbolTableCanonicalSpellingTest {

    @Test
    @DisplayName("a word's canonical spelling is the first spelling of it the table saw, whatever case came later")
    void spellsAsFirstSeen() {
        SymbolTable symbols = new SymbolTable();
        symbols.useBootSymbols("alpha Beta");
        assertThat(symbols.canonicalSpellingFor("ALPHA")).isEqualTo("alpha");
        assertThat(symbols.canonicalSpellingFor("beta")).isEqualTo("Beta");
        assertThat(symbols.canonicalSpellingFor("Gamma")).isEqualTo("Gamma");
        assertThat(symbols.canonicalSpellingFor("GAMMA")).isEqualTo("Gamma");
        assertThat(symbols.canonicalSpellingFor("alpha")).isEqualTo("alpha");
    }

    @Test
    @DisplayName("the spelling and the index agree, so two spellings of one word share both")
    void agreesWithTheIndex() {
        SymbolTable symbols = new SymbolTable();
        int first = symbols.canonicalIndexFor("Delta");
        assertThat(symbols.canonicalIndexFor("DELTA")).isEqualTo(first);
        assertThat(symbols.canonicalSpellingFor("DELTA")).isEqualTo(symbols.canonicalSpellingFor("delta"));
    }
}
