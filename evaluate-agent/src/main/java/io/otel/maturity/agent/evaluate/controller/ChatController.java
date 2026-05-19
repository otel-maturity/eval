package io.otel.maturity.agent.evaluate.controller;

import io.otel.maturity.agent.evaluate.model.ChatRequest;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatClient chatClient;
    private final RestClient restClient;
    private final InMemoryChatMemoryRepository memoryRepository = new InMemoryChatMemoryRepository();

    @Value("${dimension.agents.1.url:http://integration-surface-agent:8080}")
    private String dim1Url;

    @Value("${dimension.agents.2.url:http://semantic-conventions-agent:8080}")
    private String dim2Url;

    @Value("${dimension.agents.3.url:http://resource-attributes-agent:8080}")
    private String dim3Url;

    @Value("${dimension.agents.4.url:http://trace-modeling-agent:8080}")
    private String dim4Url;

    @Value("${dimension.agents.5.url:http://multi-signal-agent:8080}")
    private String dim5Url;

    @Value("${dimension.agents.6.url:http://audience-quality-agent:8080}")
    private String dim6Url;

    @Value("${dimension.agents.7.url:http://stability-agent:8080}")
    private String dim7Url;

    public ChatController(ChatClient chatClient) {
        this.chatClient = chatClient;
        HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory();
        factory.setReadTimeout(Duration.ofHours(2));
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    @PostMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chat(@RequestBody ChatRequest request) {
        MessageChatMemoryAdvisor advisor = MessageChatMemoryAdvisor.builder(
                        MessageWindowChatMemory.builder()
                                .chatMemoryRepository(memoryRepository)
                                .build())
                .conversationId(request.conversationId())
                .build();

        // Determine next version number from existing evaluation files
        boolean foundPrevious = false;
        int nextVersion = 1;
        Pattern versionPattern = Pattern.compile("EVALUATION_v(\\d+)\\.md");
        for (Path dir : new Path[]{
                Path.of(".otel-eval", request.projectName()),
                Path.of("results", request.projectName())}) {
            if (Files.isDirectory(dir)) {
                foundPrevious = true;
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

        final String version = "v" + nextVersion;
        final int versionNumber = nextVersion;
        final boolean hasPrevious = foundPrevious;

        // Call all 7 dimension agents in parallel using virtual threads.
        // Results are collected into a map as each dimension completes so that
        // we can stream each section to the user the moment it arrives, rather
        // than waiting for all 7 before emitting anything.
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        Map<Integer, String> dimResults = new ConcurrentHashMap<>();

        Flux<String> d1 = dimFlux(1, "Integration Surface",            dim1Url, request, version, executor, dimResults);
        Flux<String> d2 = dimFlux(2, "Semantic Conventions",           dim2Url, request, version, executor, dimResults);
        Flux<String> d3 = dimFlux(3, "Resource Attributes",            dim3Url, request, version, executor, dimResults);
        Flux<String> d4 = dimFlux(4, "Trace Modeling",                 dim4Url, request, version, executor, dimResults);
        Flux<String> d5 = dimFlux(5, "Multi-Signal Observability",     dim5Url, request, version, executor, dimResults);
        Flux<String> d6 = dimFlux(6, "Audience & Signal Quality",      dim6Url, request, version, executor, dimResults);
        Flux<String> d7 = dimFlux(7, "Stability & Change Management",  dim7Url, request, version, executor, dimResults);

        return Flux.concat(
                Flux.just("Starting parallel dimension evaluations for "
                        + request.projectName() + " " + version + "...\n\n"),
                // Emits each dimension's section as soon as it finishes (order varies)
                Flux.merge(d1, d2, d3, d4, d5, d6, d7),
                // Flux.merge completes only after all 7 are done — safe to assemble
                Flux.defer(() -> {
                    executor.close();
                    String assemblyPrompt = buildAssemblyPrompt(
                            request, version, versionNumber, hasPrevious,
                            dimResults.getOrDefault(1, ""),
                            dimResults.getOrDefault(2, ""),
                            dimResults.getOrDefault(3, ""),
                            dimResults.getOrDefault(4, ""),
                            dimResults.getOrDefault(5, ""),
                            dimResults.getOrDefault(6, ""),
                            dimResults.getOrDefault(7, ""));
                    return Flux.just("\n\n---\nAssembling final evaluation...\n\n")
                            .concatWith(chatClient.prompt()
                                    .advisors(advisor)
                                    .user(assemblyPrompt)
                                    .stream()
                                    .content());
                })
        );
    }

    private Flux<String> dimFlux(int num, String label, String baseUrl,
                                  ChatRequest original, String version,
                                  ExecutorService executor, Map<Integer, String> results) {
        return Mono.fromFuture(() -> CompletableFuture.supplyAsync(() -> {
            ChatRequest req = new ChatRequest(
                    UUID.randomUUID().toString(),
                    original.clusterName(),
                    original.projectName(),
                    original.projectUrl(),
                    "Evaluate for project " + original.projectName() + " version " + version);
            String response = restClient.post()
                    .uri(baseUrl + "/api/evaluate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(req)
                    .retrieve()
                    .body(String.class);
            return response != null ? response : "";
        }, executor))
        .onErrorResume(e -> Mono.just("(Dimension " + num + " evaluation failed: " + e.getMessage() + ")"))
        .doOnNext(r -> results.put(num, r))
        .map(r -> "#### [Dimension " + num + " — " + label + " — complete]\n\n" + r + "\n\n")
        .flux();
    }

    private String buildAssemblyPrompt(ChatRequest request, String version, int versionNumber,
                                        boolean hasPrevious,
                                        String d1, String d2, String d3, String d4,
                                        String d5, String d6, String d7) {
        StringBuilder sb = new StringBuilder();
        sb.append("Assemble the complete EVALUATION.md for project **")
          .append(request.projectName()).append("** (evaluation run: ").append(version).append(").\n\n");

        if (hasPrevious) {
            Path prev = Path.of(".otel-eval", request.projectName(), "EVALUATION.md");
            if (Files.isRegularFile(prev)) {
                sb.append("A previous EVALUATION.md exists at ").append(prev)
                  .append(" — read it for context before assembling.\n\n");
            }
        }

        sb.append("## Dimension Results\n\n");
        sb.append("### Dimension 1: Integration Surface\n\n").append(d1).append("\n\n");
        sb.append("### Dimension 2: Semantic Conventions\n\n").append(d2).append("\n\n");
        sb.append("### Dimension 3: Resource Attributes & Configuration\n\n").append(d3).append("\n\n");
        sb.append("### Dimension 4: Trace Modeling & Context Propagation\n\n").append(d4).append("\n\n");
        sb.append("### Dimension 5: Multi-Signal Observability\n\n").append(d5).append("\n\n");
        sb.append("### Dimension 6: Audience & Signal Quality\n\n").append(d6).append("\n\n");
        sb.append("### Dimension 7: Stability & Change Management\n\n").append(d7).append("\n\n");

        sb.append("Write the complete EVALUATION.md (overwriting any existing file) and ")
          .append("EVALUATION_v").append(versionNumber).append(".md ")
          .append("to .otel-eval/").append(request.projectName()).append("/ using FileSystemTools.\n");

        return sb.toString();
    }
}
