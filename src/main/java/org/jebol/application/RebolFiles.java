package org.jebol.application;

import org.jebol.domain.host.FilePort;

final class RebolFiles {

    private final FilePort files;

    RebolFiles(FilePort files) {
        this.files = files;
    }

    RebolFile errors() {
        return jebolDir("errors.reb");
    }

    RebolFile typespec() {
        return jebolDir("typespec.reb");
    }

    RebolFile ops() {
        return jebolDir("ops.reb");
    }

    RebolFile modes() {
        return jebolDir("modes.reb");
    }

    RebolFile actions() {
        return jebolDir("actions.reb");
    }

    RebolFile natives() {
        return jebolDir("natives.reb");
    }

    RebolFile genNatives() {
        return jebolDir("gen-natives.reb");
    }

    RebolFile bootSymbolsTxt() {
        return jebolDir("boot-symbols.txt");
    }

    RebolFile prelude() {
        return jebolDir("prelude.reb");
    }

    RebolFile sysobj() {
        return jebolDir("sysobj.reb");
    }

    RebolFile systemObject() {
        return bootDir("system-object.reb");
    }

    RebolFile schemes() {
        return bootDir("schemes.reb");
    }

    RebolFile qoiCodec() {
        return bootDir("qoi-codec.reb");
    }

    RebolFile handOverToStart() {
        return bootDir("hand-over-to-start.reb");
    }

    RebolFile modules() {
        return bootDir("modules.reb");
    }

    RebolFile modulesDirectory() {
        return bootDir("modules-directory.reb");
    }

    RebolFile mezzBanner() {
        return mezzDir("mezz-banner.reb");
    }

    RebolFile protMysql() {
        return mezzDir("prot-mysql.reb");
    }

    RebolFile baseFuncs() {
        return mezzDir("base-funcs.reb");
    }

    RebolFile orderTxt() {
        return mezzDir("ORDER.txt");
    }

    RebolFile mezzDir(String name) {
        return jebolDir("mezz/" + name);
    }

    RebolFile modulesDir(String name) {
        return jebolDir("modules/" + name);
    }

    private RebolFile bootDir(String name) {
        return jebolDir("boot/" + name);
    }

    private RebolFile jebolDir(String name) {
        return new RebolFile("/org/jebol/" + name, files);
    }
}
