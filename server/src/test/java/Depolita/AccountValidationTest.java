package Depolita;

import Depolita.domain.account.config.AllowedAccountProperties;
import Depolita.domain.account.exception.InvalidRepositoryUrlException;
import Depolita.domain.account.exception.NotAllowedAccountException;
import Depolita.domain.account.service.AccountValidator;
import Depolita.domain.account.support.GithubRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AccountValidationTest {

    @Test
    @DisplayName("URL에서 owner(소문자)와 저장소 이름을 꺼내고 .git을 뗀다")
    void parse() {
        GithubRepository repo = GithubRepository.parse("https://github.com/1UB7/Depolita.git");

        assertEquals("1ub7", repo.owner());
        assertEquals("Depolita", repo.name());
        assertEquals("https://github.com/1ub7/Depolita.git", repo.cloneUrl());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://gitlab.com/1ub7/Depolita",
            "http://github.com/1ub7/Depolita",
            "https://github.com/1ub7/.",
            "https://github.com/1ub7/..",
            "https://github.com/1ub7"
    })
    @DisplayName("GitHub HTTPS 저장소 주소가 아니면 거절한다")
    void rejectInvalidUrl(String url) {
        assertThrows(InvalidRepositoryUrlException.class, () -> GithubRepository.parse(url));
    }

    @Test
    @DisplayName("허용 목록에 있는 계정은 통과 (대소문자 무시)")
    void allowedAccount() {
        AccountValidator validator = new AccountValidator(new AllowedAccountProperties(Set.of(" 1UB7 ")));

        assertDoesNotThrow(() -> validator.validate("https://github.com/1ub7/Depolita"));
    }

    @Test
    @DisplayName("허용 목록에 없는 계정은 거절")
    void notAllowedAccount() {
        AccountValidator validator = new AccountValidator(new AllowedAccountProperties(Set.of("1ub7")));

        assertThrows(NotAllowedAccountException.class,
                () -> validator.validate("https://github.com/someone/repo"));
    }

    @Test
    @DisplayName("ALLOWED_ACCOUNTS=* 이면 모든 계정 허용")
    void allowAll() {
        AccountValidator validator = new AccountValidator(new AllowedAccountProperties(Set.of(" * ")));

        assertDoesNotThrow(() -> validator.validate("https://github.com/someone/repo"));
        assertDoesNotThrow(() -> validator.validate("https://github.com/1ub7/Depolita"));
    }

    @Test
    @DisplayName("* 이어도 GitHub 저장소 주소 형식은 그대로 검사")
    void allowAllStillValidatesUrl() {
        AccountValidator validator = new AccountValidator(new AllowedAccountProperties(Set.of("*")));

        assertThrows(InvalidRepositoryUrlException.class,
                () -> validator.validate("https://gitlab.com/someone/repo"));
    }

    @Test
    @DisplayName("설정이 비어 있어도 NPE 없이 모두 거절")
    void emptyConfig() {
        AccountValidator validator = new AccountValidator(new AllowedAccountProperties(null));

        assertThrows(NotAllowedAccountException.class,
                () -> validator.validate("https://github.com/1ub7/Depolita"));
    }
}
