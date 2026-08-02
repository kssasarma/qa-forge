package com.qaforge.domain.port.out;

import com.qaforge.domain.model.AcceptanceCriteria;
import com.qaforge.domain.model.PullRequest;
import java.util.Optional;

public interface AcceptanceCriteriaPort {
    Optional<AcceptanceCriteria> fetch(PullRequest pr);
}
