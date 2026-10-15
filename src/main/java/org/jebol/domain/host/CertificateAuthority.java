package org.jebol.domain.host;

import java.util.List;

/**
 * Who decides whether a server's certificates are to be trusted.
 *
 * <p>Given the chain exactly as the server sent it, its first certificate
 * first, the verdict is either the names that first certificate vouches for
 * or the reason it is not trusted. Whether those names cover the server the
 * script asked for is not the authority's question: the interpreter answers
 * it the same way on every host.
 *
 * <p>Specified in {@code spec/embed.allium}.
 */
public interface CertificateAuthority {

    ChainVerdict judge(List<byte[]> chain);
}
