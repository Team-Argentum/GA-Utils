package dev.team_argentum.ga_utils.core;

import java.util.List;
import java.util.Map;

public interface Processor {
    String id();

    TransformResult process(List<SourceFile> inputs, Map<String, String> options);
}
