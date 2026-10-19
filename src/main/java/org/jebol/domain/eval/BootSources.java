package org.jebol.domain.eval;

import java.util.List;

/**
 * The REBOL text a boot is built from: Rebol's own declarations of its errors,
 * datatypes, operators, port modes and functions.
 *
 * <p>Owned by the domain and filled from outside, so a boot never knows where
 * the text is kept.
 */
public interface BootSources {

    String errorCatalogue();

    String datatypeSpecs();

    String operatorTable();

    String modeTable();

    /** The actions, natives and generated natives, in the order they are declared. */
    List<String> functionDeclarations();

    /** Sources that declare nothing, so a boot has only what Java registers itself. */
    static BootSources none() {
        return new BootSources() {
            @Override
            public String errorCatalogue() {
                return "";
            }

            @Override
            public String datatypeSpecs() {
                return "";
            }

            @Override
            public String operatorTable() {
                return "";
            }

            @Override
            public String modeTable() {
                return "";
            }

            @Override
            public List<String> functionDeclarations() {
                return List.of();
            }
        };
    }
}
