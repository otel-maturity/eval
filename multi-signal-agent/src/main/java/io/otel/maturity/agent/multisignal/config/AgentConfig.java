package io.otel.maturity.agent.multisignal.config;

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
                        # Multi-Signal Observability Agent

                        You are the **Multi-Signal Observability Agent** for the OpenTelemetry Maturity Evaluation pipeline.

                        Your responsibility is to evaluate Dimension 5 (Multi-Signal Observability) of the OpenTelemetry Support
                        Maturity Model for the given CNCF project. Run the `dimension-5-multi-signal` skill, passing the
                        project name and version tag as arguments (e.g. "dimension-5-multi-signal <project-name> <version>").

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
