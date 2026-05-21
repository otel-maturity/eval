package io.otel.maturity.agent.evaluate.quarkus.tools;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import jakarta.enterprise.context.ApplicationScoped;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

@ApplicationScoped
public class ShellTools {

    private static final long DEFAULT_TIMEOUT_SECONDS = 600;
    // After /bin/sh exits, give drain threads this long to consume any buffered
    // output before we close the pipes. Anything still holding the pipes (e.g.
    // a process backgrounded with `&` that inherited stdout) gets cut off here
    // — without this cap, drain threads block forever and the tool never
    // returns, hanging the entire AI service until curl --max-time fires.
    private static final long DRAIN_GRACE_MILLIS = 2000;

    @Tool("""
            Run a shell command and return its stdout, stderr, and exit code. The command is executed via
            `/bin/sh -c <command>`. Use this for kubectl/helm/curl/jq and any other CLI needed by the active skill.
            Times out after 10 minutes.

            To run a long-lived process (kubectl port-forward, log followers, watchers, etc.) without
            blocking, redirect its output and disown it, e.g.:
              `nohup kubectl port-forward svc/foo 8080:80 > /tmp/pf.log 2>&1 < /dev/null & disown`
            Plain `cmd &` will hang because the backgrounded child inherits this tool's stdout pipe.
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
            // Kill /bin/sh and any descendants the LLM may have spawned (helm, kubectl, etc.)
            // before they can keep the pipes open and stall drain.
            killTree(process);
            joinDrainWithGrace(outThread, errThread, process);
            return "ERROR: command timed out after " + DEFAULT_TIMEOUT_SECONDS + "s\n"
                    + "STDOUT:\n" + stdout + "\nSTDERR:\n" + stderr;
        }

        // /bin/sh exited cleanly, but a backgrounded `cmd &` may still hold the
        // stdout/stderr pipes open, blocking drain. Give the drain threads a
        // short grace window, then force-close to release the threads. Any
        // surviving descendant keeps running detached but does not block this
        // tool from returning.
        joinDrainWithGrace(outThread, errThread, process);

        return "exit_code=" + process.exitValue() + "\n"
                + "STDOUT:\n" + stdout + "\n"
                + "STDERR:\n" + stderr;
    }

    private static void joinDrainWithGrace(Thread outThread, Thread errThread, Process process)
            throws InterruptedException {
        outThread.join(DRAIN_GRACE_MILLIS);
        errThread.join(DRAIN_GRACE_MILLIS);
        if (outThread.isAlive() || errThread.isAlive()) {
            // Force-close the stdio pipes so the readers in drain() get EOF
            // (or IOException) and exit. Without this, a backgrounded child
            // still holding fd 1/2 would keep our readLine() loops blocked.
            try { process.getInputStream().close(); } catch (IOException ignored) {}
            try { process.getErrorStream().close(); } catch (IOException ignored) {}
            outThread.join(DRAIN_GRACE_MILLIS);
            errThread.join(DRAIN_GRACE_MILLIS);
        }
    }

    private static void killTree(Process process) {
        process.descendants().forEach(ProcessHandle::destroyForcibly);
        process.destroyForcibly();
    }

    private static void drain(java.io.InputStream in, StringBuilder sink) {
        try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sink.append(line).append('\n');
            }
        } catch (IOException ignored) {
            // Process termination and stream-close races are expected.
        }
    }
}
