package io.otel.maturity.agent.multisignal.quarkus.tools;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import jakarta.enterprise.context.ApplicationScoped;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

@ApplicationScoped
public class ShellTools {

    private static final long DEFAULT_TIMEOUT_SECONDS = 600;

    @Tool("""
            Run a shell command and return its stdout, stderr, and exit code. The command is executed via
            `/bin/sh -c <command>`. Use this for kubectl/helm/curl/jq and any other CLI needed by the active skill.
            Times out after 10 minutes.
            """)
    public String runShell(@P("Shell command to execute") String command) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder("/bin/sh", "-c", command);
        pb.redirectErrorStream(false);
        Process process = pb.start();

        StringBuilder stdout = new StringBuilder();
        StringBuilder stderr = new StringBuilder();

        Thread outThread = Thread.ofVirtual().start(() -> drain(process.getInputStream(), stdout));
        Thread errThread = Thread.ofVirtual().start(() -> drain(process.getErrorStream(), stderr));

        boolean finished = process.waitFor(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            outThread.join();
            errThread.join();
            return "ERROR: command timed out after " + DEFAULT_TIMEOUT_SECONDS + "s\n"
                    + "STDOUT:\n" + stdout + "\nSTDERR:\n" + stderr;
        }
        outThread.join();
        errThread.join();

        return "exit_code=" + process.exitValue() + "\n"
                + "STDOUT:\n" + stdout + "\n"
                + "STDERR:\n" + stderr;
    }

    private static void drain(java.io.InputStream in, StringBuilder sink) {
        try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sink.append(line).append('\n');
            }
        } catch (IOException ignored) {
            // process termination races are expected
        }
    }
}
