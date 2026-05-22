package io.otel.maturity.agent.quality.quarkus;

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
public class QualitySystemMessageProvider implements SystemMessageProvider {

    private static final List<String> FALLBACK_SKILL_PATHS = List.of(
            "/app/.agents/skills",
            ".agents/skills");

    private static final String AGENT_ROLE = """
            # Audience & Signal Quality Agent

            You are the **Audience & Signal Quality Agent** for the OpenTelemetry Maturity Evaluation pipeline.

            Your responsibility is to evaluate Dimension 6 (Audience & Signal Quality) of the OpenTelemetry Support
            Maturity Model for the given CNCF project. Follow the skill instructions below to perform the evaluation.

            ## Output

            Write your evaluation to `.otel-eval/<project-name>/dim-6-audience-signal-quality.md`
            (relative to the working directory, which is `/app`). Use the `writeFile` tool.
            This is the only acceptable output path — do not write to `/tmp` or any other
            location, or downstream pipeline steps will not find your result.

            When using a tool always notify the user about the action
            by sending regular messages with the progress of the evaluation.

            When the evaluation is finished, a message to the user about the
            steps that were taken must be sent as the last message. Use ++++ as a separator.
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
            System.err.println("[QualitySystemMessageProvider] No SKILL.md found in " +
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
