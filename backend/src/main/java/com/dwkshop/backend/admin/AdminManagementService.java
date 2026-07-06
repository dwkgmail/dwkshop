package com.dwkshop.backend.admin;

import com.dwkshop.backend.admin.dto.AdminAccountResponse;
import com.dwkshop.backend.admin.dto.AdminAssignRoleRequest;
import com.dwkshop.backend.admin.dto.AdminCreateUserRequest;
import com.dwkshop.backend.admin.dto.AdminCouponRequest;
import com.dwkshop.backend.admin.dto.AdminCouponResponse;
import com.dwkshop.backend.admin.dto.AdminRoleResponse;
import com.dwkshop.backend.admin.dto.AdminStatusRequest;
import com.dwkshop.backend.admin.dto.AdminUserResponse;
import com.dwkshop.backend.auth.PasswordHasher;
import com.dwkshop.backend.domain.entity.AdminRole;
import com.dwkshop.backend.domain.entity.AdminUser;
import com.dwkshop.backend.domain.entity.AdminUserRole;
import com.dwkshop.backend.domain.entity.Coupon;
import com.dwkshop.backend.domain.entity.User;
import com.dwkshop.backend.domain.entity.UserPointAccount;
import com.dwkshop.backend.domain.repository.AdminRoleRepository;
import com.dwkshop.backend.domain.repository.AdminUserRepository;
import com.dwkshop.backend.domain.repository.AdminUserRoleRepository;
import com.dwkshop.backend.domain.repository.CouponRepository;
import com.dwkshop.backend.domain.repository.CouponUserRepository;
import com.dwkshop.backend.domain.repository.TradeOrderRepository;
import com.dwkshop.backend.domain.repository.UserPointAccountRepository;
import com.dwkshop.backend.domain.repository.UserRepository;
import com.dwkshop.backend.product.PriceFormatter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminManagementService {

    private final UserRepository userRepository;
    private final UserPointAccountRepository pointAccountRepository;
    private final TradeOrderRepository tradeOrderRepository;
    private final CouponUserRepository couponUserRepository;
    private final CouponRepository couponRepository;
    private final AdminUserRepository adminUserRepository;
    private final AdminRoleRepository adminRoleRepository;
    private final AdminUserRoleRepository adminUserRoleRepository;
    private final AdminOperationLogService operationLogService;
    private final PasswordHasher passwordHasher;

    public AdminManagementService(
        UserRepository userRepository,
        UserPointAccountRepository pointAccountRepository,
        TradeOrderRepository tradeOrderRepository,
        CouponUserRepository couponUserRepository,
        CouponRepository couponRepository,
        AdminUserRepository adminUserRepository,
        AdminRoleRepository adminRoleRepository,
        AdminUserRoleRepository adminUserRoleRepository,
        AdminOperationLogService operationLogService,
        PasswordHasher passwordHasher
    ) {
        this.userRepository = userRepository;
        this.pointAccountRepository = pointAccountRepository;
        this.tradeOrderRepository = tradeOrderRepository;
        this.couponUserRepository = couponUserRepository;
        this.couponRepository = couponRepository;
        this.adminUserRepository = adminUserRepository;
        this.adminRoleRepository = adminRoleRepository;
        this.adminUserRoleRepository = adminUserRoleRepository;
        this.operationLogService = operationLogService;
        this.passwordHasher = passwordHasher;
    }

    @Transactional
    public AdminUserResponse createUser(AdminCreateUserRequest request) {
        String mobile = request.mobile().trim();
        if (userRepository.existsByMobile(mobile)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mobile already exists");
        }
        User user = new User();
        user.setId(nextUserId());
        user.setMobile(mobile);
        user.setNickname(resolveNickname(request.nickname(), mobile));
        user.setPasswordHash(passwordHasher.hash(request.password().trim()));
        user.setStatus(request.status() == null || request.status().isBlank() ? "ACTIVE" : normalizeStatus(request.status()));
        LocalDateTime now = LocalDateTime.now();
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        User saved = userRepository.save(user);
        operationLogService.record("USER_CREATE", "USER", saved.getId(), null, snapshot("mobile", saved.getMobile()), "鍒涘缓鐢ㄦ埛");
        return toUserResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<AdminUserResponse> listUsers() {
        return userRepository.findAll().stream()
            .sorted((left, right) -> Long.compare(right.getId(), left.getId()))
            .map(this::toUserResponse)
            .toList();
    }

    @Transactional
    public AdminUserResponse updateUserStatus(Long id, AdminStatusRequest request) {
        User user = userRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        String beforeStatus = user.getStatus();
        user.setStatus(normalizeStatus(request.status()));
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);
        operationLogService.record("USER_STATUS_UPDATE", "USER", id, snapshot("status", beforeStatus), snapshot("status", user.getStatus()), "更新用户状态");
        return toUserResponse(user);
    }

    @Transactional(readOnly = true)
    public List<AdminCouponResponse> listCoupons() {
        return couponRepository.findAll().stream()
            .sorted((left, right) -> Long.compare(right.getId(), left.getId()))
            .map(this::toCouponResponse)
            .toList();
    }

    @Transactional
    public AdminCouponResponse createCoupon(AdminCouponRequest request) {
        Coupon coupon = new Coupon();
        fillCoupon(coupon, request);
        coupon.setCouponCode(resolveCouponCode(request.couponCode()));
        coupon.setReceivedQuantity(0);
        coupon.setUsedQuantity(0);
        coupon.setCreatedAt(LocalDateTime.now());
        coupon.setUpdatedAt(LocalDateTime.now());
        Coupon saved = couponRepository.save(coupon);
        operationLogService.record("COUPON_CREATE", "COUPON", saved.getId(), null, snapshot(saved), "创建优惠券");
        return toCouponResponse(saved);
    }

    @Transactional
    public AdminCouponResponse updateCouponStatus(Long id, AdminStatusRequest request) {
        Coupon coupon = couponRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Coupon not found"));
        String beforeStatus = coupon.getCouponStatus();
        coupon.setCouponStatus(normalizeStatus(request.status()));
        coupon.setUpdatedAt(LocalDateTime.now());
        couponRepository.save(coupon);
        operationLogService.record("COUPON_STATUS_UPDATE", "COUPON", id, snapshot("status", beforeStatus), snapshot("status", coupon.getCouponStatus()), "更新优惠券状态");
        return toCouponResponse(coupon);
    }

    @Transactional(readOnly = true)
    public List<AdminRoleResponse> listRoles() {
        return adminRoleRepository.findAll().stream()
            .sorted((left, right) -> Long.compare(left.getId(), right.getId()))
            .map(this::toRoleResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<AdminAccountResponse> listAdminAccounts() {
        Map<Long, AdminRole> roles = adminRoleRepository.findAll().stream()
            .collect(Collectors.toMap(AdminRole::getId, Function.identity()));
        return adminUserRepository.findAll().stream()
            .sorted((left, right) -> Long.compare(left.getId(), right.getId()))
            .map(admin -> toAdminAccountResponse(admin, roles))
            .toList();
    }

    @Transactional
    public AdminAccountResponse assignRole(Long adminUserId, AdminAssignRoleRequest request) {
        AdminUser admin = adminUserRepository.findById(adminUserId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Admin user not found"));
        AdminRole role = adminRoleRepository.findById(request.roleId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Role not found"));
        AdminUserRole relation = adminUserRoleRepository.findFirstByAdminUserId(adminUserId).orElse(null);
        Long beforeRoleId = relation == null ? null : relation.getRoleId();
        if (relation == null) {
            relation = new AdminUserRole();
            relation.setAdminUserId(adminUserId);
            relation.setCreatedAt(LocalDateTime.now());
        }
        relation.setRoleId(role.getId());
        adminUserRoleRepository.save(relation);
        operationLogService.record("ADMIN_ROLE_ASSIGN", "ADMIN_USER", adminUserId, snapshot("roleId", beforeRoleId), snapshot("roleId", role.getId()), "分配管理员角色");
        return toAdminAccountResponse(admin, Map.of(role.getId(), role));
    }

    @Transactional
    public AdminAccountResponse updateAdminStatus(Long adminUserId, AdminStatusRequest request) {
        AdminUser admin = adminUserRepository.findById(adminUserId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Admin user not found"));
        String beforeStatus = admin.getStatus();
        admin.setStatus(normalizeStatus(request.status()));
        admin.setUpdatedAt(LocalDateTime.now());
        adminUserRepository.save(admin);
        operationLogService.record("ADMIN_STATUS_UPDATE", "ADMIN_USER", adminUserId, snapshot("status", beforeStatus), snapshot("status", admin.getStatus()), "更新管理员状态");
        Map<Long, AdminRole> roles = adminRoleRepository.findAll().stream().collect(Collectors.toMap(AdminRole::getId, Function.identity()));
        return toAdminAccountResponse(admin, roles);
    }

    private AdminUserResponse toUserResponse(User user) {
        UserPointAccount account = pointAccountRepository.findByUserId(user.getId()).orElse(null);
        return new AdminUserResponse(
            user.getId(),
            user.getMobile(),
            user.getNickname(),
            user.getStatus(),
            account == null ? 0 : account.getAvailablePoints(),
            account == null ? 0 : account.getLockedPoints(),
            tradeOrderRepository.countByUserId(user.getId()),
            couponUserRepository.countByUserId(user.getId()),
            user.getCreatedAt()
        );
    }

    private AdminCouponResponse toCouponResponse(Coupon coupon) {
        return new AdminCouponResponse(
            coupon.getId(),
            coupon.getCouponCode(),
            coupon.getName(),
            coupon.getCouponType(),
            coupon.getThresholdAmount(),
            PriceFormatter.formatCents(coupon.getThresholdAmount()),
            coupon.getDiscountAmount(),
            PriceFormatter.formatCents(coupon.getDiscountAmount()),
            coupon.getDiscountRate(),
            coupon.getTotalQuantity(),
            coupon.getReceivedQuantity(),
            coupon.getUsedQuantity(),
            coupon.getReceiveStartTime(),
            coupon.getReceiveEndTime(),
            coupon.getUseStartTime(),
            coupon.getUseEndTime(),
            coupon.getCouponStatus()
        );
    }

    private AdminRoleResponse toRoleResponse(AdminRole role) {
        return new AdminRoleResponse(role.getId(), role.getRoleCode(), role.getRoleName(), role.getPermissions(), role.getStatus());
    }

    private AdminAccountResponse toAdminAccountResponse(AdminUser admin, Map<Long, AdminRole> roles) {
        AdminUserRole relation = adminUserRoleRepository.findFirstByAdminUserId(admin.getId()).orElse(null);
        AdminRole role = relation == null ? null : roles.get(relation.getRoleId());
        return new AdminAccountResponse(
            admin.getId(),
            admin.getUsername(),
            admin.getDisplayName(),
            admin.getStatus(),
            role == null ? null : role.getId(),
            role == null ? null : role.getRoleName(),
            admin.getCreatedAt()
        );
    }

    private void fillCoupon(Coupon coupon, AdminCouponRequest request) {
        coupon.setName(request.name().trim());
        coupon.setCouponType(request.couponType().trim());
        coupon.setThresholdAmount(request.thresholdAmount());
        coupon.setDiscountAmount(request.discountAmount());
        coupon.setDiscountRate(request.discountRate());
        coupon.setTotalQuantity(request.totalQuantity());
        coupon.setReceiveStartTime(request.receiveStartTime());
        coupon.setReceiveEndTime(request.receiveEndTime());
        coupon.setUseStartTime(request.useStartTime());
        coupon.setUseEndTime(request.useEndTime());
        coupon.setCouponStatus(normalizeStatus(request.couponStatus()));
    }

    private String resolveCouponCode(String requestedCode) {
        String code = requestedCode == null || requestedCode.isBlank()
            ? "C-ADMIN-" + DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(LocalDateTime.now())
            : requestedCode.trim();
        if (couponRepository.existsByCouponCode(code)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Coupon code already exists");
        }
        return code;
    }

    private String normalizeStatus(String status) {
        String normalized = status == null ? "" : status.trim().toUpperCase();
        if (!List.of("ACTIVE", "DISABLED", "ENABLED").contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported status");
        }
        return normalized;
    }

    private String resolveNickname(String nickname, String mobile) {
        return nickname == null || nickname.isBlank() ? "user" + mobile.substring(7) : nickname.trim();
    }

    private Long nextUserId() {
        return userRepository.findTopByOrderByIdDesc()
            .map(User::getId)
            .orElse(0L) + 1;
    }

    private Map<String, Object> snapshot(String key, Object value) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put(key, value);
        return snapshot;
    }

    private Map<String, Object> snapshot(Coupon coupon) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", coupon.getId());
        snapshot.put("couponCode", coupon.getCouponCode());
        snapshot.put("name", coupon.getName());
        snapshot.put("couponType", coupon.getCouponType());
        snapshot.put("thresholdAmount", coupon.getThresholdAmount());
        snapshot.put("discountAmount", coupon.getDiscountAmount());
        snapshot.put("discountRate", coupon.getDiscountRate());
        snapshot.put("totalQuantity", coupon.getTotalQuantity());
        snapshot.put("couponStatus", coupon.getCouponStatus());
        return snapshot;
    }
}
