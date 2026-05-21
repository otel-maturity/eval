package io.otel.maturity.agent.multisignal.quarkus;

import io.otel.maturity.agent.multisignal.quarkus.model.ChatRequest;
import io.smallrye.mutiny.Multi;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.jboss.resteasy.reactive.RestStreamElementType;

@Path("/api")
public class ChatResource {

    private static final String AGENT_INSTRUCTIONS = """
            # Multi-Signal Observability Agent

            You are the **Multi-Signal Observability Agent** for the OpenTelemetry Maturity Evaluation pipeline.

            Your responsibility is to evaluate Dimension 5 (Multi-Signal Observability) of the OpenTelemetry Support
            Maturity Model for the given CNCF project. Activate the `dimension-5-multi-signal` skill, passing the
            project name and version tag as arguments (e.g. "dimension-5-multi-signal <project-name> <version>").

            When using a skill or a tool always notify the user about the action
            by sending regular messages with the progress of the evaluation.

            When the evaluation is finished, a message to the user about the
            steps that were taken must be sent as the last message. Use ++++ as a separator.
            """;

    @Inject
    MultiSignalAgent agent;

    @POST
    @Path("/chat")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.SERVER_SENT_EVENTS)
    @RestStreamElementType(MediaType.TEXT_PLAIN)
    public Multi<String> chat(ChatRequest request) {
        return agent.chatStream(request.conversationId(), buildUserPrompt(request));
    }

    @POST
    @Path("/evaluate")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.TEXT_PLAIN)
    public String evaluate(ChatRequest request) {
        return agent.evaluate(buildUserPrompt(request));
    }

    private String buildUserPrompt(ChatRequest request) {
        return AGENT_INSTRUCTIONS + "\n\n" +
               "The Kubernetes cluster to use for this evaluation is: " + request.clusterName() + "\n" +
               "The CNCF project to evaluate is: " + request.projectName() +
               " (" + request.projectUrl() + ").\n" +
               request.message();
    }
}
