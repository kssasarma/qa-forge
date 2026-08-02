package com.qaforge.infrastructure.jira;

import com.qaforge.domain.model.AcceptanceCriteria;
import com.qaforge.domain.model.PullRequest;
import com.qaforge.domain.port.out.AcceptanceCriteriaPort;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Default {@link AcceptanceCriteriaPort} when JIRA is disabled ({@code qaforge.jira.enabled=false},
 * the default). {@code ContextGatheringAgent} derives acceptance criteria from the PR title and
 * description in this case, per its own prompt rule.
 */
@Component
@ConditionalOnProperty(name = "qaforge.jira.enabled", havingValue = "false", matchIfMissing = true)
public class NoopAcceptanceCriteriaPort implements AcceptanceCriteriaPort {

    @Override
    public Optional<AcceptanceCriteria> fetch(PullRequest pr) {
        return Optional.empty();
    }
}
