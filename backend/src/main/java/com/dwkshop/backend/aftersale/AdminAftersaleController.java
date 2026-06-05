package com.dwkshop.backend.aftersale;

import com.dwkshop.backend.aftersale.dto.AftersaleResponse;
import com.dwkshop.backend.aftersale.dto.RejectAftersaleRequest;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/aftersales")
public class AdminAftersaleController {

    private final AftersaleService aftersaleService;

    public AdminAftersaleController(AftersaleService aftersaleService) {
        this.aftersaleService = aftersaleService;
    }

    @GetMapping
    public List<AftersaleResponse> list() {
        return aftersaleService.listAdmin();
    }

    @PostMapping("/{id}/approve")
    public AftersaleResponse approve(@PathVariable Long id) {
        return aftersaleService.approve(id);
    }

    @PostMapping("/{id}/reject")
    public AftersaleResponse reject(@PathVariable Long id, @RequestBody(required = false) RejectAftersaleRequest request) {
        return aftersaleService.reject(id, request);
    }
}
