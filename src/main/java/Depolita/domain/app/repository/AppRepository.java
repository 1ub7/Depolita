package Depolita.domain.app.repository;

import Depolita.domain.app.entity.App;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AppRepository extends JpaRepository<App, Long> {
    boolean existsByName(String name);
    List<App> findAllByOrderByCreatedAtDesc();
    @Query
    List<Integer> findAllAssignedPorts();
}
