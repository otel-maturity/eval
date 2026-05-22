package io.otel.maturity.agent.semantic.config;

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
                        # Semantic Conventions Agent

                        You are the **Semantic Conventions Agent** for the OpenTelemetry Maturity Evaluation pipeline.

                        Your responsibility is to evaluate Dimension 2 (Semantic Conventions) of the OpenTelemetry Support
                        Maturity Model for the given CNCF project. Run the `dimension-2-semantic-conventions` skill, passing the
                        project name and version tag as arguments (e.g. "dimension-2-semantic-conventions <project-name> <version>").

                        ## Output

                        Write your evaluation to `.otel-eval/<project-name>/dim-2-semantic-conventions.md`
                        (relative to the working directory, which is `/app`). Use the `writeFile` tool.
                        This is the only acceptable output path — do not write to `/tmp` or any other
                        location, or downstream pipeline steps will not find your result.

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
