package com.example.algoproject.study.controller;

import com.example.algoproject.errors.response.*;
import com.example.algoproject.study.domain.Study;
import com.example.algoproject.study.dto.request.AddMember;
import com.example.algoproject.study.dto.request.CreateStudy;
import com.example.algoproject.study.dto.response.MemberInfo;
import com.example.algoproject.study.dto.request.MemberList;
import com.example.algoproject.study.dto.response.StudyInfo;
import com.example.algoproject.study.service.StudyService;
import com.example.algoproject.user.dto.CustomUserDetailsVO;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@RequiredArgsConstructor
@RequestMapping("/api/study")
@RestController
public class StudyController {

    private final StudyService studyService;
    private final ResponseService responseService;

    @ApiOperation(value="스터디 생성", notes="studyId 반환")
    @PostMapping()
    public SingleResponse<String> studyAdd(@AuthenticationPrincipal CustomUserDetailsVO cudVO, @RequestBody @Valid CreateStudy request) {
        return responseService.getSingleResponse(studyService.create(cudVO, request));
    }

    @ApiOperation(value="멤버 추가", notes="code와 message 반환")
    @PostMapping("/member")
    public CommonResponse memberAdd(@AuthenticationPrincipal CustomUserDetailsVO cudVO, @RequestBody @Valid AddMember request) {
        studyService.addMember(cudVO, request);
        return responseService.getSuccessResponse();
    }

    @ApiOperation(value="멤버 조회", notes="스터디에 참여중인 멤버 리스트 반환")
    @GetMapping("/member/list")
    public ListResponse<MemberInfo> memberList(@AuthenticationPrincipal @RequestBody @Valid MemberList request) {
        return responseService.getListResponse(studyService.getMembers(request));
    }

    @ApiOperation(value="스터디 조회", notes="스터디의 정보룰 반환(스터디 이름, 레포지토리 주소, 멤버들의 목록)")
    @GetMapping("/{studyId}")
    public SingleResponse<StudyInfo> studyDetails(@AuthenticationPrincipal @PathVariable("studyId") String studyId) {
        return responseService.getSingleResponse(studyService.detail(studyId));
    }

    @ApiOperation(value = "스터디 목록", notes="사용자가 참여중인 스터디 목록을 반환")
    @GetMapping("/list")
    public ListResponse<Study> studyList(@AuthenticationPrincipal CustomUserDetailsVO cudVO) {
        return responseService.getListResponse(studyService.list(cudVO));
    }
}
