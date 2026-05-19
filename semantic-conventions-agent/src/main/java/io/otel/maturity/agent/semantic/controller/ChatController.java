package io.otel.maturity.agent.semantic.controller;

import io.otel.maturity.agent.semantic.model.ChatRequest;
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

@RestController
@RequestMapping("/api")
public class ChatController {

    private final ChatClient chatClient;

    private final InMemoryChatMemoryRepository memoryRepository = new InMemoryChatMemoryRepository();

    public ChatController(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    /**
     * Streaming SSE endpoint — for standalone use or human-facing clients.
     */
    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chat(@RequestBody ChatRequest request) {
        MessageChatMemoryAdvisor advisor = MessageChatMemoryAdvisor.builder(
                        MessageWindowChatMemory.builder()
                                .chatMemoryRepository(memoryRepository)
                                .build())
                .conversationId(request.conversationId())
                .build();

        String userPrompt = "The CNCF project to evaluate is: " + request.projectName() +
                " (" + request.projectUrl() + ").\n" + request.message();

        return chatClient.prompt()
                .advisors(advisor)
                .system("The Kubernetes cluster to use for this evaluation is: " + request.clusterName())
                .user(userPrompt)
                .stream()
                .content();
    }

    /**
     * Blocking endpoint — called by the evaluate-agent orchestrator.
     * Returns the complete dimension evaluation section as plain text.
     */
    @PostMapping(value = "/evaluate", produces = MediaType.TEXT_PLAIN_VALUE)
    public String evaluate(@RequestBody ChatRequest request) {
        String userPrompt = "The CNCF project to evaluate is: " + request.projectName() +
                " (" + request.projectUrl() + ").\n" + request.message();

        return chatClient.prompt()
                .system("The Kubernetes cluster to use for this evaluation is: " + request.clusterName())
                .user(userPrompt)
                .call()
                .content();
    }
}
