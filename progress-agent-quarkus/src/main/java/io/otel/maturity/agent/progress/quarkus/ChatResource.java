package io.otel.maturity.agent.progress.quarkus;

import io.otel.maturity.agent.progress.quarkus.model.ChatRequest;
import io.smallrye.mutiny.Multi;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.jboss.resteasy.reactive.RestStreamElementType;

import java.nio.file.Files;

@Path("/api/chat")
public class ChatResource {

    @Inject
    ProgressAgent agent;

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.SERVER_SENT_EVENTS)
    @RestStreamElementType(MediaType.TEXT_PLAIN)
    public Multi<String> chat(ChatRequest request) {
        return agent.chatStream(request.conversationId(), buildUserPrompt(request));
    }

    private String buildUserPrompt(ChatRequest request) {
        StringBuilder sb = new StringBuilder();
        sb.append("The CNCF project to track progress for is: ")
          .append(request.projectName())
          .append(" (").append(request.projectUrl()).append(").\n");

        boolean foundTracking = false;
        for (java.nio.file.Path dir : new java.nio.file.Path[]{
                java.nio.file.Path.of("results", request.projectName()),
                java.nio.file.Path.of(".otel-eval", request.projectName())}) {
            java.nio.file.Path tracking = dir.resolve("TRACKING.md");
            if (Files.isRegularFile(tracking)) {
                sb.append("The TRACKING.md file is at: ").append(tracking).append("\n");
                foundTracking = true;

                java.nio.file.Path evolution = dir.resolve("EVOLUTION.md");
                if (Files.isRegularFile(evolution)) {
                    sb.append("An existing EVOLUTION.md is at: ").append(evolution).append("\n");
                }
                break;
            }
        }

        if (!foundTracking) {
            sb.append("No TRACKING.md file found for this project. No GitHub queries are needed.\n");
        }

        sb.append(request.message());
        return sb.toString();
    }
}
