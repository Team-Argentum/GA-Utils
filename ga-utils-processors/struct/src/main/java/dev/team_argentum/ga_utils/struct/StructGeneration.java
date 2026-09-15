package dev.team_argentum.ga_utils.struct;

import dev.team_argentum.ga_utils.core.ProblemCollector;
import dev.team_argentum.ga_utils.core.SourceFile;
import dev.team_argentum.ga_utils.struct.backend.BackendRegistry;
import dev.team_argentum.ga_utils.struct.backend.StructBackend;
import dev.team_argentum.ga_utils.struct.ir.StructIr;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class StructGeneration {

    private StructGeneration() {
    }

    public static List<SourceFile> generate(List<StructIr> structs, Map<String, String> options,
                                            String defaultBackend, ProblemCollector problems) {
        Map<String, List<StructIr>> byBackend = new LinkedHashMap<>();
        for (StructIr s : structs) {
            String name = s.backendName != null ? s.backendName : defaultBackend;
            byBackend.computeIfAbsent(name, k -> new ArrayList<>()).add(s);
        }
        List<SourceFile> out = new ArrayList<>();
        for (Map.Entry<String, List<StructIr>> e : byBackend.entrySet()) {
            StructBackend backend;
            try {
                backend = BackendRegistry.byName(e.getKey());
            } catch (IllegalArgumentException ex) {
                List<String> names = e.getValue().stream().map(s -> s.name).toList();
                problems.error("<struct>", 0, "unknown backend '" + e.getKey() + "' for @Struct: "
                        + String.join(", ", names) + " (available: " + BackendRegistry.names() + ")");
                continue;
            }
            out.addAll(backend.generateStructSources(e.getValue(), options, problems));
        }
        return out;
    }
}
