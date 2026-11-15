package org.jebol.domain.eval.compression.zlibng;

import static org.jebol.domain.eval.compression.zlibng.ZlibNgDeflateState.STD_MIN_MATCH;

/** The shape below is zlib-ng's match_tpl.h, converted mechanically: see docs/zlib-ng-port.md, and do not tidy it. */
final class ZlibNgMatches {

    private static final int EARLY_EXIT_TRIGGER_LEVEL = 5;
    private static final int COMPARED = 256;

    private ZlibNgMatches() {
    }

    static int compare256(byte[] window, int src0, int src1) {
        int limit = Math.min(COMPARED, window.length - Math.max(src0, src1));
        int len = 0;
        while (len < limit && window[src0 + len] == window[src1 + len]) {
            len++;
        }
        return len;
    }

    static int compare256Rle(byte[] window, int src0, int src1) {
        int limit = Math.min(COMPARED, window.length - src1);
        byte repeated = window[src0];
        int len = 0;
        while (len < limit && window[src1 + len] == repeated) {
            len++;
        }
        return len;
    }

    private static int offsetFor(int bestLen) {
        int offset = bestLen - 1;
        if (bestLen >= Integer.BYTES) {
            offset -= 2;
            if (bestLen >= Long.BYTES) {
                offset -= 4;
            }
        }
        return offset;
    }

    private static int bytesComparedFor(int bestLen) {
        if (bestLen < Integer.BYTES) {
            return 2;
        }
        return bestLen >= Long.BYTES ? Long.BYTES : Integer.BYTES;
    }

    private static boolean isSame(byte[] window, int a, int b, int count) {
        for (int each = 0; each < count; each++) {
            if (window[a + each] != window[b + each]) {
                return false;
            }
        }
        return true;
    }

    static int longestMatch(ZlibNgDeflateState s, int curMatch) {
        int strstart = s.strstart;
        int wmask = s.wMask;
        byte[] window = s.window;
        int scan = strstart;
        int[] prev = s.prev;
        int lookahead = s.lookahead;
        int bestLen = s.prevLength != 0 ? s.prevLength : STD_MIN_MATCH - 1;
        int offset = offsetFor(bestLen);
        int mbaseEnd = offset;
        int chainLength = s.maxChainLength;
        if (bestLen >= s.goodMatch) {
            chainLength >>>= 2;
        }
        int niceMatch = s.niceMatch;
        int limit = strstart > s.maxDist() ? strstart - s.maxDist() : 0;
        boolean earlyExit = s.level < EARLY_EXIT_TRIGGER_LEVEL;
        for (;;) {
            if (curMatch >= strstart) {
                break;
            }
            int compared = bytesComparedFor(bestLen);
            for (;;) {
                if (isSame(window, mbaseEnd + curMatch, scan + offset, compared)
                        && isSame(window, curMatch, scan, compared)) {
                    break;
                }
                if (--chainLength != 0 && (curMatch = prev[curMatch & wmask]) > limit) {
                    continue;
                }
                return bestLen;
            }
            int len = compare256(window, scan + 2, curMatch + 2) + 2;
            if (len > bestLen) {
                s.matchStart = curMatch;
                if (len > lookahead) {
                    return lookahead;
                }
                bestLen = len;
                if (bestLen >= niceMatch) {
                    return bestLen;
                }
                offset = offsetFor(bestLen);
                mbaseEnd = offset;
            } else if (earlyExit) {
                break;
            }
            if (--chainLength != 0 && (curMatch = prev[curMatch & wmask]) > limit) {
                continue;
            }
            return bestLen;
        }
        return bestLen;
    }

    static int longestMatchSlow(ZlibNgDeflateState s, int curMatch) {
        int strstart = s.strstart;
        int wmask = s.wMask;
        byte[] window = s.window;
        int scan = strstart;
        int mbaseStart = 0;
        int[] prev = s.prev;
        int lookahead = s.lookahead;
        int matchOffset = 0;
        int bestLen = s.prevLength != 0 ? s.prevLength : STD_MIN_MATCH - 1;
        int offset = offsetFor(bestLen);
        int mbaseEnd = mbaseStart + offset;
        int chainLength = s.maxChainLength;
        if (bestLen >= s.goodMatch) {
            chainLength >>>= 2;
        }
        int niceMatch = s.niceMatch;
        int limit = strstart > s.maxDist() ? strstart - s.maxDist() : 0;
        int limitBase = limit;
        if (bestLen >= STD_MIN_MATCH) {
            int hash = s.updateHashForLevel(0, window[scan + 1]);
            hash = s.updateHashForLevel(hash, window[scan + 2]);
            for (int i = 3; i <= bestLen; i++) {
                hash = s.updateHashForLevel(hash, window[scan + i]);
                int pos = s.head[hash];
                if (pos < curMatch) {
                    matchOffset = (i - 2) & 0xFFFF;
                    curMatch = pos;
                }
            }
            limit = (limitBase + matchOffset) & 0xFFFF;
            if (curMatch <= limit) {
                return breakMatching(s, bestLen);
            }
            mbaseStart -= matchOffset;
            mbaseEnd -= matchOffset;
        }
        for (;;) {
            if (curMatch >= strstart) {
                break;
            }
            int compared = bytesComparedFor(bestLen);
            for (;;) {
                if (isSame(window, mbaseEnd + curMatch, scan + offset, compared)
                        && isSame(window, mbaseStart + curMatch, scan, compared)) {
                    break;
                }
                if (--chainLength != 0 && (curMatch = prev[curMatch & wmask]) > limit) {
                    continue;
                }
                return bestLen;
            }
            int len = compare256(window, scan + 2, mbaseStart + curMatch + 2) + 2;
            if (len > bestLen) {
                int matchStart = curMatch - matchOffset;
                s.matchStart = matchStart;
                if (len > lookahead) {
                    return lookahead;
                }
                bestLen = len;
                if (bestLen >= niceMatch) {
                    return bestLen;
                }
                offset = offsetFor(bestLen);
                if (len > STD_MIN_MATCH && matchStart + len < strstart) {
                    curMatch = (curMatch - matchOffset) & 0xFFFF;
                    matchOffset = 0;
                    int nextPos = curMatch;
                    for (int i = 0; i <= len - STD_MIN_MATCH; i++) {
                        int pos = prev[(curMatch + i) & wmask];
                        if (pos < nextPos) {
                            if (pos <= limitBase + i) {
                                return breakMatching(s, bestLen);
                            }
                            nextPos = pos;
                            matchOffset = i & 0xFFFF;
                        }
                    }
                    curMatch = nextPos;
                    int scanEndstr = scan + len - (STD_MIN_MATCH + 1);
                    int hash = s.updateHashForLevel(0, window[scanEndstr]);
                    hash = s.updateHashForLevel(hash, window[scanEndstr + 1]);
                    hash = s.updateHashForLevel(hash, window[scanEndstr + 2]);
                    int pos = s.head[hash];
                    if (pos < curMatch) {
                        matchOffset = (len - (STD_MIN_MATCH + 1)) & 0xFFFF;
                        if (pos <= limitBase + matchOffset) {
                            return breakMatching(s, bestLen);
                        }
                        curMatch = pos;
                    }
                    limit = (limitBase + matchOffset) & 0xFFFF;
                    mbaseStart = -matchOffset;
                    mbaseEnd = mbaseStart + offset;
                    continue;
                }
                mbaseEnd = mbaseStart + offset;
            }
            if (--chainLength != 0 && (curMatch = prev[curMatch & wmask]) > limit) {
                continue;
            }
            return bestLen;
        }
        return bestLen;
    }

    private static int breakMatching(ZlibNgDeflateState s, int bestLen) {
        if (bestLen < s.lookahead) {
            return bestLen;
        }
        return s.lookahead;
    }
}
