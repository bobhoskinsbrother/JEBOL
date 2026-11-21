package org.jebol.devtools;

import org.jebol.domain.read.TranscodeResult;
import org.jebol.domain.read.Transcoder;
import org.jebol.domain.value.AnyBlockValue;
import org.jebol.domain.value.AnyStringValue;
import org.jebol.domain.value.EmailValue;
import org.jebol.domain.value.FileValue;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.SetWordValue;
import org.jebol.domain.value.StringValue;
import org.jebol.domain.value.TupleValue;
import org.jebol.domain.value.UrlValue;
import org.jebol.domain.value.Value;
import org.jebol.domain.value.WordValue;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;

public final class AppReb {

    public static final String FILE = "app.reb";
    public static final String DEFAULT_ENTRY = "main.reb";
    public static final int SMALLEST_ICON = 1024;
    public static final int LARGEST_BUILD = 2_100_000_000;

    private static final List<String> FIELDS = List.of("title", "id", "version", "build", "icon", "summary",
            "publisher", "contact", "website", "category", "colour", "uses", "requires", "entry", "icon-foreground");

    private static final Pattern IDENTITY_PART = Pattern.compile("[A-Za-z][A-Za-z0-9_]*");

    private static final String A_STRING = "a string!";
    private static final String AN_ICON = "a square PNG of at least " + SMALLEST_ICON + " pixels";

    public sealed interface Reading permits Declared, Refused {
    }

    public record Declared(AppDeclaration declaration) implements Reading {
    }

    public record Refused(List<FieldFault> faults) implements Reading {
    }

    private final Path directory;
    private final Map<String, Value> fields = new LinkedHashMap<>();
    private final List<String> unknownFields = new ArrayList<>();
    private final List<FieldFault> faults = new ArrayList<>();
    private final List<FieldFault> faultsOfShape = new ArrayList<>();

    private AppReb(Path directory) {
        this.directory = directory.toAbsolutePath().normalize();
    }

    public static Reading read(Path directory) {
        return new AppReb(directory).readFile();
    }

    private Reading readFile() {
        Path file = directory.resolve(FILE);
        if (!Files.isRegularFile(file)) {
            return new Refused(List.of(new FieldFault(FILE, Fault.MISSING, "a file at the top of the app's directory")));
        }
        TranscodeResult loaded = Transcoder.transcode(readText(file));
        Optional<AnyBlockValue> block = loaded.values();
        if (block.isEmpty()) {
            return new Refused(List.of(new FieldFault(FILE, Fault.MALFORMED, "a REBOL block that loads")));
        }
        collectFields(block.get().remaining());
        AppDeclaration declaration = declare();
        unknownFields.forEach(name -> faults.add(new FieldFault(name, Fault.UNKNOWN_FIELD, "one of app.reb's fields")));
        faults.addAll(faultsOfShape);
        return faults.isEmpty() ? new Declared(declaration) : new Refused(List.copyOf(faults));
    }

    private static String readText(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }

    private void collectFields(List<Value> values) {
        int at = 0;
        while (at < values.size()) {
            if (!(values.get(at) instanceof SetWordValue named)) {
                faultsOfShape.add(new FieldFault(FILE, Fault.MALFORMED, "each field as name: value"));
                at++;
                continue;
            }
            String name = named.canonical();
            if (at + 1 >= values.size() || values.get(at + 1) instanceof SetWordValue) {
                faultsOfShape.add(new FieldFault(name, Fault.MALFORMED, "a value after it"));
                at++;
                continue;
            }
            Value value = values.get(at + 1);
            if (fields.containsKey(name) || unknownFields.contains(name)) {
                faults.add(new FieldFault(name, Fault.MALFORMED, "the field given once"));
            } else if (FIELDS.contains(name)) {
                fields.put(name, value);
            } else {
                unknownFields.add(name);
            }
            at += 2;
        }
    }

    private AppDeclaration declare() {
        String title = requiredText("title");
        String identity = identity();
        String version = threePartTuple("version", "a tuple! of three parts, like 1.2.3").orElse("");
        int build = build();
        Path icon = icon("icon", true).orElse(directory);
        String summary = requiredText("summary");
        String publisher = requiredText("publisher");
        String contact = required("contact", "an email!", EmailValue.class).map(AnyStringValue::form).orElse("");
        Optional<String> website = optional("website", "a url!", UrlValue.class).map(AnyStringValue::form);
        Category category = category();
        Colour colour = colour();
        Map<Service, String> uses = uses();
        List<String> requires = requires();
        String entry = entry();
        Optional<Path> iconForeground = icon("icon-foreground", false);
        return new AppDeclaration(directory, title, identity, version, build, icon, summary, publisher, contact,
                website, category, colour, uses, requires, entry, iconForeground);
    }

    private <T extends Value> Optional<T> required(String field, String expected, Class<T> type) {
        if (!fields.containsKey(field)) {
            faults.add(new FieldFault(field, Fault.MISSING, expected));
            return Optional.empty();
        }
        return optional(field, expected, type);
    }

    private <T extends Value> Optional<T> optional(String field, String expected, Class<T> type) {
        Value value = fields.get(field);
        if (value == null) {
            return Optional.empty();
        }
        if (!type.isInstance(value)) {
            faults.add(new FieldFault(field, Fault.WRONG_TYPE, expected + ", but was " + withArticle(value.datatype().literalSpelling())));
            return Optional.empty();
        }
        return Optional.of(type.cast(value));
    }

    private static String withArticle(String typeName) {
        return ("aeiou".indexOf(typeName.charAt(0)) >= 0 ? "an " : "a ") + typeName;
    }

    private String requiredText(String field) {
        Optional<String> text = required(field, A_STRING, StringValue.class).map(AnyStringValue::form);
        if (text.isPresent() && text.get().isBlank()) {
            faults.add(new FieldFault(field, Fault.MALFORMED, "a string! that is not empty"));
        }
        return text.orElse("");
    }

    private String identity() {
        Optional<String> word = required("id", "a word! of dotted parts, like com.example.notes", WordValue.class)
                .map(WordValue::spelling);
        if (word.isPresent() && !isReverseDomain(word.get())) {
            faults.add(new FieldFault("id", Fault.MALFORMED, "a word of two or more dotted parts, like com.example.notes"));
        }
        return word.orElse("");
    }

    private static boolean isReverseDomain(String identity) {
        String[] parts = identity.split("\\.", -1);
        if (parts.length < 2) {
            return false;
        }
        for (String part : parts) {
            if (!IDENTITY_PART.matcher(part).matches()) {
                return false;
            }
        }
        return true;
    }

    private Optional<String> threePartTuple(String field, String expected) {
        Optional<TupleValue> tuple = fields.containsKey(field)
                ? optional(field, expected, TupleValue.class)
                : required(field, expected, TupleValue.class);
        if (tuple.isEmpty()) {
            return Optional.empty();
        }
        int[] segments = tuple.get().segments();
        if (tuple.get().segmentCount() != 3) {
            faults.add(new FieldFault(field, Fault.MALFORMED, expected));
            return Optional.empty();
        }
        return Optional.of(segments[0] + "." + segments[1] + "." + segments[2]);
    }

    private int build() {
        Optional<IntegerValue> number = required("build", "an integer!", IntegerValue.class);
        if (number.isEmpty()) {
            return 0;
        }
        long magnitude = number.get().magnitude();
        if (magnitude < 1 || magnitude > LARGEST_BUILD) {
            faults.add(new FieldFault("build", Fault.MALFORMED, "a whole number from 1 to " + LARGEST_BUILD));
            return 0;
        }
        return (int) magnitude;
    }

    private Optional<Path> icon(String field, boolean isRequired) {
        Optional<FileValue> named = isRequired
                ? required(field, "a file! naming " + AN_ICON, FileValue.class)
                : optional(field, "a file! naming " + AN_ICON, FileValue.class);
        if (named.isEmpty()) {
            return Optional.empty();
        }
        Optional<Path> file = insideApp(named.get().form());
        if (file.isEmpty() || !IconImage.isASquarePngOfAtLeast(file.get(), SMALLEST_ICON)) {
            faults.add(new FieldFault(field, Fault.MALFORMED, AN_ICON));
            return Optional.empty();
        }
        return file;
    }

    private Optional<Path> insideApp(String name) {
        Path given = Path.of(name);
        if (given.isAbsolute()) {
            return Optional.empty();
        }
        Path resolved = directory.resolve(given).normalize();
        if (!resolved.startsWith(directory) || !Files.isRegularFile(resolved)) {
            return Optional.empty();
        }
        return Optional.of(resolved);
    }

    private Category category() {
        return wordFromList("category", Category::named, Category.listed()).orElse(Category.UTILITY);
    }

    private <T> Optional<T> wordFromList(String field, Function<String, Optional<T>> named, String listed) {
        Optional<WordValue> word = optional(field, "a word!", WordValue.class);
        if (word.isEmpty()) {
            return Optional.empty();
        }
        Optional<T> found = named.apply(word.get().spelling());
        if (found.isEmpty()) {
            faults.add(new FieldFault(field, Fault.UNKNOWN_WORD, "one of " + listed + ", but was " + word.get().spelling()));
        }
        return found;
    }

    private Colour colour() {
        Optional<TupleValue> tuple = optional("colour", "a tuple! of three parts, like 0.120.215", TupleValue.class);
        if (tuple.isEmpty()) {
            return Colour.WHITE;
        }
        if (tuple.get().segmentCount() != 3) {
            faults.add(new FieldFault("colour", Fault.MALFORMED, "a tuple! of three parts, like 0.120.215"));
            return Colour.WHITE;
        }
        int[] segments = tuple.get().segments();
        return new Colour(segments[0], segments[1], segments[2]);
    }

    private Map<Service, String> uses() {
        Map<Service, String> uses = new LinkedHashMap<>();
        Optional<AnyBlockValue> block = optional("uses", "a block! of services and sentences", AnyBlockValue.class);
        if (block.isEmpty()) {
            return uses;
        }
        List<Value> entries = block.get().remaining();
        boolean sentenceMissing = false;
        List<String> unknown = new ArrayList<>();
        for (int at = 0; at < entries.size(); at++) {
            if (!(entries.get(at) instanceof WordValue word)) {
                sentenceMissing = true;
                continue;
            }
            Optional<String> sentence = at + 1 < entries.size() && entries.get(at + 1) instanceof StringValue text
                    ? Optional.of(text.form())
                    : Optional.empty();
            if (sentence.isPresent()) {
                at++;
            }
            Optional<Service> service = Service.named(word.spelling());
            if (service.isEmpty()) {
                unknown.add(word.spelling());
            } else if (sentence.isEmpty() || sentence.get().isBlank()) {
                sentenceMissing = true;
            } else {
                uses.put(service.get(), sentence.get());
            }
        }
        if (sentenceMissing) {
            faults.add(new FieldFault("uses", Fault.MALFORMED, "a sentence for each service"));
        }
        unknown.forEach(word -> faults.add(new FieldFault("uses", Fault.UNKNOWN_WORD,
                "one of notifications, location, camera, but was " + word)));
        return uses;
    }

    private List<String> requires() {
        List<String> requires = new ArrayList<>();
        Optional<AnyBlockValue> block = optional("requires", "a block! of extension names", AnyBlockValue.class);
        if (block.isEmpty()) {
            return requires;
        }
        Set<String> known = KnownExtensions.names();
        for (Value entry : block.get().remaining()) {
            String name = entry instanceof WordValue word ? word.spelling() : String.valueOf(entry);
            if (entry instanceof WordValue && known.contains(name)) {
                requires.add(name);
            } else {
                faults.add(new FieldFault("requires", Fault.UNKNOWN_WORD,
                        "one of " + String.join(", ", known.stream().sorted().toList()) + ", but was " + name));
            }
        }
        return requires;
    }

    private String entry() {
        String expected = "a script inside the app's directory";
        Optional<FileValue> named = optional("entry", "a file! naming " + expected, FileValue.class);
        if (fields.containsKey("entry") && named.isEmpty()) {
            return DEFAULT_ENTRY;
        }
        String name = named.map(AnyStringValue::form).orElse(DEFAULT_ENTRY);
        if (insideApp(name).isEmpty()) {
            faults.add(new FieldFault("entry", Fault.MALFORMED, expected));
            return DEFAULT_ENTRY;
        }
        return name;
    }

    static final class KnownExtensions {

        private static final String LISTING = "known-extensions.txt";

        private KnownExtensions() {
        }

        static Set<String> names() {
            try (InputStream listing = AppReb.class.getResourceAsStream(LISTING)) {
                if (listing == null) {
                    return Set.of();
                }
                return Set.copyOf(new String(listing.readAllBytes(), StandardCharsets.UTF_8).lines()
                        .map(String::strip).filter(line -> !line.isEmpty()).toList());
            } catch (IOException unreadable) {
                throw new UncheckedIOException(unreadable);
            }
        }
    }
}
