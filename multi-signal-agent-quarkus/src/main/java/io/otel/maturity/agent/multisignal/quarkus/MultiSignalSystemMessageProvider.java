package io.otel.maturity.agent.multisignal.quarkus;

import io.quarkiverse.langchain4j.runtime.aiservice.SystemMessageProvider;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.ConfigProvider;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * The previous version of this class relied on field-injected {@code @ConfigProperty}
 * which is silently null when Quarkus-LangChain4j instantiates the provider outside
 * the CDI lookup path. That left {@code appendSkillBodies} as a no-op, producing a
 * system message containing only {@code AGENT_ROLE}. Dimensions 1/2/3/4/6/7 still
 * worked because their LLMs autonomously explored {@code .agents/skills/} and
 * located their SKILL.md at runtime; dimension 5's LLM did not, so it never read
 * the skill, never learned the expected output path, and wrote its evaluation to
 * {@code /tmp/evaluation_results.json}. To make this deterministic we now read
 * the skill body up front via {@link ConfigProvider} (which works regardless of
 * how the bean is instantiated) plus a hardcoded fallback to the container path,
 * and we make the output filename explicit in the agent role.
 */
@ApplicationScoped
public class MultiSignalSystemMessageProvider implements SystemMessageProvider {

    private static final List<String> FALLBACK_SKILL_PATHS = List.of(
            "/app/.agents/skills",
            ".agents/skills");

    private static final String AGENT_ROLE = """
            # Multi-Signal Observability Agent

            You are the **Multi-Signal Observability Agent** for the OpenTelemetry Maturity Evaluation pipeline.

            Your responsibility is to evaluate Dimension 5 (Multi-Signal Observability) of the OpenTelemetry Support
            Maturity Model for the given CNCF project. Follow the skill instructions below to perform the evaluation.

            ## Output

            Write your evaluation to `.otel-eval/<project-name>/dim-5-multi-signal.md`
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
            System.err.println("[MultiSignalSystemMessageProvider] No SKILL.md found in " +
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
