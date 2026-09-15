package dev.team_argentum.ga_utils.struct;

import dev.team_argentum.ga_utils.core.ProblemCollector;
import dev.team_argentum.ga_utils.struct.frontend.ParsedFile;
import dev.team_argentum.ga_utils.struct.ir.FieldIr;
import dev.team_argentum.ga_utils.struct.ir.MethodIr;
import dev.team_argentum.ga_utils.struct.ir.StructIr;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class StructCatalogs {

    private StructCatalogs() {
    }

    public static Map<String, StructIr> build(List<ParsedFile> parsed, ProblemCollector problems) {
        Map<String, StructIr> catalog = new LinkedHashMap<>();
        for (ParsedFile pf : parsed) {
            for (StructIr s : pf.structs()) {
                if (catalog.putIfAbsent(s.name, s) != null) {
                    problems.error(pf.path(), 0, "duplicate @Struct name: " + s.name);
                }
            }
        }
        for (ParsedFile pf : parsed) {
            for (StructIr s : pf.structs()) {
                for (FieldIr f : s.fields) {
                    if (catalog.containsKey(baseTypeName(f.type))) {
                        problems.error(pf.path(), 0, "nested @Struct fields are not supported: "
                                + s.name + "." + f.name + " of type " + f.type);
                    }
                }
                for (MethodIr m : s.methods) {
                    if (catalog.containsKey(baseTypeName(m.returnType()))) {
                        problems.error(pf.path(), 0, "returning @Struct values is not supported: "
                                + s.name + "." + m.name());
                    }
                }
            }
        }
        return catalog;
    }

    private static String baseTypeName(String type) {
        return type.replace("[]", "").trim();
    }
}
