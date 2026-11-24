package Depolita.domain.webhook.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * GitHub push 이벤트에서 필요한 필드만 받는다.
 * https://docs.github.com/en/webhooks/webhook-events-and-payloads#push
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GithubPushPayload(
        String ref,
        String after,
        boolean deleted,
        Repository repository
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Repository(
            @JsonProperty("clone_url") String cloneUrl,
            @JsonProperty("default_branch") String defaultBranch
    ) {
    }

    public boolean isDefaultBranchPush() {
        return !deleted
                && repository != null
                && repository.defaultBranch() != null
                && ("refs/heads/" + repository.defaultBranch()).equals(ref);
    }
}
