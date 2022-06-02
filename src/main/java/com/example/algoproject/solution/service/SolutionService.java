package com.example.algoproject.solution.service;

import com.example.algoproject.errors.exception.NotExistProblemException;
import com.example.algoproject.errors.exception.NotExistSolutionException;
import com.example.algoproject.errors.exception.NotExistStudyException;
import com.example.algoproject.errors.exception.NotExistUserException;
import com.example.algoproject.errors.response.CommonResponse;
import com.example.algoproject.errors.response.ResponseService;
import com.example.algoproject.problem.domain.Problem;
import com.example.algoproject.problem.repository.ProblemRepository;
import com.example.algoproject.s3.S3Uploader;
import com.example.algoproject.solution.domain.Solution;
import com.example.algoproject.solution.dto.request.AddSolution;
import com.example.algoproject.solution.dto.request.CommitFileRequest;
import com.example.algoproject.solution.dto.request.UpdateSolution;
import com.example.algoproject.solution.dto.response.SolutionInfo;
import com.example.algoproject.solution.repository.SolutionRepository;
import com.example.algoproject.study.domain.Study;
import com.example.algoproject.study.repository.StudyRepository;
import com.example.algoproject.user.domain.User;
import com.example.algoproject.user.dto.CustomUserDetailsVO;
import com.example.algoproject.user.repository.UserRepository;
import com.example.algoproject.util.PathUtil;
import com.example.algoproject.util.ReadMeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.sql.Timestamp;
import java.util.Base64;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
@Service
public class SolutionService {

    private final UserRepository userRepository;
    private final SolutionRepository solutionRepository;
    private final ProblemRepository problemRepository;
    private final StudyRepository studyRepository;
    private final ResponseService responseService;
    private final S3Uploader s3Uploader;
    private final PathUtil pathUtil;
    private final ReadMeUtil readMeUtil;

    public CommonResponse upload(CustomUserDetailsVO cudVO, AddSolution addSolution, MultipartFile code) throws IOException {

        User user = userRepository.findByUserId(cudVO.getUsername()).orElseThrow(NotExistUserException::new);
        Problem problem = problemRepository.findById(addSolution.getProblemId()).orElseThrow(NotExistProblemException::new);
        Study study = studyRepository.findByStudyId(problem.getStudy().getStudyId()).orElseThrow(NotExistStudyException::new);

        String gitHubPath = pathUtil.makeGitHubPath(problem, user.getName());
        String s3Path = pathUtil.makeS3Path(study.getRepositoryName(), problem, user.getName());

        log.info("github repository path : " + gitHubPath);
        log.info("s3 repository path : " + s3Path);

        long date = System.currentTimeMillis();

        /* readme file 생성 메소드 */
        MultipartFile readMe = readMeUtil.makeReadMe(addSolution.getHeader(), addSolution.getContent());

        /* github에 file commit */
        checkFileResponse(code, user, gitHubPath, study.getRepositoryName(), "");
        checkFileResponse(readMe, user, gitHubPath, study.getRepositoryName(), "");

        /* s3에 file upload */
        String codeUrl = s3Uploader.upload(code, s3Path);
        String readMeUrl = s3Uploader.upload(readMe, s3Path);

        /* local 리드미 삭제 */
        readMeUtil.removeReadMe(readMe);

        /* DB에 저장 */
        solutionRepository.save(new Solution(user, problem, codeUrl, readMeUrl, new Timestamp(date), addSolution.getTime(), addSolution.getMemory()));

        return responseService.getSuccessResponse();
    }

    public CommonResponse getFileUrl(CustomUserDetailsVO cudVO, Long problemId) {

        User user = userRepository.findByUserId(cudVO.getUsername()).orElseThrow(NotExistUserException::new);

        Problem problem = problemRepository.findById(problemId).orElseThrow(NotExistProblemException::new);
        Solution solution = solutionRepository.findByUserIdAndProblemId(user, problem).orElseThrow(NotExistSolutionException::new);

        return responseService.getSingleResponse(new S3UrlResponse(solution.getCodeUrl(), solution.getReadMeUrl()));
    }

    /*
    private method
    */

    private void checkFileResponse(MultipartFile multipartFile, User user, String path, String repoName, String commitMessage) throws IOException {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Accept", "application/vnd.github.v3+json");
        headers.add("User-Agent", "api-test");
        headers.add("Authorization", "token " + user.getAccessToken());

        RestTemplate restTemplate = new RestTemplate();

        HttpEntity entity = new HttpEntity<>(headers); // http entity에 header 담아줌
        try { // 깃허브에 파일 존재.
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    "https://api.github.com/repos/" + user.getName() + "/" + repoName + "/contents/" + path + multipartFile.getOriginalFilename(),
                    HttpMethod.GET,
                    entity,
                    new ParameterizedTypeReference<>() {
                    });

            log.info("get sha : " + response.getBody().get("sha").toString());

            commitFileResponse(response.getBody().get("sha").toString(), multipartFile, user, path, repoName, commitMessage);
        } catch (HttpClientErrorException e) { // 깃허브에 파일 존재 x. 새로 생성되는 파일인 경우 404 에러 뜸.
            commitFileResponse(null, multipartFile, user, path, repoName, commitMessage);
        }
    }

    /* github file commit 메소드 */
    private Map<String, Object> commitFileResponse(String sha, MultipartFile multipartFile, User user, String path, String repoName, String commitMessage) throws IOException {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Accept", "application/vnd.github.v3+json");
        headers.add("User-Agent", "api-test");
        headers.add("Authorization", "token " + user.getAccessToken());

        CommitFileRequest request = new CommitFileRequest();
        request.setMessage(commitMessage);
        request.setContent(Base64.getEncoder().encodeToString(multipartFile.getBytes())); // 내용 base64로 인코딩 해줘야됨 (필수)

        if (sha != null) { // 기존 파일 수정하는 거면 sha 바디에 추가해야됨
            request.setSha(sha);
        }

        HttpEntity<CommitFileRequest> entity = new HttpEntity<>(request, headers);

        RestTemplate restTemplate = new RestTemplate();

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                "https://api.github.com/repos/" + user.getName() + "/" + repoName + "/contents/" + path + multipartFile.getOriginalFilename(),
                HttpMethod.PUT,
                entity,
                new ParameterizedTypeReference<>() {
                });
        log.info("github path : " + path + multipartFile.getOriginalFilename());

        return response.getBody();
    }

}