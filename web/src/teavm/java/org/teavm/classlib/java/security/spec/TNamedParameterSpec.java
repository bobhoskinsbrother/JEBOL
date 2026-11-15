package org.teavm.classlib.java.security.spec;

public class TNamedParameterSpec {
    public static final TNamedParameterSpec X25519 = new TNamedParameterSpec("X25519");
    public static final TNamedParameterSpec X448 = new TNamedParameterSpec("X448");
    private final String name;
    public TNamedParameterSpec(String name) { this.name = name; }
    public String getName() { return name; }
}
