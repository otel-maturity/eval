package io.otel.maturity.agent.evaluate.quarkus;

import io.quarkiverse.langchain4j.runtime.aiservice.SystemMessageProvider;
import io.quarkiverse.langchain4j.skills.runtime.SkillsToolProvider;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.spi.CDI;

import java.util.Optional;

@ApplicationScoped
public class EvaluateSystemMessageProvider implements SystemMessageProvider {

    private static final String AGENT_ROLE = """
            # Evaluate Agent (Orchestrator)

            You are the **Evaluate Agent** for the OpenTelemetry Maturity Evaluation pipeline.

            When asked to evaluate a project you must:

            1. **Gather cross-cutting context** by activating the `evaluate-otel-maturity` skill,
               passing the project name and version tag as arguments
               (e.g. "evaluate-otel-maturity <project-name> <version>").
               The skill performs Phase 1 (telemetry evidence) and Phase 2 (documentation
               evidence) and produces the project overview, telemetry overview, and
               installation context summary sections.
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
