package dev.team_argentum.ga_utils.struct.ir;

public final class FieldIr {
    public final String name;
    public final String type;
    public final boolean scalar;

    public FieldIr(String name, String type) {
        this.name = name;
        this.type = type;
        this.scalar = !type.contains("[]");
    }
}
