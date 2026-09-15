package dev.team_argentum.ga_utils.struct.ir;

import java.util.List;

public final class StructIr {
    public final String packageName;
    public final String name;
    public final String backendName;
    public final List<FieldIr> fields;
    public final List<MethodIr> methods;

    public StructIr(String packageName, String name, String backendName, List<FieldIr> fields, List<MethodIr> methods) {
        this.packageName = packageName;
        this.name = name;
        this.backendName = backendName;
        this.fields = List.copyOf(fields);
        this.methods = List.copyOf(methods);
    }

    public boolean hasField(String fieldName) {
        return fields.stream().anyMatch(f -> f.name.equals(fieldName));
    }

    public FieldIr field(String fieldName) {
        return fields.stream().filter(f -> f.name.equals(fieldName)).findFirst().orElse(null);
    }

    public MethodIr method(String methodName) {
        return methods.stream().filter(m -> m.name().equals(methodName)).findFirst().orElse(null);
    }
}
