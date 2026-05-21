package io.otel.maturity.agent.progress.quarkus;

import io.quarkiverse.langchain4j.runtime.aiservice.SystemMessageProvider;
import io.quarkiverse.langchain4j.skills.runtime.SkillsToolProvider;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.spi.CDI;

import java.util.Optional;

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

            TRACK the project's progress using the "track-project-progress" skill. \
            Follow ALL steps in the skill (SKILL.md). The skill produces ONE file:
              1. EVOLUTION.md — list of GitHub items ordered by date (newest first)

            When using a skill or a tool always notify the user about the action \
            by sending regular messages with the progress of the tracking.

            When the EVOLUTION.md is written (or updated), send a final message to the user \
            summarising the steps taken. Use ++++ as a separator.
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
