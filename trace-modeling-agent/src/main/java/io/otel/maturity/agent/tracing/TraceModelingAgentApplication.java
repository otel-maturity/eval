package io.otel.maturity.agent.tracing;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class TraceModelingAgentApplication {
    public static void main(String[] args) {
        SpringApplication.run(TraceModelingAgentApplication.class, args);
    }
}
