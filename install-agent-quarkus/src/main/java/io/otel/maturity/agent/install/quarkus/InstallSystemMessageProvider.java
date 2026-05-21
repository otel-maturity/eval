package io.otel.maturity.agent.install.quarkus;

import io.quarkiverse.langchain4j.runtime.aiservice.SystemMessageProvider;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * The Quarkus skills extension registers an {@code activate_skill} tool through a
 * {@code SkillsToolProvider} CDI bean — but {@code @RegisterAiService(tools = {...})}
 * silently overrides any auto-discovered ToolProvider beans (confirmed by
 * {@code ExplicitToolsWhenBeanToolProviderExistsTest} in the Quarkus LangChain4j repo),
 * so the LLM never receives the activation tool and resorts to running
 * {@code runShell("Activating skill...")} as a stand-in.
 *
 * Workaround: read each SKILL.md file from {@code quarkus.langchain4j.skills.directories}
 * at request time and inline the full body into the system message. The LLM then has the
 * complete skill instructions up-front and executes them via FileSystemTools/ShellTools
 * without needing the activation tool at all.
 */
@ApplicationScoped
public class InstallSystemMessageProvider implements SystemMessageProvider {

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
