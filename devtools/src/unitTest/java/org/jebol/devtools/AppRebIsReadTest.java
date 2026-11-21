package org.jebol.devtools;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class AppRebIsReadTest {

    @TempDir
    Path directory;

    private AppDeclaration declared(ATestApp app) {
        AppReb.Reading reading = AppReb.read(app.directory());
        assertThat(reading).isInstanceOf(AppReb.Declared.class);
        return ((AppReb.Declared) reading).declaration();
    }

    private List<FieldFault> refused(ATestApp app) {
        AppReb.Reading reading = AppReb.read(app.directory());
        assertThat(reading).isInstanceOf(AppReb.Refused.class);
        return ((AppReb.Refused) reading).faults();
    }

    private ATestApp smallestWith(String moreFields) {
        return ATestApp.smallestIn(directory).withAppReb(ATestApp.SMALLEST_APP_REB + moreFields);
    }

    private ATestApp smallestReplacing(String field, String replacement) {
        String source = ATestApp.SMALLEST_APP_REB.lines()
                .map(line -> line.startsWith(field + ":") ? replacement : line)
                .reduce("", (all, line) -> all + line + "\n");
        return ATestApp.smallestIn(directory).withAppReb(source);
    }

    @Test
    @DisplayName("a complete app.reb is read into every field, each in its own type")
    void aCompleteAppRebIsRead() {
        AppDeclaration declaration = declared(ATestApp.completeIn(directory));

        assertThat(declaration.title()).isEqualTo("Notes");
        assertThat(declaration.identity()).isEqualTo("com.example.notes");
        assertThat(declaration.version()).isEqualTo("1.2.3");
        assertThat(declaration.build()).isEqualTo(7);
        assertThat(declaration.icon()).isEqualTo(directory.resolve("icon.png"));
        assertThat(declaration.summary()).isEqualTo("Quick notes that sync nowhere");
        assertThat(declaration.publisher()).isEqualTo("Example Ltd");
        assertThat(declaration.contact()).isEqualTo("dev@example.com");
        assertThat(declaration.website()).contains("https://example.com/notes");
        assertThat(declaration.category()).isEqualTo(Category.PRODUCTIVITY);
        assertThat(declaration.colour()).isEqualTo(new Colour(0, 120, 215));
        assertThat(declaration.uses()).containsExactly(
                Map.entry(Service.NOTIFICATIONS, "Notes reminds you of a note at the time you set"),
                Map.entry(Service.CAMERA, "Notes attaches a photo to a note"));
        assertThat(declaration.requires()).containsExactly("sqlite");
        assertThat(declaration.entry()).isEqualTo("main.reb");
        assertThat(declaration.iconForeground()).isEmpty();
    }

    @Test
    @DisplayName("what is optional takes its default when absent")
    void anAbsentOptionalFieldTakesItsDefault() {
        AppDeclaration declaration = declared(ATestApp.smallestIn(directory));

        assertThat(declaration.entry()).isEqualTo("main.reb");
        assertThat(declaration.category()).isEqualTo(Category.UTILITY);
        assertThat(declaration.colour()).isEqualTo(new Colour(255, 255, 255));
        assertThat(declaration.uses()).isEmpty();
        assertThat(declaration.requires()).isEmpty();
        assertThat(declaration.website()).isEmpty();
    }

    @Test
    @DisplayName("every missing required field is reported at once")
    void everyMissingFieldIsReportedAtOnce() {
        ATestApp app = ATestApp.smallestIn(directory).withAppReb("""
                id:        com.example.notes
                version:   1.2.3
                icon:      %icon.png
                summary:   "Quick notes that sync nowhere"
                publisher: "Example Ltd"
                """);

        assertThat(refused(app)).extracting(FieldFault::field, FieldFault::fault).containsExactly(
                tuple("title", Fault.MISSING),
                tuple("build", Fault.MISSING),
                tuple("contact", Fault.MISSING));
    }

    @Test
    @DisplayName("a fault names its field and what was expected")
    void aFaultNamesItsFieldAndWhatWasExpected() {
        ATestApp app = smallestReplacing("version", """
                version: "1.2.3"
                """.strip());

        assertThat(refused(app)).extracting(FieldFault::describe)
                .containsExactly("version: expected a tuple! of three parts, like 1.2.3, but was a string!");
    }

    @Test
    @DisplayName("faults of every kind are reported together, in the file's order")
    void faultsOfEveryKindAreReportedTogether() {
        ATestApp app = ATestApp.smallestIn(directory).withAppReb("""
                title:     "Notes"
                id:        notes
                version:   1.2.3
                build:     "7"
                icon:      %icon.png
                summary:   "Quick notes that sync nowhere"
                publisher: "Example Ltd"
                contact:   dev@example.com
                categry:   game
                """);

        assertThat(refused(app)).extracting(FieldFault::field, FieldFault::fault).containsExactly(
                tuple("id", Fault.MALFORMED),
                tuple("build", Fault.WRONG_TYPE),
                tuple("categry", Fault.UNKNOWN_FIELD));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"title: {}", "summary: {}", "publisher: {}"})
    @DisplayName("a required text may not be empty")
    void aRequiredTextMayNotBeEmpty(String field) {
        String name = field.substring(0, field.indexOf(':'));
        assertThat(refused(smallestReplacing(name, field)))
                .extracting(FieldFault::field, FieldFault::fault)
                .containsExactly(tuple(name, Fault.MALFORMED));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"notes", "com", "com.1example", "com..notes"})
    @DisplayName("an identity that is not two or more dotted parts, each starting with a letter, is malformed")
    void aMalformedIdentityIsRefused(String identity) {
        List<FieldFault> faults = refused(smallestReplacing("id", "id: " + identity));

        assertThat(faults).extracting(FieldFault::field).containsExactly("id");
        assertThat(faults.getFirst().describe())
                .isEqualTo("id: expected a word of two or more dotted parts, like com.example.notes");
    }

    @Test
    @DisplayName("an identity in quotes is a string, not a word, and is the wrong type")
    void aQuotedIdentityIsTheWrongType() {
        assertThat(refused(smallestReplacing("id", "id: {com.example.notes}")))
                .extracting(FieldFault::fault).containsExactly(Fault.WRONG_TYPE);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"version: 1.2.3.4", "version: 1.2"})
    @DisplayName("a version is a tuple of exactly three parts")
    void aVersionIsThreeParts(String field) {
        assertThat(refused(smallestReplacing("version", field))).extracting(FieldFault::field).containsExactly("version");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"build: 0", "build: -3", "build: 2100000001"})
    @DisplayName("a build is a whole number from 1 to Android's largest version code")
    void aBuildIsInRange(String field) {
        assertThat(refused(smallestReplacing("build", field)))
                .extracting(FieldFault::field, FieldFault::fault)
                .containsExactly(tuple("build", Fault.MALFORMED));
    }

    @Test
    @DisplayName("an icon that is not square is malformed")
    void anIconThatIsNotSquareIsRefused() {
        ATestApp app = ATestApp.smallestIn(directory).withIcon("icon.png", 1024, 1000);

        assertThat(refused(app)).extracting(FieldFault::describe)
                .containsExactly("icon: expected a square PNG of at least 1024 pixels");
    }

    @Test
    @DisplayName("an icon smaller than 1024 pixels is malformed")
    void aSmallIconIsRefused() {
        ATestApp app = ATestApp.smallestIn(directory).withIcon("icon.png", 512, 512);

        assertThat(refused(app)).extracting(FieldFault::field).containsExactly("icon");
    }

    @Test
    @DisplayName("an icon that is not a PNG is malformed, so an SVG is refused")
    void anIconThatIsNotAPngIsRefused() {
        ATestApp app = smallestReplacing("icon", "icon: %icon.svg")
                .withFile("icon.svg", "<svg xmlns='http://www.w3.org/2000/svg' width='1024' height='1024'/>");

        assertThat(refused(app)).extracting(FieldFault::field).containsExactly("icon");
    }

    @Test
    @DisplayName("an icon that is not there is malformed")
    void anAbsentIconIsRefused() {
        assertThat(refused(smallestReplacing("icon", "icon: %missing.png")))
                .extracting(FieldFault::field).containsExactly("icon");
    }

    @Test
    @DisplayName("an icon-foreground is checked as the icon is, and read when good")
    void anIconForegroundIsChecked() {
        AppDeclaration good = declared(smallestWith("icon-foreground: %front.png\n").withIcon("front.png", 1024, 1024));
        assertThat(good.iconForeground()).contains(directory.resolve("front.png"));

        ATestApp bad = smallestWith("icon-foreground: %front.png\n").withIcon("front.png", 100, 100);
        assertThat(refused(bad)).extracting(FieldFault::field).containsExactly("icon-foreground");
    }

    @Test
    @DisplayName("an entry chooses another script to start from")
    void anEntryChoosesAnotherScript() {
        ATestApp app = smallestWith("entry: %start/notes.reb\n").withFile("start/notes.reb", ATestApp.MAIN_SCRIPT);

        assertThat(declared(app).entry()).isEqualTo("start/notes.reb");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"entry: %absent.reb", "entry: %../outside.reb", "entry: %/etc/passwd"})
    @DisplayName("an entry that is not a script inside the app's directory is malformed")
    void anEntryOutsideTheAppIsRefused(String field) {
        assertThat(refused(smallestWith(field + "\n"))).extracting(FieldFault::describe)
                .containsExactly("entry: expected a script inside the app's directory");
    }

    @Test
    @DisplayName("with no entry, main.reb must be there")
    void mainRebMustBeThere() {
        ATestApp app = ATestApp.in(directory).withAppReb(ATestApp.SMALLEST_APP_REB).withIcon("icon.png", 1024, 1024);

        assertThat(refused(app)).extracting(FieldFault::field).containsExactly("entry");
    }

    @Test
    @DisplayName("a service in uses with no sentence is malformed, and one not known is an unknown word")
    void usesAreChecked() {
        ATestApp app = smallestWith("""
                uses: [
                    location ""
                    telepathy "reads your mind"
                ]
                """);

        assertThat(refused(app)).extracting(FieldFault::field, FieldFault::fault).containsExactly(
                tuple("uses", Fault.MALFORMED),
                tuple("uses", Fault.UNKNOWN_WORD));
    }

    @Test
    @DisplayName("a category outside the list is an unknown word, naming the list")
    void anUnknownCategoryIsRefused() {
        assertThat(refused(smallestWith("category: spreadsheets\n"))).extracting(FieldFault::describe)
                .containsExactly("category: expected one of utility, productivity, game, education, graphics, "
                        + "audio, video, developer, network, but was spreadsheets");
    }

    @Test
    @DisplayName("an extension that is not JEBOL's own is an unknown word")
    void anUnknownExtensionIsRefused() {
        assertThat(refused(smallestWith("requires: [sqlite postgres]\n")))
                .extracting(FieldFault::field, FieldFault::fault)
                .containsExactly(tuple("requires", Fault.UNKNOWN_WORD));
    }

    @Test
    @DisplayName("a colour is a tuple of three parts")
    void aColourIsThreeParts() {
        assertThat(refused(smallestWith("colour: 1.2.3.4\n"))).extracting(FieldFault::field).containsExactly("colour");
    }

    @Test
    @DisplayName("a field given twice is malformed, rather than one of them quietly winning")
    void aFieldGivenTwiceIsRefused() {
        assertThat(refused(smallestWith("title: {Other}\n")))
                .extracting(FieldFault::field, FieldFault::fault)
                .containsExactly(tuple("title", Fault.MALFORMED));
    }

    @Test
    @DisplayName("nothing in app.reb is evaluated: an expression is the wrong type, not its result")
    void nothingIsEvaluated() {
        assertThat(refused(smallestReplacing("title", "title: join {No} {tes}")))
                .extracting(FieldFault::field).contains("title");
    }

    @Test
    @DisplayName("with no app.reb at all, that one fault is reported")
    void noAppRebIsRefused() {
        ATestApp app = ATestApp.in(directory).withFile("main.reb", ATestApp.MAIN_SCRIPT);

        assertThat(refused(app)).extracting(FieldFault::describe)
                .containsExactly("app.reb: expected a file at the top of the app's directory");
    }

    @Test
    @DisplayName("an app.reb that does not load is reported, not read in part")
    void anAppRebThatDoesNotLoadIsRefused() {
        ATestApp app = ATestApp.smallestIn(directory).withAppReb("title: {Notes\nid: com.example.notes\n");

        assertThat(refused(app)).extracting(FieldFault::field, FieldFault::fault)
                .containsExactly(tuple("app.reb", Fault.MALFORMED));
    }

    @Test
    @DisplayName("a website is a url")
    void aWebsiteIsAUrl() {
        assertThat(refused(smallestWith("website: {example.com}\n")))
                .extracting(FieldFault::fault).containsExactly(Fault.WRONG_TYPE);
        assertThat(declared(smallestWith("website: http://example.com\n")).website())
                .isEqualTo(Optional.of("http://example.com"));
    }
}
