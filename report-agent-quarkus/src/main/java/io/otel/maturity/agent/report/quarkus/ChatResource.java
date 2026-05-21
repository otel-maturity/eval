package io.otel.maturity.agent.report.quarkus;

import io.otel.maturity.agent.report.quarkus.model.ChatRequest;
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
    ReportAgent agent;

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.SERVER_SENT_EVENTS)
    @RestStreamElementType(MediaType.TEXT_PLAIN)
    public Multi<String> chat(ChatRequest request) {
        return agent.chatStream(request.conversationId(), buildUserPrompt(request));
    }

    private String buildUserPrompt(ChatRequest request) {
        StringBuilder sb = new StringBuilder();
        sb.append("The CNCF project to generate a report for is: ")
          .append(request.projectName())
          .append(" (").append(request.projectUrl()).append(").\n");

        java.nio.file.Path resultsDir = java.nio.file.Path.of("results", request.projectName());
        java.nio.file.Path evaluation = resultsDir.resolve("EVALUATION.md");
        if (Files.isRegularFile(evaluation)) {
            sb.append("The evaluation file is at: ").append(evaluation).append("\n");
        }

        java.nio.file.Path otelEvalDir = java.nio.file.Path.of(".otel-eval", request.projectName());
        java.nio.file.Path otelEvaluation = otelEvalDir.resolve("EVALUATION.md");
        if (Files.isRegularFile(otelEvaluation)) {
            sb.append("The evaluation file is at: ").append(otelEvaluation).append("\n");
        }

        sb.append(request.message());
        return sb.toString();
    }
}
