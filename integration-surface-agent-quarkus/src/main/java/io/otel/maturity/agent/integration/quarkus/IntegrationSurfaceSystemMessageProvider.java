package io.otel.maturity.agent.integration.quarkus;

import io.quarkiverse.langchain4j.runtime.aiservice.SystemMessageProvider;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.Stream;

@ApplicationScoped
public class IntegrationSurfaceSystemMessageProvider implements SystemMessageProvider {

    private static final String AGENT_ROLE = """
            # Integration Surface Agent

            You are the **Integration Surface Agent** for the OpenTelemetry Maturity Evaluation pipeline.

            Your responsibility is to evaluate Dimension 1 (Integration Surface) of the OpenTelemetry Support
            Maturity Model for the given CNCF project. Follow the skill instructions below to perform the evaluation.

            When using a tool always notify the user about the action
            by sending regular messages with the progress of the evaluation.

            When the evaluation is finished, a message to the user about the
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
