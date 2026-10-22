package org.jebol.adapter.web;

import org.jebol.domain.host.FilePort;
import org.jebol.domain.host.WindowPort;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PageWindows implements WindowPort {

    private static final Pattern BROWSER_COLOUR = Pattern.compile("#[0-9a-fA-F]{6}");

    private static final Pattern URL_WITH_SCHEME = Pattern.compile("[a-zA-Z][a-zA-Z0-9+.-]*:.*");

    private static final Pattern SUFFIX_PATTERN = Pattern.compile("\\*(\\.[^*?/\\\\;]+)");

    private static final Set<String> CATCH_ALL_PATTERNS = Set.of("*", "*.*");

    private static final String UNSUGGESTED_SAVE_NAME = "untitled";

    private static final String BROWSER_FAILED = "call-fail";

    private final PageRoundTrip page;
    private final PickedFiles picked;

    public PageWindows(PageRoundTrip page, PickedFiles picked) {
        this.page = page;
        this.picked = picked;
    }

    @Override
    public boolean browse(String target) {
        PageAsk asked = new PageAsk("open-tab", true);
        PageAsk naming = URL_WITH_SCHEME.matcher(target).matches()
                ? asked.with("target", target)
                : asked.with("name", lastSegment(target)).withOctets("octets", readStoredFile(target));
        return requireGivenOrDeclined(page.ask(naming));
    }

    @Override
    public List<String> chooseFiles(
            boolean forSaving, boolean allowingMany,
            Optional<String> suggestedName, Optional<String> title,
            List<String> filterPairs) {
        String accepted = acceptList(filterPairs);
        return forSaving
                ? chooseSaveTarget(suggestedName, accepted)
                : chooseToOpen(allowingMany, title, accepted);
    }

    @Override
    public Optional<String> chooseDirectory(Optional<String> startingAt, Optional<String> title) {
        PageAnswer answer = page.ask(withTitle(new PageAsk("open-folder", true), title));
        if (!requireGivenOrDeclined(answer)) {
            return Optional.empty();
        }
        return Optional.of(picked.keepFolder(answer.listed("files")));
    }

    @Override
    public Optional<int[]> chooseColour(Optional<int[]> suggested) {
        PageAsk asked = new PageAsk("colour", true);
        PageAnswer answer = page.ask(suggested.map(colour -> asked.with("suggested", writeColour(colour)))
                .orElse(asked));
        if (!requireGivenOrDeclined(answer)) {
            return Optional.empty();
        }
        return Optional.of(readColour(answer));
    }

    @Override
    public Optional<String> askForPassword() {
        throw new Denied("no-service",
                "a page asks for a password through the screen's own form, and no page is attached to show it");
    }

    private List<String> chooseToOpen(boolean allowingMany, Optional<String> title, String accepted) {
        PageAsk asked = new PageAsk("open-files", true).with("many", allowingMany).with("accept", accepted);
        PageAnswer answer = page.ask(withTitle(asked, title));
        if (!requireGivenOrDeclined(answer) || answer.listed("files").isEmpty()) {
            return List.of();
        }
        List<String> kept = picked.keepPick(answer.listed("files"));
        return allowingMany ? kept : kept.subList(0, 1);
    }

    private List<String> chooseSaveTarget(Optional<String> suggestedName, String accepted) {
        String offered = suggestedName.map(this::lastSegment)
                .filter(name -> !name.isEmpty())
                .orElse(UNSUGGESTED_SAVE_NAME);
        PageAnswer answer = page.ask(new PageAsk("save-file", true)
                .with("name", offered)
                .with("accept", accepted));
        if (!requireGivenOrDeclined(answer)) {
            return List.of();
        }
        String chosen = answer.text("name").filter(name -> !name.isEmpty()).orElseThrow(() ->
                new Denied(BROWSER_FAILED, "the page chose where to save and gave no name"));
        return List.of(picked.reserveSave(chosen));
    }

    private PageAsk withTitle(PageAsk asked, Optional<String> title) {
        return title.map(heading -> asked.with("title", heading)).orElse(asked);
    }

    private boolean requireGivenOrDeclined(PageAnswer answer) {
        if (answer.isRefused()) {
            throw new Denied(BROWSER_FAILED, answer.why());
        }
        return answer.isGiven();
    }

    private int[] readColour(PageAnswer answer) {
        String written = answer.text("colour")
                .filter(colour -> BROWSER_COLOUR.matcher(colour).matches())
                .orElseThrow(() -> new Denied(BROWSER_FAILED,
                        "the page answered a colour this interpreter cannot read"));
        return new int[] {
                Integer.parseInt(written.substring(1, 3), 16),
                Integer.parseInt(written.substring(3, 5), 16),
                Integer.parseInt(written.substring(5, 7), 16)};
    }

    private byte[] readStoredFile(String target) {
        try {
            return picked.readBytes(target);
        } catch (FilePort.Denied missing) {
            throw new Denied("cannot-open", "there is no " + target + " in the page's store to open");
        }
    }

    private String acceptList(List<String> filterPairs) {
        Set<String> suffixes = new LinkedHashSet<>();
        for (int at = 1; at < filterPairs.size(); at += 2) {
            for (String pattern : filterPairs.get(at).split(";")) {
                String trimmed = pattern.trim();
                if (CATCH_ALL_PATTERNS.contains(trimmed)) {
                    return "";
                }
                Matcher suffix = SUFFIX_PATTERN.matcher(trimmed);
                if (suffix.matches()) {
                    suffixes.add(suffix.group(1));
                }
            }
        }
        return String.join(",", suffixes);
    }

    private String writeColour(int[] colour) {
        return String.format("#%02x%02x%02x", colour[0] & 0xFF, colour[1] & 0xFF, colour[2] & 0xFF);
    }

    private String lastSegment(String path) {
        String withoutTrailingSlash = path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
        return withoutTrailingSlash.substring(withoutTrailingSlash.lastIndexOf('/') + 1);
    }
}
