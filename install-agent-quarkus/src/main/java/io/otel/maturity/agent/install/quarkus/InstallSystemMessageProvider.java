package io.otel.maturity.agent.install.quarkus;

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
public class InstallSystemMessageProvider implements SystemMessageProvider {

    private static final List<String> FALLBACK_SKILL_PATHS = List.of(
            "/app/.agents/skills",
            ".agents/skills");

    private static final String AGENT_ROLE = """
            # Install Agent

            You are the **Install Agent** for the OpenTelemetry Maturity Evaluation pipeline.

            Your responsibility is to research, install, and configure a CNCF project in an \
            evaluation cluster so that its telemetry can be collected and assessed.

            INSTALL the CNCF project:
               - If the prompt lists an INSTALL-PLAN.md from a previous run, read
                 it with FileSystemTools and use it to install the project directly.
                 Skip the research phase described in the skill instructions below.
               - If no INSTALL-PLAN.md is available, follow the full procedure in the
                 skill instructions below to research and install the project.

            When using a tool always notify the user about the action by sending regular
            messages with the progress of the installation.

            The installation must finish with telemetry flowing to the collector.

            The agent must create an INSTALL-PLAN.md file documenting the steps used to install the project.

            When the installation is finished, a message to the user about the
            steps that were taken must be sent as the last message.
            Use ++++ as a separator.
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
            System.err.println("[InstallSystemMessageProvider] No SKILL.md found in " +
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
