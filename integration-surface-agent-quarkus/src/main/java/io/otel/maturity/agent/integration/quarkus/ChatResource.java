package io.otel.maturity.agent.integration.quarkus;

import io.otel.maturity.agent.integration.quarkus.model.ChatRequest;
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

    @Inject
    IntegrationSurfaceAgent agent;

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
        return "The Kubernetes cluster to use for this evaluation is: " + request.clusterName() + "\n" +
               "The CNCF project to evaluate is: " + request.projectName() +
               " (" + request.projectUrl() + ").\n" +
               request.message();
    }
}
