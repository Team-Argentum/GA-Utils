package dev.team_argentum.ga_utils.struct.backend;

import com.github.javaparser.ast.CompilationUnit;
import dev.team_argentum.ga_utils.core.ProblemCollector;
import dev.team_argentum.ga_utils.core.SourceFile;
import dev.team_argentum.ga_utils.struct.ir.StructIr;

import java.util.List;
import java.util.Map;

public interface StructBackend {
    String name();

    List<SourceFile> generateStructSources(List<StructIr> structs, Map<String, String> options, ProblemCollector problems);

    void rewriteCallers(CompilationUnit cu, Map<String, StructIr> catalog, ProblemCollector problems, String path);
}
