package Depolita.domain.app.repository;

import Depolita.domain.app.entity.App;
import Depolita.domain.app.entity.AppStatus;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface AppRepository extends JpaRepository<App, Long> {
    boolean existsByName(String name);
    List<App> findAllByOrderByCreatedAtDesc();
    List<App> findAllByStatusIn(Collection<AppStatus> statuses);
    @Query("select a.assignedPort from App a where a.assignedPort is not null")
    List<Integer> findAllAssignedPorts();
}
