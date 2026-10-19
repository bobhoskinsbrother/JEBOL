package org.jebol.editors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.w3c.dom.Document;

import static org.assertj.core.api.Assertions.assertThat;

class TheRebolGrammarFromTheSourceTest {

    private static final Path THE_BUNDLE = Path.of("editors", "Rebol.tmbundle");

    private static final Path THE_GRAMMAR = THE_BUNDLE.resolve("Syntaxes").resolve("Rebol.tmLanguage");

    private static final Path THE_BUNDLE_INFO = THE_BUNDLE.resolve("info.plist");

    private static final Map<String, String> THE_XML_ENTITIES = Map.of(
            "&amp;", "&",
            "&lt;", "<",
            "&gt;", ">",
            "&quot;", "\"",
            "&apos;", "'");

    private static String readTheGrammar() throws Exception {
        return Files.readString(THE_GRAMMAR, StandardCharsets.UTF_8);
    }

    private static List<String> listEveryRegexIn(String grammar) {
        List<String> found = new ArrayList<>();
        var pattern = java.util.regex.Pattern.compile(
                "<key>(match|begin|end)</key>\\s*<string>(.*?)</string>",
                java.util.regex.Pattern.DOTALL);
        var matcher = pattern.matcher(grammar);
        while (matcher.find()) {
            found.add(readEntitiesBack(matcher.group(2)));
        }
        return found;
    }

    private static String readEntitiesBack(String escaped) {
        String read = escaped;
        for (var entity : THE_XML_ENTITIES.entrySet()) {
            read = read.replace(entity.getKey(), entity.getValue());
        }
        return read;
    }

    private static List<String> listEveryScopeIn(String grammar) {
        List<String> found = new ArrayList<>();
        var matcher = java.util.regex.Pattern
                .compile("<key>name</key>\\s*<string>(.*?)</string>")
                .matcher(grammar);
        while (matcher.find()) {
            found.add(matcher.group(1));
        }
        return found;
    }

    @Test
    @DisplayName("both plists are well-formed, so an editor can read them at all")
    void parsesBothPlists() throws Exception {
        DocumentBuilderFactory parsing = DocumentBuilderFactory.newInstance();
        Document info = parsing.newDocumentBuilder().parse(THE_BUNDLE_INFO.toFile());
        Document grammar = parsing.newDocumentBuilder().parse(THE_GRAMMAR.toFile());

        assertThat(info.getDocumentElement().getNodeName()).isEqualTo("plist");
        assertThat(grammar.getDocumentElement().getNodeName()).isEqualTo("plist");
    }

    @Test
    @DisplayName("the grammar names itself, the files it claims and a first line")
    void declaresItself() throws Exception {
        String grammar = readTheGrammar();

        assertThat(grammar).contains("<string>source.rebol</string>");
        assertThat(grammar).contains("<string>reb</string>")
                .contains("<string>r3</string>")
                .contains("<string>rebol</string>");
        assertThat(grammar).contains("<string>^[Rr][Ee][Bb][Oo][Ll]\\s*\\[</string>");
    }

    @Test
    @DisplayName("every regex compiles the way IntelliJ will compile it")
    void compilesEveryRegex() throws Exception {
        List<String> every = listEveryRegexIn(readTheGrammar());

        assertThat(every)
                .as("a grammar with no patterns in it would pass anything")
                .hasSizeGreaterThan(30);
        for (String regex : every) {
            assertThat(java.util.regex.Pattern.compile(regex))
                    .as("the pattern %s does not compile", regex)
                    .isNotNull();
        }
    }

    @Test
    @DisplayName("and carries the scopes an editor colours: comments, strings, words, numbers")
    void holdsTheScopes() throws Exception {
        assertThat(listEveryScopeIn(readTheGrammar()))
                .contains(
                        "comment.line.semicolon.rebol",
                        "string.quoted.double.rebol",
                        "string.quoted.brace.rebol",
                        "constant.character.rebol",
                        "string.quoted.binary.rebol",
                        "constant.numeric.integer.rebol",
                        "constant.numeric.decimal.rebol",
                        "constant.numeric.pair.rebol",
                        "constant.other.date.rebol",
                        "constant.numeric.time.rebol",
                        "constant.numeric.money.rebol",
                        "constant.numeric.percent.rebol",
                        "constant.numeric.tuple.rebol",
                        "variable.other.rebol",
                        "variable.other.set.rebol",
                        "variable.other.get.rebol",
                        "variable.other.lit.rebol",
                        "variable.other.path.rebol",
                        "variable.other.refinement.rebol",
                        "keyword.control.rebol",
                        "keyword.operator.rebol",
                        "support.type.rebol",
                        "string.other.file.rebol",
                        "markup.underline.link.rebol");
    }
}
