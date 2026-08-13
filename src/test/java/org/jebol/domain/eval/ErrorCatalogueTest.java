package org.jebol.domain.eval;

import static org.assertj.core.api.Assertions.assertThat;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * REBOL 3's error catalogue, and the codes that come out of it.
 *
 * <p>Specified in {@code spec/natives.allium}, taken verbatim from a real
 * R3.
 *
 * <p>A code is its category's base plus the id's position in that
 * category, so it depends on the id and not only on the family. Each
 * category also carries a {@code type} field naming itself, which is not
 * an error id: counting it would shift every code in the category by one.
 */
class ErrorCatalogueTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Test
    @DisplayName("the catalogue holds the ten categories R3 has, and any a module added")
    void theCategoriesAreThere() {
        // Eleven, not ten: prot-mysql.reb ends with `put system/catalog/errors
        // 'MySQL make object! [...]`, and a real R3 grows the same eleventh
        // category the moment that module is imported. The categories are a
        // register a module may add to, so the assertion is that the ten are
        // all there and in Rebol's order rather than that there are ten.
        // Braces, not quotes: the category list is over fifty characters
        // and MOLD switches to the braced form past that length, which is
        // Mold_String_Series' MAX_QUOTED_STR.
        assertThat(answerTo("mold words-of system/catalog/errors"))
                .startsWith("{[Throw Note Syntax Script Math Access Command "
                        + "resv700 User Internal");
        assertThat(answerTo("mold find words-of system/catalog/errors 'MySQL"))
                .as("the module's own category keeps the case it was written in")
                .isEqualTo("\"[MySQL]\"");
    }

    @Test
    @DisplayName("each category carries the code its first id gets")
    void theCategoryCodesAreRight() {
        assertThat(answerTo("mold reduce [system/catalog/errors/Throw/code "
                + "system/catalog/errors/Syntax/code system/catalog/errors/Script/code "
                + "system/catalog/errors/Math/code system/catalog/errors/Access/code]"))
                .isEqualTo("\"[0 200 300 400 500]\"");
    }

    @Test
    @DisplayName("the first id in a category gets the category's own code")
    void theFirstIdTakesTheBase() {
        assertThat(answerTo("e: try [1 / 0] e/code")).isEqualTo("400");
    }

    @Test
    @DisplayName("a later id is offset by its position")
    void aLaterIdIsOffset() {
        // THROW is the third id under Throw, whose base is 0.
        assertThat(answerTo("e: try/all [throw 5] e/code")).isEqualTo("2");
    }

    @Test
    @DisplayName("the type field is not counted as an id")
    void theTypeFieldDoesNotShiftTheCodes() {
        // Counting it would make every code in the category one too high.
        assertThat(answerTo("e: try [1 / 0] e/code = system/catalog/errors/Math/code"))
                .isEqualTo("#(true)");
    }

    @Test
    @DisplayName("a category names itself in its type field, as errors.reb writes it")
    void eachCategoryNamesItself() {
        // errors.reb line 142: `type: "math error"`. This test used to
        // expect "math", which was JEBOL's own earlier invention; the
        // catalogue now carries the declaration verbatim.
        assertThat(answerTo("system/catalog/errors/Math/type"))
                .isEqualTo("\"math error\"");
    }

    @Test
    @DisplayName("an id JEBOL raises is in the catalogue under its own category")
    void aRaisedIdIsInTheCatalogue() {
        assertThat(answerTo("true? system/catalog/errors/Script/not-defined"))
                .isEqualTo("#(true)");
    }
}
