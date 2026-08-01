package com.qaforge.infrastructure.jira;

import com.qaforge.domain.model.AcceptanceCriteria;
import com.qaforge.domain.model.PullRequest;
import com.qaforge.domain.port.out.AcceptanceCriteriaPort;
import com.qaforge.infrastructure.jira.dto.JiraIssueResponse;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Implements {@link AcceptanceCriteriaPort} against the JIRA REST API (PRD §12.10 G-12).
 * Active only when {@code qaforge.jira.enabled=true}; see {@link NoopAcceptanceCriteriaPort}
 * for the default no-JIRA case.
 *
 * <p>Uses the JIRA v2 issue API, which returns a plain-text {@code description}. JIRA Cloud's
 * v3 API instead returns Atlassian Document Format (rich JSON) for the same field — parsing
 * that is out of scope for this pass; see docs/IMPLEMENTATION_STATUS.md.
 */
@Component
@ConditionalOnProperty(name = "qaforge.jira.enabled", havingValue = "true")
public class JiraAcAdapter implements AcceptanceCriteriaPort {

    private static final Logger log = LoggerFactory.getLogger(JiraAcAdapter.class);
    private static final Pattern ISSUE_KEY_PATTERN = Pattern.compile("[A-Z][A-Z0-9]+-\\d+");

    private final RestClient restClient;

    public JiraAcAdapter(RestClient.Builder restClientBuilder, JiraProperties properties) {
        this.restClient = restClientBuilder
            .baseUrl(properties.baseUrl())
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.token())
            .defaultHeader(HttpHeaders.ACCEPT, "application/json")
            .build();
    }

    @Override
    public Optional<AcceptanceCriteria> fetch(PullRequest pr) {
        Optional<String> issueKey = extractIssueKey(pr);
        if (issueKey.isEmpty()) {
            return Optional.empty();
        }

        try {
            JiraIssueResponse issue = restClient.get()
                .uri("/rest/api/2/issue/{key}", issueKey.get())
                .retrieve()
                .body(JiraIssueResponse.class);

            if (issue == null || issue.fields() == null || issue.fields().description() == null) {
                return Optional.empty();
            }

            String rawText = issue.fields().description();
            return Optional.of(new AcceptanceCriteria("jira", rawText, parseCriteria(rawText)));
        } catch (RestClientException e) {
            log.warn("Failed to fetch JIRA issue {} for PR {}: {}", issueKey.get(), pr.id(), e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<String> extractIssueKey(PullRequest pr) {
        for (String candidate : List.of(
                pr.title() == null ? "" : pr.title(),
                pr.headBranch() == null ? "" : pr.headBranch())) {
            Matcher matcher = ISSUE_KEY_PATTERN.matcher(candidate);
            if (matcher.find()) {
                return Optional.of(matcher.group());
            }
        }
        return Optional.empty();
    }

    private List<String> parseCriteria(String rawText) {
        List<String> bulletLines = rawText.lines()
            .map(String::strip)
            .filter(line -> line.startsWith("-") || line.startsWith("*") || line.matches("^\\d+[.).].*"))
            .toList();
        return bulletLines.isEmpty() ? List.of(rawText.strip()) : bulletLines;
    }
}
