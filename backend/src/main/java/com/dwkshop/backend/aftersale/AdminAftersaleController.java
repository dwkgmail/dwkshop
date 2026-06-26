package com.dwkshop.backend.aftersale;

import com.dwkshop.backend.aftersale.dto.AftersaleResponse;
import com.dwkshop.backend.aftersale.dto.RejectAftersaleRequest;
import com.dwkshop.backend.aftersale.dto.RefundFailureRequest;
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

    @PostMapping("/{id}/refund/complete")
    public AftersaleResponse completeRefund(@PathVariable Long id) {
        return aftersaleService.completeRefund(id);
    }

    @PostMapping("/{id}/refund/fail")
    public AftersaleResponse failRefund(@PathVariable Long id, @RequestBody(required = false) RefundFailureRequest request) {
        return aftersaleService.failRefund(id, request);
    }

    @PostMapping("/{id}/refund/retry")
    public AftersaleResponse retryRefund(@PathVariable Long id) {
        return aftersaleService.retryRefund(id);
    }

    @PostMapping("/{id}/reject")
    public AftersaleResponse reject(@PathVariable Long id, @RequestBody(required = false) RejectAftersaleRequest request) {
        return aftersaleService.reject(id, request);
    }
}
