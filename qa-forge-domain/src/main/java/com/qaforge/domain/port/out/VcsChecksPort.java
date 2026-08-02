package com.qaforge.domain.port.out;

import com.qaforge.domain.model.PullRequest;

/** Posts a status check to the originating VCS (GitHub Check Run / GitLab commit status). */
public interface VcsChecksPort {
    void postPending(PullRequest pr, String checkName, String detailsUrl);
    void postSuccess(PullRequest pr, String checkName, String summary);
    void postFailure(PullRequest pr, String checkName, String summary);
}
