package com.dwkshop.backend.admin;

import com.dwkshop.backend.admin.dto.AdminOperationLogResponse;
import com.dwkshop.backend.auth.AuthContext;
import com.dwkshop.backend.auth.AuthPrincipal;
import com.dwkshop.backend.domain.entity.AdminOperationLog;
import com.dwkshop.backend.domain.repository.AdminOperationLogRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class AdminOperationLogService {

    private final AdminOperationLogRepository adminOperationLogRepository;

    public AdminOperationLogService(AdminOperationLogRepository adminOperationLogRepository) {
        this.adminOperationLogRepository = adminOperationLogRepository;
    }

    public void record(String module, String action, String targetType, Long targetId, String detail) {
        AuthPrincipal principal = AuthContext.current().orElse(null);
        AdminOperationLog log = new AdminOperationLog();
        log.setAdminUserId(principal == null ? null : principal.id());
        log.setAdminUsername(principal == null ? "system" : principal.subject());
        log.setModule(module);
        log.setAction(action);
        log.setTargetType(targetType);
        log.setTargetId(targetId);
        log.setDetail(detail == null || detail.isBlank() ? "-" : detail);
        log.setCreatedAt(LocalDateTime.now());
        adminOperationLogRepository.save(log);
    }

    public List<AdminOperationLogResponse> listRecent() {
        return adminOperationLogRepository.findTop100ByOrderByIdDesc().stream()
            .map(log -> new AdminOperationLogResponse(
                log.getId(),
                log.getAdminUserId(),
                log.getAdminUsername(),
                log.getModule(),
                log.getAction(),
                log.getTargetType(),
                log.getTargetId(),
                log.getDetail(),
                log.getCreatedAt()
            ))
            .toList();
    }
}
