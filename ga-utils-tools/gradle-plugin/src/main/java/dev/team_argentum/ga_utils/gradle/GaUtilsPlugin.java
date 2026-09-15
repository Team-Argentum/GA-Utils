package dev.team_argentum.ga_utils.gradle;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.Directory;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;

import java.util.Map;

public class GaUtilsPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        project.getPluginManager().apply("java");

        GaUtilsExtension ext = project.getExtensions().create("gautils", GaUtilsExtension.class);
        ext.getBackend().convention("flatten");
        ext.getSrcDir().convention("src/main/java");
        ext.getTestSrcDir().convention("src/test/java");
        ext.getOutputDir().convention("gautils/generated-src");
        ext.getToolProject().convention(":ga-utils-processors:struct");
        ext.getRewriteTestSourceSet().convention(true);

        Configuration tool = project.getConfigurations().create("gautilsTool");
        tool.setCanBeConsumed(false);
        tool.setCanBeResolved(true);

        project.afterEvaluate(p -> {
            String toolProject = ext.getToolProject().get();
            if (!toolProject.isEmpty()) {
                tool.getDependencies().add(p.getDependencies().project(Map.of("path", toolProject)));
            }

            SourceSetContainer sourceSets = p.getExtensions().getByType(SourceSetContainer.class);
            wireSourceSet(p, ext, tool, sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME),
                    "gautilsExpand", ext.getSrcDir().get(), "compileJava");
            if (ext.getRewriteTestSourceSet().get()) {
                wireSourceSet(p, ext, tool, sourceSets.getByName(SourceSet.TEST_SOURCE_SET_NAME),
                        "gautilsExpandTest", ext.getTestSrcDir().get(), "compileTestJava");
            }
        });
    }

    private void wireSourceSet(Project p, GaUtilsExtension ext, Configuration tool,
                               SourceSet sourceSet, String taskName, String srcDir, String compileTaskName) {
        Provider<Directory> outDir = p.getLayout().getBuildDirectory()
                .dir(ext.getOutputDir().get() + "/" + sourceSet.getName());
        var srcRoot = p.file(srcDir);

        var expand = p.getTasks().register(taskName, JavaExec.class, t -> {
            t.setGroup("build");
            t.setDescription("Expands @Struct code into DOD form before compilation (" + sourceSet.getName() + ").");
            t.setClasspath(tool);
            t.getMainClass().set("dev.team_argentum.ga_utils.struct.CliMain");
            t.args("--backend=" + ext.getBackend().get(),
                    "--out=" + outDir.get().getAsFile().getAbsolutePath(),
                    srcRoot.getAbsolutePath());
            t.getInputs().files(p.fileTree(srcRoot));
            t.getInputs().property("backend", ext.getBackend());
            t.getOutputs().dir(outDir);
            t.doFirst(task -> p.delete(outDir.get().getAsFile()));
        });

        p.getTasks().withType(org.gradle.api.tasks.SourceTask.class).named(compileTaskName).configure(t -> {
            t.dependsOn(expand);
            t.setSource(p.fileTree(outDir));
        });
    }
}
