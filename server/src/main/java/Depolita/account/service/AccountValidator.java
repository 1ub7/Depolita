package Depolita.account.service;

import Depolita.account.config.AllowedAccountProperties;
import Depolita.account.exception.NotAllowedAccountException;
import Depolita.account.support.GithubRepository;
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
