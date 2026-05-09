package io.otel.maturity.agent.report.config;

import org.springaicommunity.agent.tools.FileSystemTools;
import org.springaicommunity.agent.tools.ShellTools;
import org.springaicommunity.agent.tools.SkillsTool;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;

@Configuration
public class AgentConfig {

    @Bean
    ChatClient chatClient(ChatClient.Builder builder,
                          ResourceLoader resourceLoader) {
        return builder
                .defaultSystem("""
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
                        """)
                .defaultToolCallbacks(SkillsTool.builder()
                        .addSkillsResource(resourceLoader.getResource("classpath:.agents/skills"))
                        .build())
                .defaultTools(FileSystemTools.builder().build())
                .defaultTools(ShellTools.builder().build())
                .build();
    }
}
