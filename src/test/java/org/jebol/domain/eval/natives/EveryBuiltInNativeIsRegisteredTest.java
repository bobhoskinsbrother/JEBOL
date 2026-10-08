package org.jebol.domain.eval.natives;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.jebol.application.Interpreter;
import org.jebol.domain.eval.actions.AddAction;
import org.jebol.domain.value.Catalogue;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.DefaultNative;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.lang.reflect.Constructor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class EveryBuiltInNativeIsRegisteredTest {

    private static final Set<String> THE_BUILT_INS_PACKAGES =
            Set.of(LoopNative.class.getPackageName(), AddAction.class.getPackageName());

    private List<DefaultNative> everyDefinitionOnTheClasspath() {
        String classesDirs = System.getProperty("jebol.mainClassesDirs");
        List<Path> paths = Arrays.stream(classesDirs.split(File.pathSeparator))
                .map(Path::of)
                .filter(Files::isDirectory)
                .toList();
        List<DefaultNative> found = new ArrayList<>();
        for (JavaClass each : new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPaths(paths)) {
            if (!each.isAssignableTo(DefaultNative.class)
                    || !THE_BUILT_INS_PACKAGES.contains(each.getPackageName())
                    || each.isInterface()
                    || each.getModifiers().toString().contains("ABSTRACT")) {
                continue;
            }
            found.addAll(instantiated(each));
        }
        return found;
    }

    private List<DefaultNative> instantiated(JavaClass each) {
        try {
            Class<?> definition = Class.forName(each.getName());
            if (isAFamilyOfOnePerDatatype(definition)) {
                return onePerDatatype(definition);
            }
            return List.of((DefaultNative) builtFromItsSmallestConstructor(definition));
        } catch (ReflectiveOperationException unbuildable) {
            throw new IllegalStateException(
                    each.getName() + " has no constructor taking nothing, a datatype or "
                            + "collaborators that build in turn, so nothing can "
                            + "register it", unbuildable);
        }
    }

    private Object builtFromItsSmallestConstructor(Class<?> type) throws ReflectiveOperationException {
        Constructor<?> built = theConstructorToBuildWith(type);
        built.setAccessible(true);
        Object[] collaborators = new Object[built.getParameterCount()];
        for (int at = 0; at < collaborators.length; at++) {
            collaborators[at] = builtFromItsSmallestConstructor(built.getParameterTypes()[at]);
        }
        return built.newInstance(collaborators);
    }

    private boolean isAFamilyOfOnePerDatatype(Class<?> definition) {
        return Arrays.stream(definition.getDeclaredConstructors())
                .anyMatch(constructor -> Arrays.equals(
                        constructor.getParameterTypes(), new Class<?>[] {Datatype.class}));
    }

    private Constructor<?> theConstructorToBuildWith(Class<?> definition)
            throws NoSuchMethodException {
        return Arrays.stream(definition.getDeclaredConstructors())
                .min(Comparator.comparingInt(Constructor::getParameterCount))
                .orElseThrow(NoSuchMethodException::new);
    }

    private List<DefaultNative> onePerDatatype(Class<?> definition)
            throws ReflectiveOperationException {
        List<DefaultNative> family = new ArrayList<>();
        for (Datatype datatype : Catalogue.DATATYPES.entries()) {
            family.add((DefaultNative) definition.getDeclaredConstructor(Datatype.class)
                    .newInstance(datatype));
        }
        return family;
    }

    private String whatTheInterpreterKnows(List<String> names) {
        String asked = "collect [foreach name [" + String.join(" ", names)
                + "] [unless any [value? name find system/catalog/natives name] [keep mold name]]]";
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(asked);
        return interpreter.display(interpreter.run(asked));
    }

    @Test
    @DisplayName("a definition nobody registers is a native nobody can call")
    void everyDefinitionIsReachableFromAScript() {
        List<DefaultNative> defined = everyDefinitionOnTheClasspath();

        assertThat(defined)
                .as("no DefaultNative was found at all, so this test is "
                        + "asserting nothing; check jebol.mainClassesDirs")
                .isNotEmpty();

        List<String> names = defined.stream().map(DefaultNative::nativeName).toList();

        assertThat(whatTheInterpreterKnows(names))
                .as("these are written as a DefaultNative but no call to "
                        + "register mentions them, so a script cannot "
                        + "reach them and nothing else would say so")
                .isEqualTo("[]");
    }

    @Test
    @DisplayName("each one declares the name it registers under exactly once")
    void noTwoDefinitionsClaimTheSameName() {
        List<String> names = everyDefinitionOnTheClasspath().stream()
                .map(DefaultNative::nativeName)
                .toList();

        assertThat(names)
                .as("two definitions under one name means whichever registers "
                        + "last silently wins")
                .doesNotHaveDuplicates();
    }
}
