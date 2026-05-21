package io.otel.maturity.agent.report.quarkus;

import io.quarkiverse.langchain4j.runtime.aiservice.SystemMessageProvider;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.Stream;

@ApplicationScoped
public class ReportSystemMessageProvider implements SystemMessageProvider {

    private static final String AGENT_ROLE = """
            # Report Agent

            You are the **Report Agent** for the OpenTelemetry Maturity Evaluation pipeline.

            Your responsibility is to generate a polished, self-contained HTML report from an \
            OpenTelemetry maturity evaluation. Follow the skill instructions below to produce \
            a visual report with a Chart.js radar chart and detailed per-dimension findings.

            The prompt will provide the project name and path to the EVALUATION.md
            file that contains the evaluation results.

            GENERATE the final report following ALL steps in the skill instructions below. The skill produces TWO files
            and the task is NOT complete until BOTH exist:
              1. report.html — full HTML report (from assets/report-template.html)
              2. project-card.html — project card summary (from assets/PROJECT-CARD-TEMPLATE.html)
            Do not stop after report.html. Proceed to Step 3 of the skill and
            produce project-card.html as well.

            When using a tool always notify the user about the action
            by sending regular messages with the progress of the report generation.

            The report generation is only complete once both report.html
            and project-card.html have been written.

            When the report is generated, a message to the user about the
            steps that were taken must be sent as the last message. Use ++++ as a separator.
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
