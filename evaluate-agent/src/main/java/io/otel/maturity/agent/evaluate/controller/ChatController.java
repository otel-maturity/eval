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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

        // Check if previous results exist on the filesystem (both locations)
        boolean foundPrevious = false;
        int nextVersion = 1;
        Pattern versionPattern = Pattern.compile("EVALUATION_v(\\d+)\\.md");
        for (Path dir : new Path[]{
                Path.of(".otel-eval", request.projectName()),
                Path.of("results", request.projectName())}) {
            if (Files.isDirectory(dir)) {
                foundPrevious = true;
                userPrompt.append("A previous evaluation run exists at: ")
                        .append(dir).append("/\n");
                userPrompt.append("The following files are available from the previous run:\n");
                try (var files = Files.walk(dir, 1)) {
                    files.filter(Files::isRegularFile).forEach(file ->
                            userPrompt.append("- ").append(dir).append("/")
                                    .append(file.getFileName()).append("\n"));
                } catch (IOException e) {
                    userPrompt.append("(could not list files: ").append(e.getMessage()).append(")\n");
                }
                Path evaluation = dir.resolve("EVALUATION.md");
                if (Files.isRegularFile(evaluation)) {
                    userPrompt.append("Review the previous evaluation in ")
                            .append(evaluation)
                            .append(" and use it as reference for the new evaluation.\n");
                }
                // Determine the highest existing version number for this project
                try (var files = Files.list(dir)) {
                    int highestVersion = files
                            .map(p -> {
                                Matcher m = versionPattern.matcher(p.getFileName().toString());
                                return m.matches() ? Integer.parseInt(m.group(1)) : 0;
                            })
                            .max(Integer::compareTo)
                            .orElse(0);
                    nextVersion = Math.max(nextVersion, highestVersion + 1);
                } catch (IOException e) {
                    // keep current nextVersion
                }
            }
        }
        if (!foundPrevious) {
            userPrompt.append("No previous evaluation exists for this project.\n");
        }
        userPrompt.append("The version tag for this evaluation run is: v").append(nextVersion).append("\n");

        userPrompt.append(request.message());

        return chatClient.prompt()
                .advisors(advisor)
                .system("The Kubernetes cluster to use for this evaluation is: " + request.clusterName())
                .user(userPrompt.toString())
                .stream()
                .content();
    }
}
