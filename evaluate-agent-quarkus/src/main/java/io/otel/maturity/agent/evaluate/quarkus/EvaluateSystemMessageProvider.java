package io.otel.maturity.agent.evaluate.quarkus;

import io.quarkiverse.langchain4j.runtime.aiservice.SystemMessageProvider;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.ConfigProvider;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

@ApplicationScoped
public class EvaluateSystemMessageProvider implements SystemMessageProvider {

    private static final List<String> FALLBACK_SKILL_PATHS = List.of(
            "/app/.agents/skills",
            ".agents/skills");

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

    @Override
    public Optional<String> getSystemMessage(Object memoryId) {
        StringBuilder sb = new StringBuilder(AGENT_ROLE);
        appendSkillBodies(sb);
        return Optional.of(sb.toString());
    }

    private void appendSkillBodies(StringBuilder sb) {
        // @ConfigProperty injection is unreliable here because Quarkus-LangChain4j
        // may instantiate the SystemMessageProvider outside the CDI lookup path,
        // which leaves the field null and silently turns this into a no-op.
        // Use ConfigProvider directly so the value resolves either way, plus
        // hardcoded fallbacks for the container layout.
        String configured = ConfigProvider.getConfig()
                .getOptionalValue("quarkus.langchain4j.skills.directories", String.class)
                .orElse("");
        boolean appended = false;
        for (String dirSpec : configured.split(",")) {
            if (tryAppendFrom(sb, dirSpec)) appended = true;
        }
        if (!appended) {
            for (String fallback : FALLBACK_SKILL_PATHS) {
                if (tryAppendFrom(sb, fallback)) {
                    appended = true;
                    break;
                }
            }
        }
        if (!appended) {
            System.err.println("[EvaluateSystemMessageProvider] No SKILL.md found in " +
                    "configured=" + configured + " or fallbacks=" + FALLBACK_SKILL_PATHS);
        }
    }

    private boolean tryAppendFrom(StringBuilder sb, String dirSpec) {
        if (dirSpec == null) return false;
        String trimmed = dirSpec.trim();
        if (trimmed.isEmpty() || trimmed.startsWith("classpath:")) return false;
        Path root = Path.of(trimmed);
        if (!Files.isDirectory(root)) return false;
        boolean anyAppended = false;
        try (Stream<Path> entries = Files.list(root)) {
            for (Path skillDir : entries.filter(Files::isDirectory).sorted().toList()) {
                Path skillFile = skillDir.resolve("SKILL.md");
                if (!Files.isRegularFile(skillFile)) continue;
                try {
                    sb.append("\n\n---\n\n# Skill: ")
                      .append(skillDir.getFileName())
                      .append("\n\n")
                      .append(Files.readString(skillFile));
                    anyAppended = true;
                } catch (IOException ignored) {
                }
            }
        } catch (IOException ignored) {
        }
        return anyAppended;
    }
}
