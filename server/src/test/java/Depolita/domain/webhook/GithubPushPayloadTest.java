package Depolita.domain.webhook;

import Depolita.domain.webhook.dto.GithubPushPayload;
import Depolita.domain.webhook.dto.GithubPushPayload.Repository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GithubPushPayloadTest {
    private static final Repository REPO = new Repository("https://github.com/1ub7/demo.git", "main");

    @Test
    @DisplayName("기본 브랜치 push 만 배포 대상이다")
    void defaultBranchOnly() {
        assertTrue(new GithubPushPayload("refs/heads/main", "abc", false, REPO).isDefaultBranchPush());
        assertFalse(new GithubPushPayload("refs/heads/feature", "abc", false, REPO).isDefaultBranchPush());
        assertFalse(new GithubPushPayload("refs/tags/v1.0", "abc", false, REPO).isDefaultBranchPush());
    }

    @Test
    @DisplayName("브랜치 삭제 push 는 배포하지 않는다")
    void deletedBranch() {
        assertFalse(new GithubPushPayload("refs/heads/main", "0000000", true, REPO).isDefaultBranchPush());
    }

    @Test
    @DisplayName("repository 정보가 없으면 배포하지 않는다")
    void missingRepository() {
        assertFalse(new GithubPushPayload("refs/heads/main", "abc", false, null).isDefaultBranchPush());
    }
}
