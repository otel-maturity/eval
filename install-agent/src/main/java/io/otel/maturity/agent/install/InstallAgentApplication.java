package io.otel.maturity.agent.install;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class InstallAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(InstallAgentApplication.class, args);
    }

}
