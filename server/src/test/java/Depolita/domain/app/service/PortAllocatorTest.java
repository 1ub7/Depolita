package Depolita.domain.app.service;

import Depolita.domain.app.exception.PortExhaustedException;
import Depolita.domain.app.repository.AppRepository;
import Depolita.infra.HostPortChecker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PortAllocatorTest {
    private final AppRepository appRepository = mock(AppRepository.class);
    private final HostPortChecker hostPortChecker = mock(HostPortChecker.class);
    private final PortAllocator allocator = new PortAllocator(appRepository, hostPortChecker);

    @BeforeEach
    void setUp() {
        when(appRepository.findAllAssignedPorts()).thenReturn(List.of());
        when(hostPortChecker.isAvailable(anyInt())).thenReturn(true);
    }

    @Test
    @DisplayName("DB에 기록된 포트와 컨테이너가 쓰는 포트를 모두 피한다")
    void skipsDbAndContainerPorts() {
        when(appRepository.findAllAssignedPorts()).thenReturn(List.of(10001));

        assertEquals(10003, allocator.allocate(Set.of(10002)));
    }

    @Test
    @DisplayName("DB에서는 반납됐지만 컨테이너가 아직 잡고 있는 포트는 주지 않는다")
    void skipsPortStillHeldByContainer() {
        assertEquals(10002, allocator.allocate(Set.of(10001)));
    }

    @Test
    @DisplayName("Depolita와 무관한 프로세스가 쓰는 포트는 건너뛴다 (같은 포트로 반복 실패하지 않음)")
    void skipsPortUsedByOtherProcess() {
        when(hostPortChecker.isAvailable(10001)).thenReturn(false);

        assertEquals(10002, allocator.allocate(Set.of()));
    }

    @Test
    @DisplayName("이미 쓰는 줄 아는 포트는 소켓을 열어 확인하지 않는다")
    void doesNotProbeKnownPorts() {
        when(appRepository.findAllAssignedPorts()).thenReturn(List.of(10001));

        allocator.allocate(Set.of());

        verify(hostPortChecker, never()).isAvailable(10001);
    }

    @Test
    @DisplayName("범위 안의 포트를 모두 쓰면 PortExhaustedException")
    void exhausted() {
        when(appRepository.findAllAssignedPorts())
                .thenReturn(IntStream.rangeClosed(10001, 10999).boxed().toList());

        assertThrows(PortExhaustedException.class, () -> allocator.allocate(Set.of()));
    }
}
