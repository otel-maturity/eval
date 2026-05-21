package io.otel.maturity.agent.progress.quarkus;

import io.quarkiverse.langchain4j.runtime.aiservice.SystemMessageProvider;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.Stream;

@ApplicationScoped
public class ProgressSystemMessageProvider implements SystemMessageProvider {

    private static final String AGENT_ROLE = """
            # Progress Agent

            You are the **Progress Agent** for the OpenTelemetry Maturity Evaluation pipeline.

            Your responsibility is to track the evolution of a CNCF project's OpenTelemetry \
            maturity by querying GitHub issues and pull requests listed in the project's \
            TRACKING.md file. You then produce or update an EVOLUTION.md file that records \
            this progress in chronological order (newest first).

            The prompt will provide the project name and, when a TRACKING.md exists, its path. \
            If no TRACKING.md is found, no GitHub queries are needed — report this to the user \
            and stop.

            TRACK the project's progress following ALL steps in the skill instructions below. \
            The skill produces ONE file:
              1. EVOLUTION.md — list of GitHub items ordered by date (newest first)

            When using a tool always notify the user about the action \
            by sending regular messages with the progress of the tracking.

            When the EVOLUTION.md is written (or updated), send a final message to the user \
            summarising the steps taken. Use ++++ as a separator.
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
