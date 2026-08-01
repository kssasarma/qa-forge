package com.qaforge.bootstrap.cli;

import org.springframework.shell.core.command.annotation.Command;
import org.springframework.shell.core.command.annotation.CommandGroup;
import org.springframework.stereotype.Component;

/** {@code qa version} (PRD §12.10). */
@Component
@CommandGroup(name = "qa", prefix = "qa", description = "QA Forge commands")
public class VersionCommand {

    @Command(value = "version", description = "Print the QA Forge version")
    public String version() {
        String implementationVersion = getClass().getPackage().getImplementationVersion();
        return "QA Forge " + (implementationVersion == null ? "1.0.0-SNAPSHOT" : implementationVersion);
    }
}
