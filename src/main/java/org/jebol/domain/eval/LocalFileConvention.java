package org.jebol.domain.eval;

import java.util.Optional;

public enum LocalFileConvention {

    POSIX('/'),
    WINDOWS('\\');

    private static final char REBOLS_SEPARATOR = '/';
    private static final char DRIVE_MARK = ':';
    private static final char DOT = '.';

    private final char separator;

    LocalFileConvention(char separator) {
        this.separator = separator;
    }

    public static LocalFileConvention forSeparator(char separator) {
        return separator == WINDOWS.separator ? WINDOWS : POSIX;
    }

    public char separator() {
        return separator;
    }

    public LocalPathStart startLocalPath(String rebolPath) {
        if (this == POSIX || rebolPath.isEmpty() || rebolPath.charAt(0) != REBOLS_SEPARATOR) {
            return new LocalPathStart(rebolPath.startsWith("/") ? String.valueOf(separator) : "",
                    rebolPath.startsWith("/") ? 1 : 0);
        }
        if (rebolPath.length() == 1) {
            return new LocalPathStart("", 1);
        }
        char first = rebolPath.charAt(1);
        if (first == REBOLS_SEPARATOR) {
            return new LocalPathStart(String.valueOf(separator), 2);
        }
        boolean driveEndsThere = rebolPath.length() == 2 || rebolPath.charAt(2) == REBOLS_SEPARATOR;
        if (driveEndsThere) {
            return new LocalPathStart("" + first + DRIVE_MARK + separator, Math.min(3, rebolPath.length()));
        }
        return new LocalPathStart("" + separator + separator, 1);
    }

    public String writeLocalPath(String rebolPath) {
        return written(rebolPath, false);
    }

    public String writeLocalPathResolvingDots(String rebolPath) {
        return written(rebolPath, true);
    }

    private String written(String path, boolean resolvingDots) {
        LocalPathStart start = startLocalPath(path);
        StringBuilder built = new StringBuilder(start.written());
        int at = start.readFrom();
        while (at < path.length()) {
            if (resolvingDots) {
                at = skipAnyDots(path, at, built);
            }
            while (at < path.length()) {
                char letter = path.charAt(at);
                at++;
                if (letter == REBOLS_SEPARATOR) {
                    if (built.isEmpty() || built.charAt(built.length() - 1) != separator) {
                        built.append(separator);
                    }
                    break;
                }
                built.append(letter);
            }
        }
        return built.toString();
    }

    private int skipAnyDots(String path, int at, StringBuilder built) {
        if (at >= path.length() || path.charAt(at) != DOT) {
            return at;
        }
        boolean twoDots = at + 1 < path.length() && path.charAt(at + 1) == DOT;
        int after = at + (twoDots ? 2 : 1);
        boolean wholeSegment = after >= path.length() || path.charAt(after) == REBOLS_SEPARATOR;
        if (!wholeSegment) {
            return at;
        }
        if (twoDots) {
            backOutOneDirectory(built);
        }
        return after;
    }

    private void backOutOneDirectory(StringBuilder built) {
        int length = built.length() > 2 ? built.length() - 2 : 0;
        while (length > 0 && built.charAt(length) != separator) {
            length--;
        }
        built.setLength(length);
        built.append(separator);
    }

    public Optional<String> readLocalPath(String localPath) {
        StringBuilder built = new StringBuilder(localPath.length() + 1);
        boolean sawDrive = false;
        boolean afterSeparator = false;
        for (int at = 0; at < localPath.length(); at++) {
            char letter = localPath.charAt(at);
            if (this == WINDOWS && letter == DRIVE_MARK) {
                if (sawDrive || afterSeparator) {
                    return Optional.empty();
                }
                sawDrive = true;
                if (at + 1 < localPath.length() && isSeparator(localPath.charAt(at + 1))) {
                    at++;
                }
                built.append(REBOLS_SEPARATOR);
            } else if (isSeparator(letter)) {
                if (!afterSeparator) {
                    built.append(REBOLS_SEPARATOR);
                    afterSeparator = true;
                }
            } else {
                built.append(letter);
                afterSeparator = false;
            }
        }
        if (sawDrive) {
            built.insert(0, REBOLS_SEPARATOR);
        }
        return Optional.of(built.toString());
    }

    private static boolean isSeparator(char letter) {
        return letter == '/' || letter == '\\';
    }

    public record LocalPathStart(String written, int readFrom) {
    }
}
