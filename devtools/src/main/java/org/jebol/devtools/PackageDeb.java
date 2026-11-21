package org.jebol.devtools;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.List;

public abstract class PackageDeb extends DesktopPackage {

    public static final int ICON_SIZE = 256;
    private static final String DESKTOP_TEMPLATE = "app.desktop";

    @Override
    protected Platform platform() {
        return Platform.LINUX;
    }

    @Override
    protected void fillProject(Placeholders placeholders, Path resources, Path input) {
        super.fillProject(placeholders, resources, input);
        AppDeclaration declaration = declaration();
        Path filledDesktopEntry = resources.resolve(DESKTOP_TEMPLATE);
        if (filledDesktopEntry.toFile().isFile()) {
            Path entry = input.resolve(declaration.identity() + ".desktop");
            if (!filledDesktopEntry.toFile().renameTo(entry.toFile())) {
                throw new IllegalStateException("could not move the desktop entry to " + entry);
            }
        }
    }

    @Override
    protected void writeIcons(AppDeclaration declaration, Path resources) {
        BufferedImage icon = IconImage.read(declaration.icon());
        IconSizes.writePng(icon, ICON_SIZE, resources.resolve(launcherName(declaration) + ".png"));
    }

    @Override
    protected List<List<String>> platformArgumentSets(AppDeclaration declaration, Path resources) {
        return List.of(joined(List.of(
                "--type", "deb",
                "--icon", resources.resolve(launcherName(declaration) + ".png").toString(),
                "--linux-package-name", Placeholders.packageName(declaration),
                "--linux-deb-maintainer", declaration.contact(),
                "--linux-app-category", "misc"), aboutUrl(declaration)));
    }
}
