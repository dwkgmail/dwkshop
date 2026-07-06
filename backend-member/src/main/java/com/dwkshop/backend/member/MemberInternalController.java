package com.dwkshop.backend.member;

import com.dwkshop.backend.member.dto.MemberAddressResponse;
import com.dwkshop.backend.member.dto.MemberPointAccountResponse;
import com.dwkshop.backend.member.dto.MemberPointAccountSnapshotResponse;
import com.dwkshop.backend.member.dto.MemberPointCommandRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/members")
public class MemberInternalController {

    private final MemberService memberService;

    public MemberInternalController(MemberService memberService) {
        this.memberService = memberService;
    }

    @GetMapping("/{userId}/addresses/resolved")
    public MemberAddressResponse resolveAddress(
        @PathVariable Long userId,
        @RequestParam(required = false) Long addressId
    ) {
        return memberService.resolveAddress(userId, addressId);
    }

    @GetMapping("/{userId}/point-account")
    public MemberPointAccountResponse getPointAccount(@PathVariable Long userId) {
        return memberService.getPointAccount(userId);
    }

    @GetMapping("/point-accounts")
    public List<MemberPointAccountSnapshotResponse> listPointAccounts(@RequestParam List<Long> userIds) {
        return memberService.listPointAccounts(userIds);
    }

    @PostMapping("/{userId}/points/freeze")
    public MemberPointAccountResponse freezePoints(
        @PathVariable Long userId,
        @Valid @org.springframework.web.bind.annotation.RequestBody MemberPointCommandRequest request
    ) {
        return memberService.freezePoints(userId, request);
    }

    @PostMapping("/{userId}/points/deduct")
    public MemberPointAccountResponse deductFrozenPoints(
        @PathVariable Long userId,
        @Valid @org.springframework.web.bind.annotation.RequestBody MemberPointCommandRequest request
    ) {
        return memberService.deductFrozenPoints(userId, request);
    }

    @PostMapping("/{userId}/points/release")
    public MemberPointAccountResponse releaseFrozenPoints(
        @PathVariable Long userId,
        @Valid @org.springframework.web.bind.annotation.RequestBody MemberPointCommandRequest request
    ) {
        return memberService.releaseFrozenPoints(userId, request);
    }

    @PostMapping("/{userId}/points/refund")
    public MemberPointAccountResponse refundPoints(
        @PathVariable Long userId,
        @Valid @org.springframework.web.bind.annotation.RequestBody MemberPointCommandRequest request
    ) {
        return memberService.refundPoints(userId, request);
    }
}
