package Depolita.domain.app.controller;

import Depolita.domain.app.dto.AppCreateRequest;
import Depolita.domain.app.dto.AppLogResponse;
import Depolita.domain.app.dto.AppResponse;
import Depolita.domain.app.service.AppService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/apps")
@RequiredArgsConstructor
public class AppController {
    private final AppService appService;

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public AppResponse deploy(@RequestBody @Valid AppCreateRequest request) {
        return appService.create(request);
    }

    @GetMapping
    public List<AppResponse> getApps() {
        return appService.getApps();
    }

    @GetMapping("/{appId}")
    public AppResponse getApp(@PathVariable Long appId) {
        return appService.getApp(appId);
    }

    @GetMapping("/{appId}/logs")
    public AppLogResponse getLogs(@PathVariable Long appId,
                                  @RequestParam(defaultValue = "100") @Min(1) @Max(1000) int tail) {
        return appService.getLogs(appId, tail);
    }

    @PostMapping("/{appId}/stop")
    public AppResponse stop(@PathVariable Long appId) {
        return appService.stop(appId);
    }

    @DeleteMapping("/{appId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long appId) {
        appService.delete(appId);
    }
}
