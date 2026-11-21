package org.jebol.devtools;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public abstract class PackageMac extends DesktopPackage {

    private static final int LONGEST_MAC_PACKAGE_NAME = 16;

    @Override
    protected Platform platform() {
        return Platform.MACOS;
    }

    @Override
    protected String launcherName(AppDeclaration declaration) {
        return declaration.title();
    }

    @Override
    protected Placeholders placeholdersFor(AppDeclaration declaration) {
        return Placeholders.of(declaration)
                .withWritten("launcher", xml(declaration.title()))
                .withWritten("macUsageStrings", usageStrings(declaration.uses()));
    }

    static String usageStrings(Map<Service, String> uses) {
        return uses.entrySet().stream()
                .filter(use -> use.getKey().macUsageKey().isPresent())
                .map(use -> "  <key>" + use.getKey().macUsageKey().get() + "</key>\n  <string>" + xml(use.getValue())
                        + "</string>")
                .collect(Collectors.joining("\n"));
    }

    private static String xml(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    @Override
    protected void writeIcons(AppDeclaration declaration, Path resources) {
        IcnsFile.write(IconImage.read(declaration.icon()), resources.resolve(declaration.title() + ".icns"));
    }

    @Override
    protected List<List<String>> platformArgumentSets(AppDeclaration declaration, Path resources) {
        String packageName = declaration.title().length() > LONGEST_MAC_PACKAGE_NAME
                ? declaration.title().substring(0, LONGEST_MAC_PACKAGE_NAME)
                : declaration.title();
        return List.of(List.of(
                "--type", "app-image",
                "--icon", resources.resolve(declaration.title() + ".icns").toString(),
                "--mac-package-identifier", declaration.identity(),
                "--mac-package-name", packageName,
                "--mac-app-category", declaration.category().macCategory()));
    }

    @Override
    protected void finishPackages(AppDeclaration declaration, Path packages) {
        Path app = packages.resolve(declaration.title() + ".app");
        if (Files.isDirectory(app)) {
            run("codesign", List.of("--force", "--deep", "--sign", "-", app.toString()));
        }
    }
}
