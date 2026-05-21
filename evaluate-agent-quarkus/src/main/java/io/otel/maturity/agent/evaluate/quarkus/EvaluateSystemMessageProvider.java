package io.otel.maturity.agent.evaluate.quarkus;

import io.quarkiverse.langchain4j.runtime.aiservice.SystemMessageProvider;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.Stream;

@ApplicationScoped
public class EvaluateSystemMessageProvider implements SystemMessageProvider {

    private static final String AGENT_ROLE = """
            # Evaluate Agent (Orchestrator)

            You are the **Evaluate Agent** for the OpenTelemetry Maturity Evaluation pipeline.

            When asked to evaluate a project you must:

            1. **Gather cross-cutting context** by following the skill instructions below to
               collect telemetry evidence and documentation findings. The skill performs
               Phase 1 (telemetry evidence) and Phase 2 (documentation evidence) and produces
               the project overview, telemetry overview, and installation context summary
               sections.
            2. **Assemble the complete EVALUATION.md** combining the context from the skill
               with the seven dimension results provided in the user message:
               - Project overview (metadata header)
               - Summary table with all 7 dimensions and their levels
               - Telemetry overview (signals observed, resource attributes)
               - Installation context summary
               - All 7 dimension evaluation sections verbatim
               - Key findings: top 3 strengths, top 3 areas for improvement, notable observations
               - Methodology notes
            3. Write EVALUATION.md and EVALUATION_v{version}.md using the exact absolute
               paths given in the user message.

            If a previous EVALUATION.md exists at the given path, read it first for context.

            When the evaluation is finished, send a summary message with the summary table
            and top findings. Use ++++ as a separator before the final message.
            """;

    @ConfigProperty(name = "quarkus.langchain4j.skills.directories")
    String skillsDirectories;

    @Override
    public Optional<String> getSystemMessage(Object memoryId) {
        StringBuilder sb = new StringBuilder(AGENT_ROLE);
        appendSkillBodies(sb);
        return Optional.of(sb.toString());
    }

    private void appendSkillBodies(StringBuilder sb) {
        if (skillsDirectories == null || skillsDirectories.isBlank()) return;
        for (String dirSpec : skillsDirectories.split(",")) {
            String trimmed = dirSpec.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("classpath:")) continue;
            Path root = Path.of(trimmed);
            if (!Files.isDirectory(root)) continue;
            try (Stream<Path> entries = Files.list(root)) {
                entries.filter(Files::isDirectory).sorted().forEach(skillDir -> {
                    Path skillFile = skillDir.resolve("SKILL.md");
                    if (Files.isRegularFile(skillFile)) {
                        try {
                            sb.append("\n\n---\n\n# Skill: ")
                              .append(skillDir.getFileName())
                              .append("\n\n")
                              .append(Files.readString(skillFile));
                        } catch (IOException ignored) {
                        }
                    }
                });
            } catch (IOException ignored) {
            }
        }
    }
}
