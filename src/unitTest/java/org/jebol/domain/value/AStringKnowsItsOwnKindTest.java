package org.jebol.domain.value;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AStringKnowsItsOwnKindTest {

    private final List<AnyStringValue.AnyStringDatatype> everyStringDatatype = List.of(
            StringValue.TYPE, FileValue.TYPE, EmailValue.TYPE, RefValue.TYPE, UrlValue.TYPE, TagValue.TYPE);

    private String quote(String text) {
        return '"' + text + '"';
    }

    @Nested
    @DisplayName("building one from a datatype")
    class FromADatatype {

        @Test
        @DisplayName("each any-string! datatype builds a value that answers to that datatype")
        void buildsEachStringDatatypesOwnKind() {
            assertThat(everyStringDatatype).allSatisfy(asked ->
                    assertThat(asked.wrap("abc").datatype()).isSameAs(asked));
        }

        @Test
        @DisplayName("and the class is the type, so the tag never has to be asked")
        void takesItsKindFromTheClass() {
            assertThat(StringValue.TYPE.wrap("a")).isInstanceOf(StringValue.class);
            assertThat(FileValue.TYPE.wrap("a")).isInstanceOf(FileValue.class);
            assertThat(EmailValue.TYPE.wrap("a")).isInstanceOf(EmailValue.class);
            assertThat(RefValue.TYPE.wrap("a")).isInstanceOf(RefValue.class);
            assertThat(UrlValue.TYPE.wrap("a")).isInstanceOf(UrlValue.class);
            assertThat(TagValue.TYPE.wrap("a")).isInstanceOf(TagValue.class);
        }

        @ParameterizedTest
        @ValueSource(strings = {"block", "binary", "issue", "word", "char"})
        @DisplayName("a value outside any-string! cannot be seen as a string")
        void refusesAValueOutsideTheFamily(String spelling) {
            Value outside = switch (spelling) {
                case "block" -> BlockValue.of();
                case "binary" -> BinaryValue.ofBytes(new byte[0]);
                case "issue" -> IssueValue.of("a");
                case "word" -> WordValue.of("a");
                default -> CharacterValue.of('a');
            };
            assertThatThrownBy(() -> StringValue.TYPE.as(outside))
                    .isInstanceOfSatisfying(Raised.class, raised ->
                            assertThat(raised.error().errorId()).isEqualTo("not-same-class"));
        }
    }

    @Nested
    @DisplayName("where it stands in its storage")
    class ItsPosition {

        @Test
        @DisplayName("the head is position one")
        void startsTheHeadAtOne() {
            assertThat(FileValue.TYPE.wrap(StringStorage.of("abc"), 1).index())
                    .isEqualTo(1);
        }

        @Test
        @DisplayName("the tail, one past the last letter, is still a place to stand")
        void allowsTheTail() {
            assertThat(FileValue.TYPE.wrap(StringStorage.of("abc"), 4).text())
                    .isEmpty();
        }

        @ParameterizedTest
        @ValueSource(ints = {0, -1, 5})
        @DisplayName("before the head or past the tail is refused")
        void refusesOutsideTheStorage(int index) {
            assertThatThrownBy(() -> FileValue.TYPE.wrap(StringStorage.of("abc"), index))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("outside 1..4");
        }

        @Test
        @DisplayName("storage is required")
        void requiresStorage() {
            assertThatThrownBy(() -> FileValue.TYPE.wrap(null, 1))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("needs storage");
        }

        @Test
        @DisplayName("moving along keeps the type and shares the storage")
        void keepsTheKindMovingAlong() {
            AnyStringValue file = FileValue.of("abc");
            AnyStringValue moved = file.atIndex(2);

            assertThat(moved).isInstanceOf(FileValue.class);
            assertThat(moved.text()).isEqualTo("bc");
            assertThat(moved.sharesStorageWith(file)).isTrue();
        }
    }

    @Nested
    @DisplayName("making another of the same type")
    class TheSameKind {

        @Test
        @DisplayName("holding new text keeps the type and starts at the head of fresh storage")
        void keepsTheKindWhenHeld() {
            assertThat(everyStringDatatype).allSatisfy(kind -> {
                AnyStringValue original = kind.wrap("old").atIndex(2);
                AnyStringValue held = original.wrap("new");

                assertThat(held.datatype()).isSameAs(kind);
                assertThat(held.text()).isEqualTo("new");
                assertThat(held.index()).isEqualTo(1);
                assertThat(held.sharesStorageWith(original)).isFalse();
            });
        }

        @Test
        @DisplayName("holding nothing at all is still that type")
        void holdsNothing() {
            AnyStringValue held = TagValue.of("a").wrap("");

            assertThat(held).isInstanceOf(TagValue.class);
            assertThat(held.text()).isEmpty();
        }

        @Test
        @DisplayName("a copy keeps the type")
        void keepsTheKindInACopy() {
            assertThat(UrlValue.of("http://a").copy(false)).isInstanceOf(UrlValue.class);
        }
    }

    @Nested
    @DisplayName("seeing the same letters as another type")
    class AsAnotherKind {

        @Test
        @DisplayName("the letters, the position and the storage are shared, only the type changes")
        void changesOnlyTheKind() {
            AnyStringValue file = FileValue.of("abc").atIndex(2);
            Value seen = UrlValue.TYPE.as(file);

            assertThat(seen).isInstanceOfSatisfying(UrlValue.class, url -> {
                assertThat(url.index()).isEqualTo(2);
                assertThat(url.text()).isEqualTo("bc");
                assertThat(url.sharesStorageWith(file)).isTrue();
            });
        }

        @Test
        @DisplayName("a type outside any-string! cannot see a string as itself")
        void refusesOutsideTheFamily() {
            assertThatThrownBy(() -> BinaryValue.TYPE.as(StringValue.of("a")))
                    .isInstanceOfSatisfying(Raised.class, raised ->
                            assertThat(raised.error().errorId()).isEqualTo("not-same-class"));
        }
    }

    @Nested
    @DisplayName("naming a location")
    class NamingALocation {

        @Test
        @DisplayName("a file and a url name a location")
        void namesALocationForAFileOrAUrl() {
            assertThat(FileValue.of("a").isALocation()).isTrue();
            assertThat(UrlValue.of("http://a").isALocation()).isTrue();
        }

        @Test
        @DisplayName("no other string does, an email included")
        void namesNoLocationOtherwise() {
            assertThat(StringValue.of("a").isALocation()).isFalse();
            assertThat(EmailValue.of("a@b").isALocation()).isFalse();
            assertThat(TagValue.of("a").isALocation()).isFalse();
            assertThat(RefValue.of("a").isALocation()).isFalse();
        }
    }

    @Nested
    @DisplayName("equality")
    class Equality {

        @Test
        @DisplayName("the same letters in two kinds are two different values")
        void differentKindsDiffer() {
            assertThat(TagValue.of("a")).isNotEqualTo(StringValue.of("a"));
            assertThat(UrlValue.of("a")).isNotEqualTo(FileValue.of("a"));
        }

        @Test
        @DisplayName("the same letters in one type are equal, and hash alike")
        void holdsForTheSameKind() {
            assertThat(RefValue.of("a")).isEqualTo(RefValue.of("a"));
            assertThat(RefValue.of("a")).hasSameHashCodeAs(RefValue.of("a"));
        }

        @Test
        @DisplayName("Rebol's own equality still lets two kinds that were brought together compare by letters")
        void comparesByLettersWhenBroughtTogether() {
            assertThat(FileValue.of("a").isEqualTo(StringValue.of("A"), Sameness.bringTogether()))
                    .isTrue();
        }
    }

    @Nested
    @DisplayName("how each type writes itself")
    class WritingItself {

        @Test
        @DisplayName("a string is formed bare and molded in quotes")
        void writesAString() {
            assertThat(StringValue.of("abc").form()).isEqualTo("abc");
            assertThat(StringValue.of("abc").mold()).isEqualTo(quote("abc"));
        }

        @Test
        @DisplayName("a tag is in angle brackets either way")
        void writesATag() {
            assertThat(TagValue.of("a").form()).isEqualTo("<a>");
            assertThat(TagValue.of("a").mold()).isEqualTo("<a>");
        }

        @Test
        @DisplayName("a file is molded behind a percent, with what would break it written in hex")
        void writesAFile() {
            assertThat(FileValue.of("a b").mold()).isEqualTo("%a%20b");
            assertThat(FileValue.of("a b").form()).isEqualTo("a b");
        }

        @Test
        @DisplayName("an empty file is molded as an empty quoted path")
        void writesAnEmptyFile() {
            assertThat(FileValue.of("").mold()).isEqualTo("%" + quote(""));
        }

        @Test
        @DisplayName("the delete character is written in hex in a file")
        void writesTheDeleteCharacterInAFile() {
            assertThat(FileValue.of("a\u007Fb").mold()).isEqualTo("%a%7Fb");
        }

        @Test
        @DisplayName("a url that reads back is molded as itself")
        void writesAUrlThatReadsBack() {
            assertThat(UrlValue.of("http://a").mold()).isEqualTo("http://a");
        }

        @Test
        @DisplayName("an email that reads back is molded as itself")
        void writesAnEmailThatReadsBack() {
            assertThat(EmailValue.of("a@b").mold()).isEqualTo("a@b");
        }

        @Test
        @DisplayName("an email with a slash would not read back, so it is molded as a construct")
        void writesAnEmailWithASlash() {
            assertThat(EmailValue.of("a/b@c").mold()).isEqualTo("""
                    #(email! "a/b@c")""");
        }

        @Test
        @DisplayName("a ref that reads back is molded behind an at sign")
        void writesARefThatReadsBack() {
            assertThat(RefValue.of("abc").mold()).isEqualTo("@abc");
        }

        @Test
        @DisplayName("a ref holding an at sign would read back as an email, so it is molded as a construct")
        void writesARefHoldingAnAtSign() {
            assertThat(RefValue.of("a@b").mold()).isEqualTo("""
                    #(ref! "a@b")""");
        }

        @Test
        @DisplayName("a construct past the head names where it stands")
        void writesAConstructPastTheHead() {
            assertThat(RefValue.of("a@b").atIndex(2).mold()).isEqualTo("""
                    #(ref! "a@b" 2)""");
        }
    }

    @Nested
    @DisplayName("the two halves of an email")
    class EmailHalves {

        @Test
        @DisplayName("the user is in front of the at sign and the host behind it")
        void splitIntoBothHalves() {
            EmailValue address = EmailValue.of("bob@rebol.tech");

            assertThat(address.user()).isEqualTo(StringValue.of("bob"));
            assertThat(address.host()).isEqualTo(StringValue.of("rebol.tech"));
        }

        @Test
        @DisplayName("with no at sign the whole is the user and there is no host")
        void splitWithoutAnAtSign() {
            EmailValue address = EmailValue.of("bob");

            assertThat(address.user()).isEqualTo(StringValue.of("bob"));
            assertThat(address.host()).isEqualTo(NoneValue.none());
        }

        @Test
        @DisplayName("an at sign at the very end leaves an empty host")
        void splitAtAnAtSignAtTheEnd() {
            assertThat(EmailValue.of("bob@").host()).isEqualTo(StringValue.of(""));
        }

        @Test
        @DisplayName("the halves are read from the head, wherever the email stands")
        void readFromTheHead() {
            EmailValue moved = (EmailValue) EmailValue.of("bob@x").atIndex(3);

            assertThat(moved.user()).isEqualTo(StringValue.of("bob"));
            assertThat(moved.host()).isEqualTo(StringValue.of("x"));
        }

        @Test
        @DisplayName("rewriting the user leaves the host alone")
        void rewriteTheUser() {
            EmailValue address = EmailValue.of("bob@x");
            address.rewriteUserAs("ann");

            assertThat(address.text()).isEqualTo("ann@x");
        }

        @Test
        @DisplayName("rewriting the host leaves the user alone")
        void rewriteTheHost() {
            EmailValue address = EmailValue.of("bob@x");
            address.rewriteHostAs("y");

            assertThat(address.text()).isEqualTo("bob@y");
        }

        @Test
        @DisplayName("rewriting a host where there was none adds the at sign")
        void addAHost() {
            EmailValue address = EmailValue.of("bob");
            address.rewriteHostAs("y");

            assertThat(address.text()).isEqualTo("bob@y");
        }

        @Test
        @DisplayName("rewriting the user of an email with no host replaces the whole")
        void rewriteAUserWithNoHost() {
            EmailValue address = EmailValue.of("bob");
            address.rewriteUserAs("ann");

            assertThat(address.text()).isEqualTo("ann");
        }
    }
}
