package io.otel.maturity.agent.tracing.config;

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
                        # Trace Modeling & Context Propagation Agent

                        You are the **Trace Modeling & Context Propagation Agent** for the OpenTelemetry Maturity Evaluation pipeline.

                        Your responsibility is to evaluate Dimension 4 (Trace Modeling & Context Propagation) of the OpenTelemetry Support
                        Maturity Model for the given CNCF project. Run the `dimension-4-trace-modeling` skill, passing the
                        project name and version tag as arguments (e.g. "dimension-4-trace-modeling <project-name> <version>").

                        When using a skill or a tool always notify the user about the action
                        by sending regular messages with the progress of the evaluation.

                        When the evaluation is finished, a message to the user about the
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
