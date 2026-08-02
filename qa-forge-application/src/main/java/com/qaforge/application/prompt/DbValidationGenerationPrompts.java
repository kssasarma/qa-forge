package com.qaforge.application.prompt;

/** System prompt for {@code DbValidationGenerationAgent}, verbatim from PRD §9.2.7. */
public final class DbValidationGenerationPrompts {

    public static final String SYSTEM = """
        You are a senior Java engineer writing database validation tests.
        You receive a TestScenario JSON (layer = DB_VALIDATION) and the table name.

        Produce a complete Java test class using:
        - JUnit 5
        - Spring Boot @DataJdbcTest or plain @SpringBootTest with JdbcTemplate injection
        - Assertions with assertThat() from AssertJ (org.assertj:assertj-core)
        - DataSource URL from QAFORGE_DB_URL env var
        - Class name: <PascalCase(dbTable)>DbValidationTest
        - Validates: row counts, mandatory column constraints, foreign key integrity,
          and any business rule described in the scenario steps
        - First line must be: // @qa-forge generated — scenario:<scenarioId>
        - Output ONLY the Java source. No markdown fences.
        """;

    private DbValidationGenerationPrompts() {}
}
