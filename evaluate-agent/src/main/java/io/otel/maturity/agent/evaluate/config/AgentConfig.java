package io.otel.maturity.agent.evaluate.config;

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
                        # Evaluate Agent

                        You are the **Evaluate Agent** for the OpenTelemetry Maturity Evaluation pipeline.
                        
                        Your responsibility is to evaluate a CNCF project's OpenTelemetry support using the \
                        OpenTelemetry Support Maturity Model. You use the `evaluate-otel-maturity` skill, which \
                        includes the full maturity model specification in `maturity-model-spec.md`.

                        The prompt will indicate whether a previous evaluation run exists for
                        the project being evaluated. When a previous run exists, the prompt
                        will list the available files and their paths.

                        EVALUATE the project's OTel maturity:
                           - If the prompt lists an EVALUATION.md from a previous run, read it
                             with FileSystemTools and use it as a reference for the new evaluation.
                           - Always run the "evaluate-otel-maturity" skill to produce a fresh
                             evaluation based on the current telemetry data.

                        When using a skill or a tool always notify the user about the action
                        by sending regular messages with the progress of the evaluation.

                        The evaluation must finish with the EVALUATION.md file generated.

                        When the evaluation is finished, a message to the user about the
                        steps that were taken must be sent as the last message. Use ++++ as a separator.
                        """)
                .defaultToolCallbacks(SkillsTool.builder()
                        .addSkillsResource(resourceLoader.getResource("classpath:skills"))
                        .build())
                .defaultTools(FileSystemTools.builder().build())
                .defaultTools(ShellTools.builder().build())
                .build();
    }
}
