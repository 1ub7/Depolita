package Depolita.domain.app.controller;

import Depolita.domain.app.dto.AppLogResponse;
import Depolita.domain.app.service.AppService;
import Depolita.global.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AppControllerValidationTest {
    private final AppService appService = mock(AppService.class);
    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new AppController(appService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @ParameterizedTest
    @ValueSource(strings = {"0", "1001"})
    @DisplayName("tail이 1~1000을 벗어나면 400, @RequestBody 검증 실패와 같은 INVALID_INPUT 형식")
    void tailOutOfRange(String tail) throws Exception {
        mockMvc.perform(get("/api/apps/1/logs").param("tail", tail))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.errors[0].field").value("tail"))
                .andExpect(jsonPath("$.errors[0].reason").isNotEmpty());

        verifyNoInteractions(appService);
    }

    @Test
    @DisplayName("tail이 범위 안이면 서비스까지 호출된다")
    void tailInRange() throws Exception {
        when(appService.getLogs(1L, 100)).thenReturn(AppLogResponse.of(1L, List.of()));

        mockMvc.perform(get("/api/apps/1/logs").param("tail", "100"))
                .andExpect(status().isOk());

        verify(appService).getLogs(1L, 100);
    }
}