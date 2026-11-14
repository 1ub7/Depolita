package Depolita.infra;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HostPortCheckerTest {
    private final HostPortChecker checker = new HostPortChecker();

    @Test
    @DisplayName("다른 프로세스가 열어 둔 포트는 사용할 수 없다고 판단한다")
    void occupiedPort() throws IOException {
        try (ServerSocket occupied = new ServerSocket(0)) { // OS가 비어 있는 포트를 골라 줌
            assertFalse(checker.isAvailable(occupied.getLocalPort()));
        }
    }

    @Test
    @DisplayName("아무도 쓰지 않는 포트는 사용할 수 있다고 판단한다")
    void freePort() throws IOException {
        int port;
        try (ServerSocket probe = new ServerSocket(0)) {
            port = probe.getLocalPort();
        }

        assertTrue(checker.isAvailable(port));
    }
}