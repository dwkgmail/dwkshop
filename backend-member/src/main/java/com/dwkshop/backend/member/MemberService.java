package com.dwkshop.backend.member;

import com.dwkshop.backend.domain.entity.UserAddress;
import com.dwkshop.backend.domain.entity.PointFreeze;
import com.dwkshop.backend.domain.entity.UserPointAccount;
import com.dwkshop.backend.domain.entity.UserPointFlow;
import com.dwkshop.backend.audit.AdminOperationLogService;
import com.dwkshop.backend.domain.repository.PointFreezeRepository;
import com.dwkshop.backend.domain.repository.UserAddressRepository;
import com.dwkshop.backend.domain.repository.UserPointAccountRepository;
import com.dwkshop.backend.domain.repository.UserPointFlowRepository;
import com.dwkshop.backend.member.dto.MemberAddressResponse;
import com.dwkshop.backend.member.dto.MemberPointAccountResponse;
import com.dwkshop.backend.member.dto.MemberPointCommandRequest;
import java.time.LocalDateTime;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MemberService {

    private static final String SOURCE_ORDER = "ORDER";

    private final UserAddressRepository userAddressRepository;
    private final UserPointAccountRepository userPointAccountRepository;
    private final UserPointFlowRepository userPointFlowRepository;
    private final PointFreezeRepository pointFreezeRepository;
    private final AdminOperationLogService operationLogService;

    public MemberService(
        UserAddressRepository userAddressRepository,
        UserPointAccountRepository userPointAccountRepository,
        UserPointFlowRepository userPointFlowRepository,
        PointFreezeRepository pointFreezeRepository,
        AdminOperationLogService operationLogService
    ) {
        this.userAddressRepository = userAddressRepository;
        this.userPointAccountRepository = userPointAccountRepository;
        this.userPointFlowRepository = userPointFlowRepository;
        this.pointFreezeRepository = pointFreezeRepository;
        this.operationLogService = operationLogService;
    }

    @Transactional(readOnly = true)
    public MemberAddressResponse resolveAddress(Long userId, Long addressId) {
        UserAddress address = addressId == null
            ? userAddressRepository.findFirstByUserIdAndDefaultFlagTrue(userId)
                .or(() -> userAddressRepository.findFirstByUserIdOrderByIdAsc(userId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "请先添加收货地址"))
            : userAddressRepository.findByIdAndUserId(addressId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "收货地址不存在"));
        return toAddress(address);
    }

    @Transactional(readOnly = true)
    public MemberPointAccountResponse getPointAccount(Long userId) {
        UserPointAccount account = userPointAccountRepository.findByUserId(userId).orElse(null);
        return new MemberPointAccountResponse(userId, account == null ? 0 : account.getAvailablePoints());
    }

    @Transactional
    public MemberPointAccountResponse freezePoints(Long userId, MemberPointCommandRequest request) {
        return changePoints(userId, request, "POINT_FREEZE");
    }

    @Transactional
    public MemberPointAccountResponse deductFrozenPoints(Long userId, MemberPointCommandRequest request) {
        return changePoints(userId, request, "POINT_DEDUCT");
    }

    @Transactional
    public MemberPointAccountResponse releaseFrozenPoints(Long userId, MemberPointCommandRequest request) {
        return changePoints(userId, request, "POINT_RELEASE");
    }

    @Transactional
    public MemberPointAccountResponse refundPoints(Long userId, MemberPointCommandRequest request) {
        return changePoints(userId, request, "POINT_REFUND");
    }

    private MemberPointAccountResponse changePoints(Long userId, MemberPointCommandRequest request, String changeType) {
        String flowNo = request.bizNo() + ":" + changeType;
        if (userPointFlowRepository.existsByFlowNo(flowNo)) {
            UserPointAccount account = userPointAccountRepository.findByUserId(userId).orElse(null);
            return new MemberPointAccountResponse(userId, account == null ? 0 : account.getAvailablePoints());
        }
        UserPointAccount account = userPointAccountRepository.findLockedByUserId(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Point account does not exist"));
        int points = request.points();
        int before = account.getAvailablePoints();
        int beforeLocked = account.getLockedPoints();
        int after;
        if ("POINT_FREEZE".equals(changeType)) {
            if (before < points) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Insufficient points");
            }
            after = before - points;
            account.setAvailablePoints(after);
            account.setLockedPoints(account.getLockedPoints() + points);
        } else if ("POINT_DEDUCT".equals(changeType)) {
            if (account.getLockedPoints() < points) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Insufficient frozen points");
            }
            after = before;
            account.setLockedPoints(account.getLockedPoints() - points);
            account.setTotalUsedPoints(account.getTotalUsedPoints() + points);
        } else if ("POINT_RELEASE".equals(changeType)) {
            if (account.getLockedPoints() < points) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Insufficient frozen points");
            }
            after = before + points;
            account.setAvailablePoints(after);
            account.setLockedPoints(account.getLockedPoints() - points);
        } else if ("POINT_REFUND".equals(changeType)) {
            after = before + points;
            account.setAvailablePoints(after);
            account.setTotalUsedPoints(Math.max(account.getTotalUsedPoints() - points, 0));
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported point flow type");
        }
        LocalDateTime now = LocalDateTime.now();
        account.setUpdatedAt(now);
        userPointAccountRepository.save(account);
        updatePointFreeze(userId, request, changeType, points, before, after, beforeLocked, account.getLockedPoints(), now);
        userPointFlowRepository.save(toFlow(userId, request, flowNo, changeType, points, before, after, now));
        operationLogService.record(
            changeType,
            "POINT",
            userId,
            pointSnapshot(userId, before, beforeLocked),
            pointSnapshot(userId, after, account.getLockedPoints()),
            "积分调整"
        );
        return new MemberPointAccountResponse(userId, account.getAvailablePoints());
    }

    private void updatePointFreeze(
        Long userId,
        MemberPointCommandRequest request,
        String changeType,
        int points,
        int before,
        int after,
        int beforeLocked,
        int afterLocked,
        LocalDateTime now
    ) {
        if ("POINT_FREEZE".equals(changeType)) {
            PointFreeze freeze = pointFreezeRepository.findByBizNo(request.bizNo()).orElseGet(PointFreeze::new);
            freeze.setUserId(userId);
            freeze.setOrderId(request.orderId());
            freeze.setBizNo(request.bizNo());
            freeze.setSource(SOURCE_ORDER);
            freeze.setFreezePoints(points);
            freeze.setBeforeAvailablePoints(before);
            freeze.setAfterAvailablePoints(after);
            freeze.setBeforeLockedPoints(beforeLocked);
            freeze.setAfterLockedPoints(afterLocked);
            freeze.setStatus("FROZEN");
            freeze.setIdempotencyKey(request.bizNo() + ":POINT_FREEZE");
            freeze.setFrozenAt(now);
            if (freeze.getCreatedAt() == null) {
                freeze.setCreatedAt(now);
            }
            freeze.setUpdatedAt(now);
            pointFreezeRepository.save(freeze);
            return;
        }

        PointFreeze freeze = pointFreezeRepository.findByBizNo(request.bizNo()).orElse(null);
        if (freeze == null) {
            return;
        }
        freeze.setBeforeAvailablePoints(before);
        freeze.setAfterAvailablePoints(after);
        freeze.setBeforeLockedPoints(beforeLocked);
        freeze.setAfterLockedPoints(afterLocked);
        freeze.setUpdatedAt(now);
        if ("POINT_DEDUCT".equals(changeType)) {
            freeze.setStatus("DEDUCTED");
            freeze.setDeductedAt(now);
        } else if ("POINT_RELEASE".equals(changeType)) {
            freeze.setStatus("RELEASED");
            freeze.setReleasedAt(now);
        } else if ("POINT_REFUND".equals(changeType)) {
            freeze.setStatus("REFUNDED");
            freeze.setRefundedAt(now);
        }
        pointFreezeRepository.save(freeze);
    }

    private UserPointFlow toFlow(
        Long userId,
        MemberPointCommandRequest request,
        String flowNo,
        String changeType,
        int points,
        int before,
        int after,
        LocalDateTime now
    ) {
        UserPointFlow flow = new UserPointFlow();
        flow.setUserId(userId);
        flow.setSource(SOURCE_ORDER);
        flow.setFlowNo(flowNo);
        flow.setChangeType(changeType);
        flow.setChangePoints(signedChange(changeType, points));
        flow.setBalanceAfter(after);
        flow.setBizType("ORDER");
        flow.setBizId(request.orderId());
        flow.setOrderId(request.orderId());
        flow.setBizNo(request.bizNo());
        flow.setIdempotencyKey(flowNo);
        flow.setChangeAmount(signedChange(changeType, points));
        flow.setBeforeBalance(before);
        flow.setAfterBalance(after);
        flow.setStatus("SUCCESS");
        flow.setRemark(changeType);
        flow.setCreatedAt(now);
        return flow;
    }

    private int signedChange(String changeType, int points) {
        return switch (changeType) {
            case "POINT_FREEZE", "POINT_DEDUCT" -> -points;
            case "POINT_RELEASE", "POINT_REFUND" -> points;
            default -> 0;
        };
    }

    private java.util.Map<String, Object> pointSnapshot(Long userId, int availablePoints, int lockedPoints) {
        java.util.Map<String, Object> snapshot = new java.util.LinkedHashMap<>();
        snapshot.put("userId", userId);
        snapshot.put("availablePoints", availablePoints);
        snapshot.put("lockedPoints", lockedPoints);
        return snapshot;
    }

    private MemberAddressResponse toAddress(UserAddress address) {
        return new MemberAddressResponse(
            address.getId(),
            address.getUserId(),
            address.getReceiverName(),
            address.getReceiverMobile(),
            address.getProvince(),
            address.getCity(),
            address.getDistrict(),
            address.getDetailAddress(),
            address.getDefaultFlag()
        );
    }
}
