package com.dwkshop.backend.aftersale;

import com.dwkshop.backend.aftersale.dto.AftersaleResponse;
import com.dwkshop.backend.aftersale.dto.CreateAftersaleRequest;
import com.dwkshop.backend.auth.AuthContext;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/aftersales")
public class AftersaleController {

    private static final Long DEFAULT_USER_ID = 1L;

    private final AftersaleService aftersaleService;

    public AftersaleController(AftersaleService aftersaleService) {
        this.aftersaleService = aftersaleService;
    }

    @PostMapping
    public AftersaleResponse create(
        @RequestParam(required = false) Long userId,
        @Valid @RequestBody CreateAftersaleRequest request
    ) {
        return aftersaleService.create(resolveUserId(userId), request);
    }

    @GetMapping
    public List<AftersaleResponse> list(@RequestParam(required = false) Long userId) {
        return aftersaleService.listUser(resolveUserId(userId));
    }

    @GetMapping("/{id}")
    public AftersaleResponse detail(@PathVariable Long id, @RequestParam(required = false) Long userId) {
        return aftersaleService.getUser(resolveUserId(userId), id);
    }

    @PostMapping("/{id}/cancel")
    public AftersaleResponse cancel(@PathVariable Long id, @RequestParam(required = false) Long userId) {
        return aftersaleService.cancel(resolveUserId(userId), id);
    }

    private Long resolveUserId(Long userId) {
        if (userId != null) {
            return userId;
        }
        return AuthContext.currentUserId().orElse(DEFAULT_USER_ID);
    }
}
