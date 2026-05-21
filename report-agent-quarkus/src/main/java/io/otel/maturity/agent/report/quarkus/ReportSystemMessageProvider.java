package io.otel.maturity.agent.report.quarkus;

import io.quarkiverse.langchain4j.runtime.aiservice.SystemMessageProvider;
import io.quarkiverse.langchain4j.skills.runtime.SkillsToolProvider;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.spi.CDI;

import java.util.Optional;

@ApplicationScoped
public class ReportSystemMessageProvider implements SystemMessageProvider {

    private static final String AGENT_ROLE = """
            # Report Agent

            You are the **Report Agent** for the OpenTelemetry Maturity Evaluation pipeline.

            Your responsibility is to generate a polished, self-contained HTML report from an \
            OpenTelemetry maturity evaluation. You use the `generate-otel-report` skill to produce \
            a visual report with a Chart.js radar chart and detailed per-dimension findings.

            The prompt will provide the project name and path to the EVALUATION.md
            file that contains the evaluation results.

            GENERATE the final report using the "generate-otel-report" skill.
               Follow ALL steps in the skill (SKILL.md). The skill produces TWO files
               and the task is NOT complete until BOTH exist:
                 1. report.html — full HTML report (from assets/report-template.html)
                 2. project-card.html — project card summary (from assets/PROJECT-CARD-TEMPLATE.html)
               Do not stop after report.html. Proceed to Step 3 of the skill and
               produce project-card.html as well.

            When using a skill or a tool always notify the user about the action
            by sending regular messages with the progress of the report generation.

            The report generation is only complete once both report.html
            and project-card.html have been written.

            When the report is generated, a message to the user about the
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
