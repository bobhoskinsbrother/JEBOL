package org.jebol.extension.sqlite;

import java.util.Map;

final class ResultCodes {

    private static final int PRIMARY = 0xff;
    private static final String UNKNOWN = "unknown error";

    private static final Map<Integer, String> DESCRIBED = Map.ofEntries(
            Map.entry(0, "not an error"),
            Map.entry(1, "SQL logic error"),
            Map.entry(3, "access permission denied"),
            Map.entry(4, "query aborted"),
            Map.entry(5, "database is locked"),
            Map.entry(6, "database table is locked"),
            Map.entry(7, "out of memory"),
            Map.entry(8, "attempt to write a readonly database"),
            Map.entry(9, "interrupted"),
            Map.entry(10, "disk I/O error"),
            Map.entry(11, "database disk image is malformed"),
            Map.entry(12, "unknown operation"),
            Map.entry(13, "database or disk is full"),
            Map.entry(14, "unable to open database file"),
            Map.entry(15, "locking protocol"),
            Map.entry(17, "database schema has changed"),
            Map.entry(18, "string or blob too big"),
            Map.entry(19, "constraint failed"),
            Map.entry(20, "datatype mismatch"),
            Map.entry(21, "bad parameter or other API misuse"),
            Map.entry(23, "authorization denied"),
            Map.entry(25, "column index out of range"),
            Map.entry(26, "file is not a database"),
            Map.entry(27, "notification message"),
            Map.entry(28, "warning message"),
            Map.entry(100, "another row available"),
            Map.entry(101, "no more rows available"),
            Map.entry(516, "abort due to ROLLBACK"));

    private ResultCodes() {
    }

    static String describe(int resultCode) {
        String exact = DESCRIBED.get(resultCode);
        if (exact != null) {
            return exact;
        }
        return DESCRIBED.getOrDefault(resultCode & PRIMARY, UNKNOWN);
    }
}
