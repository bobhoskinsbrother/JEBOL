package org.jebol.domain.eval;

import java.util.Optional;

/**
 * The modules bundled with this build, read from it rather than fetched.
 *
 * <p>What the BUNDLED scheme reads through. IMPORT's last resort is {@code
 * select system/modules name}, and DOWNLOAD-EXTENSION does {@code content: read
 * source} with what it finds -- an ordinary READ of whatever the url names. So
 * an address naming this scheme turns the fetch into a read of the build, and
 * neither IMPORT nor DOWNLOAD-EXTENSION has to know the difference.
 *
 * <p>A port the domain owns, like {@link FilePort} and {@link NetworkPort},
 * because the modules live wherever the host put them and the interpreter must
 * not know where that is. Unlike those two it needs no grant: nothing is
 * reached, and a build cannot be asked for something it is not carrying.
 */
public interface BundledModules {

    /**
     * The source of the module of that name, or empty when none is bundled.
     *
     * <p>Empty rather than a thrown failure, because "this build has not got
     * that one" is a true answer the boundary turns into the same refusal a
     * missing file gets.
     */
    Optional<byte[]> sourceOf(String name);

    /** A build bundling nothing, which is what an interpreter starts with. */
    static BundledModules none() {
        return name -> Optional.empty();
    }
}
