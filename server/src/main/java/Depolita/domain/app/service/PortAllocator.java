package Depolita.domain.app.service;

import Depolita.domain.app.exception.PortExhaustedException;
import Depolita.domain.app.repository.AppRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class PortAllocator {
    private static final int MIN_PORT = 10001;
    private static final int MAX_PORT = 10999;

    private final AppRepository appRepository;

    public int allocate(Set<Integer> inUseOnHost) {
        Set<Integer> used = new HashSet<>(appRepository.findAllAssignedPorts());
        used.addAll(inUseOnHost);
        for (int port = MIN_PORT; port <= MAX_PORT; port++) {
            if (!used.contains(port)) {
                return port;
            }
        }
        throw new PortExhaustedException();
    }
}