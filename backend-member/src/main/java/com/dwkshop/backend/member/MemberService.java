package com.dwkshop.backend.member;

import com.dwkshop.backend.domain.entity.UserAddress;
import com.dwkshop.backend.domain.entity.UserPointAccount;
import com.dwkshop.backend.domain.repository.UserAddressRepository;
import com.dwkshop.backend.domain.repository.UserPointAccountRepository;
import com.dwkshop.backend.member.dto.MemberAddressResponse;
import com.dwkshop.backend.member.dto.MemberPointAccountResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MemberService {

    private final UserAddressRepository userAddressRepository;
    private final UserPointAccountRepository userPointAccountRepository;

    public MemberService(
        UserAddressRepository userAddressRepository,
        UserPointAccountRepository userPointAccountRepository
    ) {
        this.userAddressRepository = userAddressRepository;
        this.userPointAccountRepository = userPointAccountRepository;
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
