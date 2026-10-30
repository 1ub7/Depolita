package Depolita.domain.account.service;

import Depolita.domain.account.config.AllowedAccountProperties;
import Depolita.domain.account.exception.NotAllowedAccountException;
import Depolita.domain.account.support.GithubRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AccountValidator {
    private final AllowedAccountProperties properties;

    public GithubRepository validate(String url) {
        GithubRepository repository = GithubRepository.parse(url);

        if (!properties.allowedAccounts().contains(repository.owner())) {
            throw new NotAllowedAccountException();
        }
        return repository;
    }
}
