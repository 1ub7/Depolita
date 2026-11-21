package Depolita.domain.app.dto;

import Depolita.domain.app.entity.App;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppResponseTest {
    private TimeZone original;

    @BeforeEach
    void saveTimeZone() {
        original = TimeZone.getDefault();
    }

    @AfterEach
    void restoreTimeZone() {
        TimeZone.setDefault(original);
    }

    private App appCreatedAt(LocalDateTime time) {
        App app = App.create("my-app", "https://github.com/1ub7/demo.git", 8080, 10001);
        ReflectionTestUtils.setField(app, "createdAt", time);
        ReflectionTestUtils.setField(app, "updatedAt", time);
        return app;
    }

    @ParameterizedTest
    @ValueSource(strings = {"UTC", "Asia/Seoul", "America/New_York"})
    @DisplayName("서버 시간대와 상관없이, 방금 만든 앱의 생성 시각은 '지금'이다")
    void justCreatedIsNow(String zone) {
        TimeZone.setDefault(TimeZone.getTimeZone(zone));
        App app = appCreatedAt(LocalDateTime.now());

        AppResponse response = AppResponse.from(app, "localhost");

        Duration diff = Duration.between(response.createdAt(), Instant.now()).abs();
        assertTrue(diff.toSeconds() < 5, zone + "에서 " + diff + " 차이가 남");
    }

    @Test
    @DisplayName("UTC 서버에서 저장된 08:00은 2026-11-18T08:00:00Z로 나간다")
    void utcServer() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        App app = appCreatedAt(LocalDateTime.of(2026, 11, 18, 8, 0));

        assertEquals("2026-11-18T08:00:00Z", AppResponse.from(app, "localhost").createdAt().toString());
    }

    @Test
    @DisplayName("KST 서버에서 저장된 17:00도 같은 순간(08:00Z)으로 나간다")
    void kstServer() {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"));
        App app = appCreatedAt(LocalDateTime.of(2026, 11, 18, 17, 0));

        assertEquals("2026-11-18T08:00:00Z", AppResponse.from(app, "localhost").createdAt().toString());
    }

    @Test
    @DisplayName("마이크로초는 밀리초로 자른다 (JS Date 정밀도)")
    void truncatesToMillis() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        App app = appCreatedAt(LocalDateTime.of(2026, 11, 18, 8, 0, 0, 123_456_000));

        assertEquals("2026-11-18T08:00:00.123Z", AppResponse.from(app, "localhost").createdAt().toString());
    }

    @Test
    @DisplayName("시각이 없으면 null")
    void nullTime() {
        App app = appCreatedAt(null);

        assertNull(AppResponse.from(app, "localhost").createdAt());
    }
}