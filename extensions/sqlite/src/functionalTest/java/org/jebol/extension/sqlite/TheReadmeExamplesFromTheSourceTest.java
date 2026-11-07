package org.jebol.extension.sqlite;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class TheReadmeExamplesFromTheSourceTest {

    private static final Pattern REBOL_BLOCK = Pattern.compile("```rebol\n(.*?)```", Pattern.DOTALL);

    private static final String ANSWER_MARK = ";== ";

    private record Example(String code, String answer) {
    }

    @TempDir
    Path directory;

    private static List<Example> readExamples() throws IOException {
        String readme = Files.readString(Path.of("README.md")).replace("\r\n", "\n");
        List<Example> examples = new ArrayList<>();
        Matcher blocks = REBOL_BLOCK.matcher(readme);
        while (blocks.find()) {
            StringBuilder code = new StringBuilder();
            for (String line : blocks.group(1).split("\n")) {
                if (line.startsWith(ANSWER_MARK)) {
                    examples.add(new Example(code.toString(), line.substring(ANSWER_MARK.length())));
                    code.setLength(0);
                } else {
                    code.append(line).append('\n');
                }
            }
            if (!code.isEmpty()) {
                examples.add(new Example(code.toString(), ""));
            }
        }
        return examples;
    }

    @Test
    @DisplayName("every example in the README answers what the README says, run in order as one script")
    void answersAsTheReadmeSays() throws IOException {
        ASqliteInterpreter sqlite = new ASqliteInterpreter(directory);
        List<Example> examples = readExamples();

        assertThat(examples).hasSizeGreaterThan(15);
        for (Example example : examples) {
            String printed = sqlite.printedBy("""
                    set/any 'answer do [%s]
                    print [{^/answer:} either unset? :answer [{unset}] [mold :answer]]""".formatted(example.code()));
            String answer = printed.substring(printed.lastIndexOf("answer: ") + "answer: ".length()).strip();
            if (!example.answer().isEmpty()) {
                assertThat(answer).as(example.code()).isEqualTo(example.answer());
            }
        }
    }
}
