package Depolita.domain.app.port;

public interface HostPortChecker {
    boolean isAvailable(int port);
}