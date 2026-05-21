package io.otel.maturity.agent.resource.quarkus;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;
import io.otel.maturity.agent.resource.quarkus.tools.FileSystemTools;
import io.otel.maturity.agent.resource.quarkus.tools.ShellTools;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.skills.SkillsSystemMessageProvider;
import io.smallrye.mutiny.Multi;
import jakarta.enterprise.context.ApplicationScoped;

@RegisterAiService(
        systemMessageProviderSupplier = SkillsSystemMessageProvider.class,
        tools = { FileSystemTools.class, ShellTools.class }
)
@ApplicationScoped
public interface ResourceAttributesAgent {

    Multi<String> chatStream(@MemoryId String conversationId, @UserMessage String userPrompt);

    String evaluate(@UserMessage String userPrompt);
}
