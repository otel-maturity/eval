package io.otel.maturity.agent.install.quarkus;

import io.quarkiverse.langchain4j.runtime.aiservice.SystemMessageProvider;
import io.quarkiverse.langchain4j.skills.runtime.SkillsToolProvider;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.spi.CDI;

import java.util.Optional;

/**
 * Custom SystemMessageProvider for the Install Agent.
 *
 * The default {@code SkillsSystemMessageProvider} only emits the skill list — without the
 * "activate it first using the `activate_skill` tool" instruction recommended by upstream
 * LangChain4j, the LLM often skips loading the skill body and produces a placeholder
 * INSTALL-PLAN.md without actually running the install. This provider combines the agent's
 * role description, the skills catalogue, and the activation directive into one system
 * message so the LLM reliably calls {@code activate_skill} before responding.
 */
@ApplicationScoped
public class InstallSystemMessageProvider implements SystemMessageProvider {

    private static final String AGENT_ROLE = """
            # Install Agent

            You are the **Install Agent** for the OpenTelemetry Maturity Evaluation pipeline.

            Your responsibility is to research, install, and configure a CNCF project in an \
            evaluation cluster so that its telemetry can be collected and assessed. You use the \
            `install-cncf-project` skill to accomplish this.

            INSTALL the CNCF project:
               - If the prompt lists an INSTALL-PLAN.md from a previous run, read
                 it with FileSystemTools and use it to install the project directly.
                 Skip the research phase of the "install-cncf-project" skill.
               - If no INSTALL-PLAN.md is available, run the full
                 "install-cncf-project" skill to research and install the project.

            When using a skill or a tool always notify the user about the action
            by sending regular messages with the progress of the installation.

            The installation must finish with telemetry flowing to the collector.

            The agent must create an INSTALL-PLAN.md file documenting the steps used to install the project.

            When the installation is finished, a message to the user about the
            steps that were taken must be sent as the last message.
            Use ++++ as a separator.
            """;

    @Override
    public Optional<String> getSystemMessage(Object memoryId) {
        Instance<SkillsToolProvider> skillsToolProvider = CDI.current().select(SkillsToolProvider.class);
        StringBuilder sb = new StringBuilder(AGENT_ROLE);
        if (skillsToolProvider.isResolvable()) {
            String skillsList = skillsToolProvider.get().getSkills().formatAvailableSkills();
            sb.append("\n\nYou have access to the following skills:\n")
              .append(skillsList)
              .append("\n\nWhen the user's request relates to one of these skills, ")
              .append("**activate it first using the `activate_skill` tool** before proceeding. ")
              .append("Do not attempt to answer or produce deliverables without first loading the skill's instructions.\n");
        }
        return Optional.of(sb.toString());
    }
}
