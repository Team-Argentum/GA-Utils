package dev.team_argentum.ga_utils.gradle;

import org.gradle.api.provider.Property;

public interface GaUtilsExtension {

    Property<String> getBackend();

    Property<String> getSrcDir();

    Property<String> getTestSrcDir();

    Property<String> getOutputDir();

    Property<String> getToolProject();

    Property<Boolean> getRewriteTestSourceSet();
}
