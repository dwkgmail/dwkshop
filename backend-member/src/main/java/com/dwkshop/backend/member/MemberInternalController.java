package com.dwkshop.backend.member;

import com.dwkshop.backend.member.dto.MemberAddressResponse;
import com.dwkshop.backend.member.dto.MemberPointAccountResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
}
