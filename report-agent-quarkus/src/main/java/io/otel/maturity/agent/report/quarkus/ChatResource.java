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

    private static final String AGENT_INSTRUCTIONS = """
            # Report Agent

            You are the **Report Agent** for the OpenTelemetry Maturity Evaluation pipeline.

            Your responsibility is to generate a polished, self-contained HTML report from an \
            OpenTelemetry maturity evaluation. You use the `generate-otel-report` skill to produce \
            a visual report with a Chart.js radar chart and detailed per-dimension findings.

            The prompt will provide the project name and path to the EVALUATION.md
            file that contains the evaluation results.

            GENERATE the final report using the "generate-otel-report" skill.
               Follow ALL steps in the skill (SKILL.md). The skill produces TWO files
               and the task is NOT complete until BOTH exist:
                 1. report.html — full HTML report (from assets/report-template.html)
                 2. project-card.html — project card summary (from assets/PROJECT-CARD-TEMPLATE.html)
               Do not stop after report.html. Proceed to Step 3 of the skill and
               produce project-card.html as well.

            When using a skill or a tool always notify the user about the action
            by sending regular messages with the progress of the report generation.

            The report generation is only complete once both report.html
            and project-card.html have been written.

            When the report is generated, a message to the user about the
            steps that were taken must be sent as the last message. Use ++++ as a separator.
            """;

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
        sb.append(AGENT_INSTRUCTIONS).append("\n\n");
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
