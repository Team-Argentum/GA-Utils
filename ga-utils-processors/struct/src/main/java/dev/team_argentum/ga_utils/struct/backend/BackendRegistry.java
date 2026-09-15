package dev.team_argentum.ga_utils.struct.backend;

import dev.team_argentum.ga_utils.struct.ir.StructIr;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public final class BackendRegistry {
    private static final Map<String, StructBackend> BACKENDS = new LinkedHashMap<>();

    static {
        register(new FlattenBackend());
        register(new SoaBackend());
        register(new SsaBackend());
    }

    public static void register(StructBackend backend) {
        BACKENDS.put(backend.name(), backend);
    }

    public static StructBackend byName(String name) {
        StructBackend b = BACKENDS.get(name);
        if (b == null) {
            throw new IllegalArgumentException("unknown backend '" + name + "', available: " + names());
        }
        return b;
    }

    public static Set<String> names() {
        return Set.copyOf(BACKENDS.keySet());
    }
}
