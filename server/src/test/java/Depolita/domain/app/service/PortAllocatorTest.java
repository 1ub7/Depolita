package Depolita.domain.app.service;

import Depolita.domain.app.exception.PortExhaustedException;
import Depolita.domain.app.repository.AppRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PortAllocatorTest {
    private final AppRepository appRepository = mock(AppRepository.class);
    private final PortAllocator allocator = new PortAllocator(appRepository);

    @Test
    @DisplayName("DB에 기록된 포트와 호스트에서 실제로 쓰는 포트를 모두 피한다")
    void skipsDbAndHostPorts() {
        when(appRepository.findAllAssignedPorts()).thenReturn(List.of(10001));

        assertEquals(10003, allocator.allocate(Set.of(10002)));
    }

    @Test
    @DisplayName("DB에서는 반납됐지만 컨테이너가 아직 잡고 있는 포트는 주지 않는다")
    void skipsPortStillHeldByContainer() {
        when(appRepository.findAllAssignedPorts()).thenReturn(List.of());

        assertEquals(10002, allocator.allocate(Set.of(10001)));
    }

    @Test
    @DisplayName("범위 안의 포트를 모두 쓰면 PortExhaustedException")
    void exhausted() {
        when(appRepository.findAllAssignedPorts())
                .thenReturn(IntStream.rangeClosed(10001, 10999).boxed().toList());

        assertThrows(PortExhaustedException.class, () -> allocator.allocate(Set.of()));
    }
}
