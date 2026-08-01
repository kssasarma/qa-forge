package com.qaforge.bootstrap.cli;

import org.springframework.context.annotation.Configuration;
import org.springframework.shell.core.command.annotation.EnableCommand;

/** Registers the {@code qa} command group's methods as Spring Shell 4.0.3 commands. */
@Configuration
@EnableCommand({
    AnalyzeCommand.class,
    RegressionCommand.class,
    ListCommand.class,
    RunCommand.class,
    ExportCommand.class,
    VersionCommand.class
})
public class CliConfig {
}
