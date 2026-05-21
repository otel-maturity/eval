package io.otel.maturity.agent.evaluate.quarkus;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.otel.maturity.agent.evaluate.quarkus.model.ChatRequest;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.resteasy.reactive.RestStreamElementType;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Path("/api/chat")
public class ChatResource {

    private static final Pattern VERSION_PATTERN = Pattern.compile("EVALUATION_v(\\d+)\\.md");

    @ConfigProperty(name = "dimension.agents.1.url") String dim1Url;
    @ConfigProperty(name = "dimension.agents.2.url") String dim2Url;
    @ConfigProperty(name = "dimension.agents.3.url") String dim3Url;
    @ConfigProperty(name = "dimension.agents.4.url") String dim4Url;
    @ConfigProperty(name = "dimension.agents.5.url") String dim5Url;
    @ConfigProperty(name = "dimension.agents.6.url") String dim6Url;
    @ConfigProperty(name = "dimension.agents.7.url") String dim7Url;

    @Inject EvaluateAgent agent;
    @Inject ObjectMapper objectMapper;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.SERVER_SENT_EVENTS)
    @RestStreamElementType(MediaType.TEXT_PLAIN)
    public Multi<String> chat(ChatRequest request) {
        VersionInfo v = computeVersion(request.projectName());
        final String version = "v" + v.nextVersion;
        final int versionNumber = v.nextVersion;
        final boolean hasPrevious = v.foundPrevious;

        Map<Integer, String> dimResults = new ConcurrentHashMap<>();

        Multi<String> d1 = dimensionMulti(1, "Integration Surface",           dim1Url, request, version, dimResults);
        Multi<String> d2 = dimensionMulti(2, "Semantic Conventions",          dim2Url, request, version, dimResults);
        Multi<String> d3 = dimensionMulti(3, "Resource Attributes",           dim3Url, request, version, dimResults);
        Multi<String> d4 = dimensionMulti(4, "Trace Modeling",                dim4Url, request, version, dimResults);
        Multi<String> d5 = dimensionMulti(5, "Multi-Signal Observability",    dim5Url, request, version, dimResults);
        Multi<String> d6 = dimensionMulti(6, "Audience & Signal Quality",     dim6Url, request, version, dimResults);
        Multi<String> d7 = dimensionMulti(7, "Stability & Change Management", dim7Url, request, version, dimResults);

        Multi<String> opening = Multi.createFrom().item(
                "Starting parallel dimension evaluations for "
                        + request.projectName() + " " + version + "...\n\n");

        // Multi.merge emits items as each dimension completes (order varies)
        Multi<String> fanout = Multi.createBy().merging().streams(d1, d2, d3, d4, d5, d6, d7);

        // Defer the assembly step so it only runs after all 7 dimensions complete
        Multi<String> assembly = Multi.createFrom().deferred(() -> {
            String r1 = dimResults.getOrDefault(1, "");
            String r2 = dimResults.getOrDefault(2, "");
            String r3 = dimResults.getOrDefault(3, "");
            String r4 = dimResults.getOrDefault(4, "");
            String r5 = dimResults.getOrDefault(5, "");
            String r6 = dimResults.getOrDefault(6, "");
            String r7 = dimResults.getOrDefault(7, "");

            String preliminary = buildPreliminaryEvaluation(request, version,
                    r1, r2, r3, r4, r5, r6, r7);
            java.nio.file.Path evalDir = java.nio.file.Path.of(".otel-eval", request.projectName());
            try {
                Files.createDirectories(evalDir);
                Files.writeString(evalDir.resolve("EVALUATION.md"), preliminary);
                Files.writeString(evalDir.resolve("EVALUATION_v" + versionNumber + ".md"), preliminary);
            } catch (IOException e) {
                System.err.println("Warning: failed to write EVALUATION.md: " + e.getMessage());
            }

            String assemblyPrompt = buildAssemblyPrompt(request, version, versionNumber,
                    hasPrevious, r1, r2, r3, r4, r5, r6, r7);
            Multi<String> header = Multi.createFrom().item("\n\n---\nAssembling final evaluation...\n\n");
            Multi<String> assembled = agent.chatStream(request.conversationId(), assemblyPrompt);
            return Multi.createBy().concatenating().streams(header, assembled);
        });

        return Multi.createBy().concatenating().streams(opening, fanout, assembly);
    }

    private Multi<String> dimensionMulti(int num, String label, String baseUrl,
                                          ChatRequest original, String version,
                                          Map<Integer, String> results) {
        return Uni.createFrom().completionStage(() -> {
                    ChatRequest req = new ChatRequest(
                            UUID.randomUUID().toString(),
                            original.clusterName(),
                            original.projectName(),
                            original.projectUrl(),
                            "Evaluate for project " + original.projectName() + " version " + version);
                    String body;
                    try {
                        body = objectMapper.writeValueAsString(req);
                    } catch (JsonProcessingException e) {
                        throw new RuntimeException(e);
                    }
                    HttpRequest httpReq = HttpRequest.newBuilder(URI.create(baseUrl + "/api/evaluate"))
                            .header("Content-Type", MediaType.APPLICATION_JSON)
                            .header("Accept", MediaType.TEXT_PLAIN)
                            .timeout(Duration.ofHours(2))
                            .POST(HttpRequest.BodyPublishers.ofString(body))
                            .build();
                    return httpClient.sendAsync(httpReq, HttpResponse.BodyHandlers.ofString())
                            .thenApply(HttpResponse::body);
                })
                .onItem().invoke(r -> results.put(num, r != null ? r : ""))
                .onFailure().recoverWithItem(e -> {
                    String fallback = "(Dimension " + num + " evaluation failed: " + e.getMessage() + ")";
                    results.put(num, fallback);
                    return fallback;
                })
                .onItem().transform(r -> "#### [Dimension " + num + " — " + label + " — complete]\n\n"
                        + r + "\n\n")
                .toMulti();
    }

    private record VersionInfo(int nextVersion, boolean foundPrevious) {}

    private VersionInfo computeVersion(String projectName) {
        boolean foundPrevious = false;
        int nextVersion = 1;
        for (java.nio.file.Path dir : new java.nio.file.Path[]{
                java.nio.file.Path.of(".otel-eval", projectName),
                java.nio.file.Path.of("results", projectName)}) {
            if (Files.isDirectory(dir)) {
                foundPrevious = true;
                try (var files = Files.list(dir)) {
                    int highestVersion = files
                            .map(p -> {
                                Matcher m = VERSION_PATTERN.matcher(p.getFileName().toString());
                                return m.matches() ? Integer.parseInt(m.group(1)) : 0;
                            })
                            .max(Integer::compareTo)
                            .orElse(0);
                    nextVersion = Math.max(nextVersion, highestVersion + 1);
                } catch (IOException ignored) {
                }
            }
        }
        return new VersionInfo(nextVersion, foundPrevious);
    }

    private String buildPreliminaryEvaluation(ChatRequest request, String version,
                                               String d1, String d2, String d3, String d4,
                                               String d5, String d6, String d7) {
        return "# OTel Maturity Evaluation: " + request.projectName() + " (" + version + ")\n\n" +
               "> This file was assembled from dimension agent outputs. " +
               "A polished version may follow if Claude assembly succeeds.\n\n" +
               "## Dimension Results\n\n" +
               "### Dimension 1: Integration Surface\n\n" + d1 + "\n\n" +
               "### Dimension 2: Semantic Conventions\n\n" + d2 + "\n\n" +
               "### Dimension 3: Resource Attributes & Configuration\n\n" + d3 + "\n\n" +
               "### Dimension 4: Trace Modeling & Context Propagation\n\n" + d4 + "\n\n" +
               "### Dimension 5: Multi-Signal Observability\n\n" + d5 + "\n\n" +
               "### Dimension 6: Audience & Signal Quality\n\n" + d6 + "\n\n" +
               "### Dimension 7: Stability & Change Management\n\n" + d7 + "\n";
    }

    private String buildAssemblyPrompt(ChatRequest request, String version, int versionNumber,
                                        boolean hasPrevious,
                                        String d1, String d2, String d3, String d4,
                                        String d5, String d6, String d7) {
        StringBuilder sb = new StringBuilder();
        sb.append("Assemble the complete EVALUATION.md for project **")
          .append(request.projectName()).append("** (evaluation run: ").append(version).append(").\n\n");

        sb.append("**Step 1 — Gather context:** Activate the `evaluate-otel-maturity` skill with ")
          .append("arguments `").append(request.projectName()).append(" ").append(version)
          .append("` to collect telemetry evidence and documentation findings. ")
          .append("Use the skill output to produce the Project overview, Telemetry overview, ")
          .append("and Installation context summary sections.\n\n");

        sb.append("**Step 2 — Assemble:** Combine the context from Step 1 with the ")
          .append("seven dimension results below into the final EVALUATION.md.\n\n");

        if (hasPrevious) {
            java.nio.file.Path prev = java.nio.file.Path.of(".otel-eval", request.projectName(), "EVALUATION.md")
                    .toAbsolutePath();
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

        String evalDir = java.nio.file.Path.of(".otel-eval", request.projectName())
                .toAbsolutePath().toString();
        sb.append("Write the complete EVALUATION.md (overwriting any existing file) to **")
          .append(evalDir).append("/EVALUATION.md** and also write ")
          .append("EVALUATION_v").append(versionNumber).append(".md to **")
          .append(evalDir).append("/EVALUATION_v").append(versionNumber).append(".md")
          .append("** using the writeFile tool. Use those exact absolute paths.\n\n");

        sb.append("When the evaluation is finished, send a summary message with the summary table ")
          .append("and top findings. Use ++++ as a separator before the final message.\n");

        return sb.toString();
    }
}
