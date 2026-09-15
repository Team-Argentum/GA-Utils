package dev.team_argentum.ga_utils.struct.ir;

import java.util.List;

public record MethodIr(String name, String returnType, List<ParamIr> params, String normalizedBody) {
}
