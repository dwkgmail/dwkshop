package com.dwkshop.backend.admin;

import com.dwkshop.backend.audit.AdminOperationLogService;
import com.dwkshop.backend.audit.dto.AdminOperationLogResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin")
public class AdminOperationLogController {

    private final AdminOperationLogService operationLogService;

    public AdminOperationLogController(AdminOperationLogService operationLogService) {
        this.operationLogService = operationLogService;
    }

    @GetMapping("/operation-logs")
    public List<AdminOperationLogResponse> operationLogs() {
        return operationLogService.listRecent();
    }
}
