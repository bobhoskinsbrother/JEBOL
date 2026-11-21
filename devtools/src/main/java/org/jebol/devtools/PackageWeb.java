package org.jebol.devtools;

import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.TaskAction;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

public abstract class PackageWeb extends AppTask {

    public static final List<Integer> ICON_SIZES = List.of(192, 512);

    @Internal
    public abstract DirectoryProperty getSite();

    @TaskAction
    public void makeSite() {
        AppDeclaration declaration = declaration();
        generate(Platform.WEB);
        Path site = getSite().get().getAsFile().toPath();
        List<String> names = AppFiles.overlay(declaration, Platform.WEB, site);
        Carried.copyFolder("runtime/web", site);
        for (String extension : declaration.requires()) {
            if (Carried.has("runtime/extensions/" + extension + "/web")) {
                Carried.copyFolder("runtime/extensions/" + extension + "/web", site);
            }
        }
        fillProject(declaration, site);
        BufferedImage icon = IconImage.read(declaration.icon());
        ICON_SIZES.forEach(size -> IconSizes.writePng(icon, size, site.resolve("icons/icon-" + size + ".png")));
        AppFiles.writeAppJson(names, declaration.entry(), site.resolve("app.json"));
        getLogger().lifecycle("made the site in " + site);
    }

    private void fillProject(AppDeclaration declaration, Path site) {
        Path project = Projects.folderFor(appDirectory(), Platform.WEB);
        Placeholders placeholders = Placeholders.of(declaration);
        try (Stream<Path> walked = Files.walk(project)) {
            for (Path each : walked.filter(Files::isRegularFile).toList()) {
                Path relative = project.relativize(each);
                if (!relative.startsWith(Projects.OVERLAY_FOLDER)) {
                    FilledFiles.fill(each, site.resolve(relative.toString()), placeholders);
                }
            }
        } catch (IOException unlisted) {
            throw new UncheckedIOException(unlisted);
        }
    }
}
