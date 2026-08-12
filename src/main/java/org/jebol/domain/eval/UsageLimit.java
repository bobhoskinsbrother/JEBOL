package org.jebol.domain.eval;

/**
 * A limit a script can ask to be held to.
 *
 * <p>Rebol's two, from LIMIT-USAGE's own help text: "eval (count) or memory
 * (bytes)". SECURE is what calls it there, and a policy decides whether
 * exceeding one does anything.
 *
 * <p>Nothing enforces either, and nothing can reach the native that records
 * them. Both of those are Rebol's arrangement: every security policy defaults to
 * ALLOW in {@code boot/sysobj.reb}, and {@code mezz-secure.reb} ends the boot with
 * {@code unset in lib 'limit-usage}. Kept here because it is what the C writes
 * -- {@code Eval_Limit} and {@code PG_Mem_Limit} -- and because SECURE is the
 * caller that would need it if its own binding to the native ever survived.
 */
public enum UsageLimit {

    /** How many values the script may evaluate. {@code Eval_Limit} in the C. */
    EVALUATIONS,

    /** How many bytes it may hold. {@code PG_Mem_Limit} in the C. */
    MEMORY_BYTES
}
