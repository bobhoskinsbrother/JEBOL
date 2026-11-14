import java.io.IOException;
import java.io.InputStream;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.constantpool.ClassEntry;
import java.lang.classfile.constantpool.FieldRefEntry;
import java.lang.classfile.constantpool.MemberRefEntry;
import java.lang.classfile.constantpool.PoolEntry;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class AndroidApiGaps {

    private static final String FAIL_ON_GAPS = "--fail-on-gaps";
    private static final String REPORT = "--report";

    private static final Set<String> REWRITTEN_BY_D8 = Set.of(
            "java/lang/invoke/LambdaMetafactory",
            "java/lang/invoke/StringConcatFactory",
            "java/lang/invoke/ConstantBootstraps",
            "java/lang/runtime/SwitchBootstraps",
            "java/lang/runtime/ObjectMethods",
            "java/lang/MatchException");

    private static final Set<String> SIGNATURE_POLYMORPHIC_OWNERS = Set.of(
            "java/lang/invoke/MethodHandle",
            "java/lang/invoke/VarHandle");

    private static final List<String> PLATFORM_PREFIXES = List.of(
            "java/", "javax/", "jdk/", "sun/", "com/sun/", "org/w3c/", "org/xml/", "org/ietf/");

    private static final int USERS_SHOWN = 4;

    record Gaps(Map<String, TreeSet<String>> classes, Map<String, TreeSet<String>> members,
                Map<String, TreeSet<String>> notCarried, Map<String, TreeSet<String>> rewritten) {

        boolean isEmpty() {
            return classes.isEmpty() && members.isEmpty() && notCarried.isEmpty();
        }
    }

    record Platform(Map<String, ClassModel> classes) {

        boolean has(String internalName) {
            return classes.containsKey(internalName);
        }

        boolean hasMember(String owner, String name, String descriptor, boolean isField) {
            List<String> toVisit = new ArrayList<>(List.of(owner));
            Set<String> visited = new TreeSet<>();
            while (!toVisit.isEmpty()) {
                String next = toVisit.removeFirst();
                ClassModel model = classes.get(next);
                if (model == null || !visited.add(next)) {
                    continue;
                }
                if (declares(model, next, name, descriptor, isField)) {
                    return true;
                }
                model.superclass().ifPresent(superclass -> toVisit.add(superclass.asInternalName()));
                model.interfaces().forEach(implemented -> toVisit.add(implemented.asInternalName()));
            }
            return false;
        }

        private static boolean declares(ClassModel model, String owner, String name, String descriptor,
                                        boolean isField) {
            if (isField) {
                return model.fields().stream().anyMatch(field -> field.fieldName().equalsString(name)
                        && field.fieldType().equalsString(descriptor));
            }
            return model.methods().stream().anyMatch(method -> method.methodName().equalsString(name)
                    && (method.methodType().equalsString(descriptor) || SIGNATURE_POLYMORPHIC_OWNERS.contains(owner)));
        }
    }

    public static void main(String[] arguments) throws IOException {
        List<String> given = new ArrayList<>(List.of(arguments));
        boolean failOnGaps = given.remove(FAIL_ON_GAPS);
        Optional<Path> reportFile = takeReportFile(given);
        if (given.size() != 2) {
            System.err.println("usage: java scripts/AndroidApiGaps.java [" + FAIL_ON_GAPS + "] [" + REPORT
                    + " <file>] <classes directory or jar> <android.jar>");
            System.exit(2);
        }
        Gaps gaps = findGaps(readClasses(Path.of(given.get(0))), readPlatform(Path.of(given.get(1))));
        String written = report(gaps);
        System.out.print(written);
        if (reportFile.isPresent()) {
            Files.writeString(reportFile.get(), written);
        }
        if (failOnGaps && !gaps.isEmpty()) {
            System.exit(1);
        }
    }

    private static Optional<Path> takeReportFile(List<String> given) {
        int at = given.indexOf(REPORT);
        if (at < 0 || at + 1 >= given.size()) {
            return Optional.empty();
        }
        Path file = Path.of(given.get(at + 1));
        given.subList(at, at + 2).clear();
        return Optional.of(file);
    }

    static Gaps findGaps(List<ClassModel> users, Platform platform) {
        Map<String, TreeSet<String>> classes = new TreeMap<>();
        Map<String, TreeSet<String>> members = new TreeMap<>();
        Map<String, TreeSet<String>> notCarried = new TreeMap<>();
        Map<String, TreeSet<String>> rewritten = new TreeMap<>();
        Set<String> carried = new TreeSet<>();
        users.forEach(user -> carried.add(user.thisClass().asInternalName()));
        for (ClassModel user : users) {
            String userName = user.thisClass().asInternalName();
            for (PoolEntry entry : user.constantPool()) {
                if (entry instanceof ClassEntry referenced) {
                    String name = elementName(referenced.asInternalName());
                    if (isPrimitive(name)) {
                        continue;
                    }
                    if (!isPlatform(name)) {
                        if (!carried.contains(name)) {
                            notCarried.computeIfAbsent(name, key -> new TreeSet<>()).add(userName);
                        }
                        continue;
                    }
                    if (platform.has(name)) {
                        continue;
                    }
                    (REWRITTEN_BY_D8.contains(name) ? rewritten : classes)
                            .computeIfAbsent(name, key -> new TreeSet<>()).add(userName);
                } else if (entry instanceof MemberRefEntry member) {
                    String owner = elementName(member.owner().asInternalName());
                    if (!isPlatform(owner) || !platform.has(owner)) {
                        continue;
                    }
                    boolean isField = entry instanceof FieldRefEntry;
                    String name = member.name().stringValue();
                    String descriptor = member.type().stringValue();
                    if (!platform.hasMember(owner, name, descriptor, isField)) {
                        String described = (isField ? "field " : "") + owner + "." + name + descriptor;
                        members.computeIfAbsent(described, key -> new TreeSet<>()).add(userName);
                    }
                }
            }
        }
        return new Gaps(classes, members, notCarried, rewritten);
    }

    static String report(Gaps gaps) {
        StringBuilder written = new StringBuilder();
        section(written, "Classes the platform does not have", gaps.classes());
        section(written, "Members the platform does not have", gaps.members());
        section(written, "Classes referenced but not carried", gaps.notCarried());
        section(written, "Rewritten away by D8, so not gaps", gaps.rewritten());
        return written.toString();
    }

    private static void section(StringBuilder written, String title, Map<String, TreeSet<String>> found) {
        written.append("== ").append(title).append(": ").append(found.size()).append('\n');
        found.forEach((name, users) -> written.append(name).append("  <- ").append(users.size()).append(' ')
                .append(abbreviate(users)).append('\n'));
        written.append('\n');
    }

    private static String abbreviate(TreeSet<String> users) {
        List<String> shown = users.stream().limit(USERS_SHOWN)
                .map(user -> user.substring(user.lastIndexOf('/') + 1)).toList();
        return shown + (users.size() > USERS_SHOWN ? " ..." : "");
    }

    private static String elementName(String internalName) {
        String name = internalName;
        while (name.startsWith("[")) {
            name = name.substring(1);
        }
        return name.startsWith("L") && name.endsWith(";") ? name.substring(1, name.length() - 1) : name;
    }

    private static boolean isPrimitive(String name) {
        return name.length() == 1;
    }

    private static boolean isPlatform(String name) {
        return PLATFORM_PREFIXES.stream().anyMatch(name::startsWith);
    }

    private static List<ClassModel> readClasses(Path classes) throws IOException {
        if (Files.isDirectory(classes)) {
            try (Stream<Path> walk = Files.walk(classes)) {
                List<ClassModel> read = new ArrayList<>();
                for (Path file : walk.filter(path -> path.toString().endsWith(".class")).sorted().toList()) {
                    read.add(ClassFile.of().parse(file));
                }
                return read;
            }
        }
        return new ArrayList<>(readJar(classes).values());
    }

    private static Platform readPlatform(Path androidJar) throws IOException {
        return new Platform(readJar(androidJar));
    }

    private static Map<String, ClassModel> readJar(Path jar) throws IOException {
        Map<String, ClassModel> read = new HashMap<>();
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            for (ZipEntry entry : zip.stream().filter(each -> each.getName().endsWith(".class")).toList()) {
                parse(zip, entry).ifPresent(model -> read.put(model.thisClass().asInternalName(), model));
            }
        }
        return read;
    }

    private static Optional<ClassModel> parse(ZipFile zip, ZipEntry entry) throws IOException {
        if (entry.getName().endsWith("module-info.class")) {
            return Optional.empty();
        }
        try (InputStream bytes = zip.getInputStream(entry)) {
            return Optional.of(ClassFile.of().parse(bytes.readAllBytes()));
        }
    }
}
