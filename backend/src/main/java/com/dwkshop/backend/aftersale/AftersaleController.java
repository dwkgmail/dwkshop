package com.dwkshop.backend.aftersale;

import com.dwkshop.backend.aftersale.dto.AftersaleResponse;
import com.dwkshop.backend.aftersale.dto.CreateAftersaleRequest;
import com.dwkshop.backend.auth.AuthContext;
import com.dwkshop.backend.auth.AuthException;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/aftersales")
public class AftersaleController {

    private final AftersaleService aftersaleService;

    public AftersaleController(AftersaleService aftersaleService) {
        this.aftersaleService = aftersaleService;
    }

    @PostMapping
    public AftersaleResponse create(
        @Valid @RequestBody CreateAftersaleRequest request
    ) {
        return aftersaleService.create(currentUserId(), request);
    }

    @GetMapping
    public List<AftersaleResponse> list() {
        return aftersaleService.listUser(currentUserId());
    }

    @GetMapping("/{id}")
    public AftersaleResponse detail(@PathVariable Long id) {
        return aftersaleService.getUser(currentUserId(), id);
    }

    private Long currentUserId() {
        return AuthContext.currentUserId().orElseThrow(() -> new AuthException("please login first"));
    }
}
