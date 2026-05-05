package io.otel.maturity.agent.report.controller;

import io.otel.maturity.agent.report.model.ChatRequest;
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
        userPrompt.append("The CNCF project to generate a report for is: ")
                .append(request.projectName())
                .append(" (").append(request.projectUrl()).append(").\n");

        // Check if evaluation results exist on the filesystem
        Path resultsDir = Path.of("results", request.projectName());
        Path evaluation = resultsDir.resolve("EVALUATION.md");
        if (Files.isRegularFile(evaluation)) {
            userPrompt.append("The evaluation file is at: ")
                    .append(evaluation).append("\n");
        }

        // Also check .otel-eval directory
        Path otelEvalDir = Path.of(".otel-eval", request.projectName());
        Path otelEvaluation = otelEvalDir.resolve("EVALUATION.md");
        if (Files.isRegularFile(otelEvaluation)) {
            userPrompt.append("The evaluation file is at: ")
                    .append(otelEvaluation).append("\n");
        }

        userPrompt.append(request.message());

        return chatClient.prompt()
                .advisors(advisor)
                .user(userPrompt.toString())
                .stream()
                .content();
    }
}
