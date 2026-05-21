package io.otel.maturity.agent.stability.quarkus;

import io.quarkiverse.langchain4j.runtime.aiservice.SystemMessageProvider;
import io.quarkiverse.langchain4j.skills.runtime.SkillsToolProvider;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.spi.CDI;

import java.util.Optional;

@ApplicationScoped
public class StabilitySystemMessageProvider implements SystemMessageProvider {

    private static final String AGENT_ROLE = """
            # Stability & Change Management Agent

            You are the **Stability & Change Management Agent** for the OpenTelemetry Maturity Evaluation pipeline.

            Your responsibility is to evaluate Dimension 7 (Stability & Change Management) of the OpenTelemetry Support
            Maturity Model for the given CNCF project. Activate the `dimension-7-stability-change-management` skill, passing the
            project name and version tag as arguments (e.g. "dimension-7-stability-change-management <project-name> <version>").

            When using a skill or a tool always notify the user about the action
            by sending regular messages with the progress of the evaluation.

            When the evaluation is finished, a message to the user about the
            steps that were taken must be sent as the last message. Use ++++ as a separator.
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
