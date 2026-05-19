package io.otel.maturity.agent.progress.config;

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
                        """)
                .defaultToolCallbacks(SkillsTool.builder()
                        .addSkillsResource(resourceLoader.getResource("classpath:.agents/skills"))
                        .build())
                .defaultTools(FileSystemTools.builder().build())
                .defaultTools(ShellTools.builder().build())
                .build();
    }
}
