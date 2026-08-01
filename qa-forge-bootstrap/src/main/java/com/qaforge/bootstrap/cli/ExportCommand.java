package com.qaforge.bootstrap.cli;

import com.qaforge.domain.exception.QaForgeException;
import com.qaforge.domain.port.out.TestFileStorePort;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.shell.core.command.annotation.Command;
import org.springframework.shell.core.command.annotation.CommandGroup;
import org.springframework.shell.core.command.annotation.Option;
import org.springframework.stereotype.Component;

/**
 * {@code qa export} — required by PRD §12.10 but not listed under the {@code cli/} files in
 * §8's project structure (which also omits {@code VersionCommand}); added to fully satisfy
 * the documented CLI contract.
 */
@Component
@CommandGroup(name = "qa", description = "QA Forge commands")
public class ExportCommand {

    private final TestFileStorePort testFileStorePort;

    public ExportCommand(TestFileStorePort testFileStorePort) {
        this.testFileStorePort = testFileStorePort;
    }

    @Command(name = {"qa", "export"}, description = "Export a repository's active test suite as a ZIP")
    public String qaExport(
            @Option(longName = "repo", required = true, description = "repositoryFullName") String repo,
            @Option(longName = "output", required = true, description = "where to save the ZIP") String output) {

        byte[] zip = testFileStorePort.exportZip(repo);
        try {
            Files.write(Path.of(output), zip);
        } catch (IOException e) {
            throw new QaForgeException("Failed to write export ZIP to " + output, e);
        }
        return "Exported %d bytes to %s".formatted(zip.length, output);
    }
}
