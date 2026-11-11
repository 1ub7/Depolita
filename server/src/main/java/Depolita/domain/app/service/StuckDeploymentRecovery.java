package Depolita.domain.app.service;

import Depolita.domain.app.entity.App;
import Depolita.domain.app.entity.AppStatus;
import Depolita.domain.app.repository.AppRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class StuckDeploymentRecovery {
    private final AppRepository appRepository;

    @Transactional
    @EventListener(ApplicationReadyEvent.class)
    public void failInterruptedDeployments() {
        List<App> stuck = appRepository.findAllByStatusIn(List.of(AppStatus.CLONING, AppStatus.BUILDING));
        stuck.forEach(app -> app.fail("서버 재시작으로 배포가 중단되었습니다. 다시 배포해주세요."));
        if (!stuck.isEmpty()) {
            log.warn("[recovery] 중단된 배포 {}건을 FAILED로 변경", stuck.size());
        }
    }
}
