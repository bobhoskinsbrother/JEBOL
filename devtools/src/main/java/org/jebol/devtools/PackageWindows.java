package org.jebol.devtools;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

public abstract class PackageWindows extends DesktopPackage {

    @Override
    protected Platform platform() {
        return Platform.WINDOWS;
    }

    @Override
    protected String launcherName(AppDeclaration declaration) {
        return declaration.title();
    }

    static String upgradeCode(AppDeclaration declaration) {
        return UUID.nameUUIDFromBytes(("jebol:" + declaration.identity()).getBytes(StandardCharsets.UTF_8)).toString();
    }

    @Override
    protected void writeIcons(AppDeclaration declaration, Path resources) {
        IcoFile.write(IconImage.read(declaration.icon()), resources.resolve(declaration.title() + ".ico"));
    }

    @Override
    protected List<List<String>> platformArgumentSets(AppDeclaration declaration, Path resources) {
        String icon = resources.resolve(declaration.title() + ".ico").toString();
        List<String> installer = joined(List.of(
                "--type", "exe",
                "--icon", icon,
                "--win-shortcut",
                "--win-per-user-install",
                "--win-upgrade-uuid", upgradeCode(declaration)), aboutUrl(declaration));
        List<String> portable = List.of("--type", "app-image", "--icon", icon);
        return List.of(installer, portable);
    }
}
