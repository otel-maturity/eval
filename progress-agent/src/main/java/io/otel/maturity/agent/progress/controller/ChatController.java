package io.otel.maturity.agent.progress.controller;

import io.otel.maturity.agent.progress.model.ChatRequest;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.nio.file.Files;
import java.nio.file.Path;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatClient chatClient;

    private final InMemoryChatMemoryRepository memoryRepository = new InMemoryChatMemoryRepository();

    public ChatController(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @PostMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chat(@RequestBody ChatRequest request) {
        MessageChatMemoryAdvisor advisor = MessageChatMemoryAdvisor.builder(
                        MessageWindowChatMemory.builder()
                                .chatMemoryRepository(memoryRepository)
                                .build())
                .conversationId(request.conversationId())
                .build();

        StringBuilder userPrompt = new StringBuilder();
        userPrompt.append("The CNCF project to track progress for is: ")
                .append(request.projectName())
                .append(" (").append(request.projectUrl()).append(").\n");

        // Check for TRACKING.md in both supported result locations
        boolean foundTracking = false;
        for (Path dir : new Path[]{
                Path.of("results", request.projectName()),
                Path.of(".otel-eval", request.projectName())}) {
            Path tracking = dir.resolve("TRACKING.md");
            if (Files.isRegularFile(tracking)) {
                userPrompt.append("The TRACKING.md file is at: ").append(tracking).append("\n");
                foundTracking = true;

                // Also surface existing EVOLUTION.md so the agent can merge/update it
                Path evolution = dir.resolve("EVOLUTION.md");
                if (Files.isRegularFile(evolution)) {
                    userPrompt.append("An existing EVOLUTION.md is at: ").append(evolution).append("\n");
                }
                break;
            }
        }

        if (!foundTracking) {
            userPrompt.append("No TRACKING.md file found for this project. No GitHub queries are needed.\n");
        }

        userPrompt.append(request.message());

        return chatClient.prompt()
                .advisors(advisor)
                .user(userPrompt.toString())
                .stream()
                .content();
    }
}
