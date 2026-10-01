package org.jebol.domain.eval.definition;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EveryNativeDefinitionIsRegisteredTest {

    private static List<NativeDefinition> everyDefinitionOnTheClasspath() {
        String classesDirs = System.getProperty("jebol.mainClassesDirs");
        List<Path> paths = Arrays.stream(classesDirs.split(File.pathSeparator))
                .map(Path::of)
                .filter(Files::isDirectory)
                .toList();
        List<NativeDefinition> found = new ArrayList<>();
        for (JavaClass each : new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPaths(paths)) {
            if (!each.isAssignableTo(NativeDefinition.class)
                    || each.isInterface()
                    || each.getModifiers().toString().contains("ABSTRACT")) {
                continue;
            }
            found.add(instantiate(each));
        }
        return found;
    }

    private static NativeDefinition instantiate(JavaClass each) {
        try {
            return (NativeDefinition) Class.forName(each.getName())
                    .getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException unbuildable) {
            throw new IllegalStateException(
                    each.getName() + " has no no-argument constructor, so nothing "
                            + "can register it", unbuildable);
        }
    }

    private static String whatTheInterpreterKnows(List<String> names) {
        String asked = "collect [foreach name [" + String.join(" ", names)
                + "] [unless value? name [keep mold name]]]";
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(asked);
        return interpreter.display(interpreter.run(asked));
    }

    @Test
    @DisplayName("a definition nobody registers is a native nobody can call")
    void everyDefinitionIsReachableFromAScript() {
        List<NativeDefinition> defined = everyDefinitionOnTheClasspath();

        assertThat(defined)
                .as("no NativeDefinition was found at all, so this test is "
                        + "asserting nothing; check jebol.mainClassesDirs")
                .isNotEmpty();

        List<String> names = defined.stream().map(NativeDefinition::name).toList();

        assertThat(whatTheInterpreterKnows(names))
                .as("these are written as a NativeDefinition but no call to "
                        + "register mentions them, so a script cannot "
                        + "reach them and nothing else would say so")
                .isEqualTo("[]");
    }

    @Test
    @DisplayName("each one declares the name it registers under exactly once")
    void noTwoDefinitionsClaimTheSameName() {
        List<String> names = everyDefinitionOnTheClasspath().stream()
                .map(NativeDefinition::name)
                .toList();

        assertThat(names)
                .as("two definitions under one name means whichever registers "
                        + "last silently wins")
                .doesNotHaveDuplicates();
    }
}
