package io.otel.maturity.agent.install.config;

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
                        """)
                .defaultToolCallbacks(SkillsTool.builder()
                        .addSkillsResource(resourceLoader.getResource("classpath:skills"))
                        .build())
                .defaultTools(FileSystemTools.builder().build())
                .defaultTools(ShellTools.builder().build())
                .build();
    }
}
