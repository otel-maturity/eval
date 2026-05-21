package io.otel.maturity.agent.install.quarkus;

import io.otel.maturity.agent.install.quarkus.model.ChatRequest;
import io.smallrye.mutiny.Multi;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.jboss.resteasy.reactive.RestStreamElementType;

import java.io.IOException;
import java.nio.file.Files;
import java.util.stream.Stream;

@Path("/api/chat")
public class ChatResource {

    private static final String AGENT_INSTRUCTIONS = """
            # Install Agent

            You are the **Install Agent** for the OpenTelemetry Maturity Evaluation pipeline.

            Your responsibility is to research, install, and configure a CNCF project in an \
            evaluation cluster so that its telemetry can be collected and assessed. You use the \
            `install-cncf-project` skill to accomplish this.

            INSTALL the CNCF project:
               - If the prompt lists an INSTALL-PLAN.md from a previous run, read
                 it with FileSystemTools and use it to install the project directly.
                 Skip the research phase of the "install-cncf-project" skill.
               - If no INSTALL-PLAN.md is available, run the full
                 "install-cncf-project" skill to research and install the project.

            When using a skill or a tool always notify the user about the action
            by sending regular messages with the progress of the installation.

            The installation must finish with telemetry flowing to the collector.

            The agent must create an INSTALL-PLAN.md file documenting the steps used to install the project.

            When the installation is finished, a message to the user about the
            steps that were taken must be sent as the last message.
            Use ++++ as a separator.
            """;

    @Inject
    InstallAgent agent;

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
        sb.append("The Kubernetes cluster to use for this installation is: ")
          .append(request.clusterName()).append("\n");
        sb.append("The CNCF project to install is: ")
          .append(request.projectName())
          .append(" (").append(request.projectUrl()).append(").\n");

        boolean foundPrevious = false;
        for (java.nio.file.Path dir : new java.nio.file.Path[]{
                java.nio.file.Path.of(".otel-eval", request.projectName()),
                java.nio.file.Path.of("results", request.projectName())}) {
            if (Files.isDirectory(dir)) {
                foundPrevious = true;
                sb.append("A previous installation run exists at: ").append(dir).append("/\n");
                sb.append("The following files are available from the previous run:\n");
                try (Stream<java.nio.file.Path> files = Files.walk(dir, 1)) {
                    files.filter(Files::isRegularFile).forEach(file ->
                            sb.append("- ").append(dir).append("/")
                              .append(file.getFileName()).append("\n"));
                } catch (IOException e) {
                    sb.append("(could not list files: ").append(e.getMessage()).append(")\n");
                }
                java.nio.file.Path installPlan = dir.resolve("INSTALL-PLAN.md");
                if (Files.isRegularFile(installPlan)) {
                    sb.append("Review the installation steps in ")
                      .append(installPlan)
                      .append(" before proceeding and use them as the basis for the new installation.\n");
                }
            }
        }
        if (!foundPrevious) {
            sb.append("No previous installation exists for this project.\n");
        }

        sb.append(request.message());
        return sb.toString();
    }
}
