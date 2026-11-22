package Depolita.global.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ApiKeyStrengthCheckTest {

    @Test
    @DisplayName("빈 키 → 기동 실패")
    void emptyKey() {
        assertThrows(IllegalStateException.class, () -> ApiKeyStrengthCheck.validate(""));
    }

    @Test
    @DisplayName("31바이트 키 → 기동 실패")
    void shortKey() {
        assertThrows(IllegalStateException.class, () -> ApiKeyStrengthCheck.validate("a".repeat(31)));
    }

    @Test
    @DisplayName("32바이트 키 → 통과")
    void longEnoughKey() {
        assertDoesNotThrow(() -> ApiKeyStrengthCheck.validate("a".repeat(32)));
    }
}
