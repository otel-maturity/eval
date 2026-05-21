package io.otel.maturity.agent.resource.quarkus.tools;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import jakarta.enterprise.context.ApplicationScoped;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@ApplicationScoped
public class FileSystemTools {

    @Tool("Read the entire contents of a UTF-8 text file at the given absolute or working-directory-relative path.")
    public String readFile(@P("File path to read") String path) throws IOException {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }

    @Tool("Write the given content to a UTF-8 text file, creating parent directories as needed. Overwrites any existing file at the path.")
    public String writeFile(@P("File path to write") String path,
                            @P("Content to write") String content) throws IOException {
        Path p = Path.of(path);
        Path parent = p.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(p, content, StandardCharsets.UTF_8);
        return "Wrote " + content.length() + " characters to " + path;
    }

    @Tool("Append the given content to a UTF-8 text file, creating it (and parent directories) if it does not exist.")
    public String appendFile(@P("File path to append to") String path,
                             @P("Content to append") String content) throws IOException {
        Path p = Path.of(path);
        Path parent = p.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(p, content, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        return "Appended " + content.length() + " characters to " + path;
    }

    @Tool("List the names of files and directories directly inside the given directory path (non-recursive).")
    public String listDirectory(@P("Directory path to list") String path) throws IOException {
        try (Stream<Path> entries = Files.list(Path.of(path))) {
            return entries
                    .map(p -> Files.isDirectory(p) ? p.getFileName() + "/" : p.getFileName().toString())
                    .sorted()
                    .collect(Collectors.joining("\n"));
        }
    }

    @Tool("Create a directory (and any missing parent directories) at the given path.")
    public String createDirectory(@P("Directory path to create") String path) throws IOException {
        Files.createDirectories(Path.of(path));
        return "Created directory: " + path;
    }

    @Tool("Check whether a file or directory exists at the given path. Returns 'true' or 'false'.")
    public String fileExists(@P("Path to check") String path) {
        return Boolean.toString(Files.exists(Path.of(path)));
    }
}
