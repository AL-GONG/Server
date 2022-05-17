package com.example.algoproject.problem.controller;

import com.example.algoproject.errors.response.*;
import com.example.algoproject.problem.dto.request.AddProblem;
import com.example.algoproject.problem.dto.request.ProblemWeekList;
import com.example.algoproject.problem.dto.response.ProblemInfo;
import com.example.algoproject.problem.service.ProblemService;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

@RequiredArgsConstructor
@RequestMapping("/api/problem")
@RestController
public class ProblemController {

    private final ProblemService problemService;
    private final ResponseService responseService;

    @ApiOperation(value="문제 추가", notes="code 와 message 반환")
    @PostMapping()
    public CommonResponse problemAdd(@AuthenticationPrincipal @RequestBody @Valid AddProblem request) {
        problemService.create(request);
        return responseService.getSuccessResponse();
    }

    @ApiOperation(value = "문제 보기", notes = "문제 json 형태로 반환")
    @GetMapping({"/{problemId}"})
    public SingleResponse<ProblemInfo> problemDetail(@AuthenticationPrincipal @PathVariable("problemId") Long problemId) {
        return responseService.getSingleResponse(problemService.detail(problemId));
    }

    @ApiOperation(value = "스터디에 등록 된 모든 문제 보기", notes = "전체 문제 리스트 반환")
    @GetMapping("/list")
    public ListResponse<ProblemInfo> problemList(@AuthenticationPrincipal @RequestParam String studyId) {
        return responseService.getListResponse(problemService.list(studyId));
    }

    @ApiOperation(value = "주차별 문제 보기", notes = "주차별 문제 list 반환")
    @GetMapping("/list/week")
    public ListResponse<ProblemInfo> problemWeekList(@AuthenticationPrincipal @RequestBody @Valid ProblemWeekList request) {
        return responseService.getListResponse(problemService.weekList(request));
    }

    @ApiOperation(value = "문제 삭제", notes = "code 와 message 를 반환")
    @DeleteMapping("/{problemId}")
    public CommonResponse problemRemove(@AuthenticationPrincipal @PathVariable("problemId") Long problemId) {
        problemService.delete(problemId);
        return responseService.getSuccessResponse();
    }

    @ApiOperation(value = "플랫폼 리스트", notes = "플랫폼 문자열 배열 반환")
    @GetMapping("/platform")
    public ListResponse<String> platformList() {
        return responseService.getListResponse(problemService.getPlatforms());
    }
}
