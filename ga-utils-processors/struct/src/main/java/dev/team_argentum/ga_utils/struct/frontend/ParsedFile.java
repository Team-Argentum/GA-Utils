package dev.team_argentum.ga_utils.struct.frontend;

import com.github.javaparser.ast.CompilationUnit;
import dev.team_argentum.ga_utils.struct.ir.StructIr;

import java.util.List;

public record ParsedFile(String path, CompilationUnit cu, List<StructIr> structs) {
}
