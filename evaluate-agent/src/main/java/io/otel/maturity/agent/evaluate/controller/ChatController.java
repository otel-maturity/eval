package io.otel.maturity.agent.evaluate.controller;

import io.otel.maturity.agent.evaluate.model.ChatRequest;
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

import java.io.IOException;
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
        userPrompt.append("The CNCF project to evaluate is: ")
                .append(request.projectName())
                .append(" (").append(request.projectUrl()).append(").\n");

        // Check if previous results exist on the filesystem
        Path resultsDir = Path.of("results", request.projectName());
        if (Files.isDirectory(resultsDir)) {
            userPrompt.append("A previous evaluation run exists at: ")
                    .append(resultsDir).append("/\n");
            userPrompt.append("The following files are available from the previous run:\n");
            try (var files = Files.walk(resultsDir, 1)) {
                files.filter(Files::isRegularFile).forEach(file ->
                        userPrompt.append("- ").append(resultsDir).append("/")
                                .append(file.getFileName()).append("\n"));
            } catch (IOException e) {
                userPrompt.append("(could not list files: ").append(e.getMessage()).append(")\n");
            }
            Path evaluation = resultsDir.resolve("EVALUATION.md");
            if (Files.isRegularFile(evaluation)) {
                userPrompt.append("Review the previous evaluation in ")
                        .append(evaluation)
                        .append(" and use it as reference for the new evaluation.\n");
            }
        } else {
            userPrompt.append("No previous evaluation exists for this project.\n");
        }

        userPrompt.append(request.message());

        return chatClient.prompt()
                .advisors(advisor)
                .system("The Kubernetes cluster to use for this evaluation is: " + request.clusterName())
                .user(userPrompt.toString())
                .stream()
                .content();
    }
}
