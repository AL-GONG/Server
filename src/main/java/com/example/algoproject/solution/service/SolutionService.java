package com.example.algoproject.solution.service;

import com.example.algoproject.belongsto.domain.BelongsTo;
import com.example.algoproject.belongsto.service.BelongsToService;
import com.example.algoproject.errors.exception.AlreadyExistSolutionException;
import com.example.algoproject.errors.exception.NotExistSolutionException;
import com.example.algoproject.errors.exception.NotMySolutionException;
import com.example.algoproject.errors.response.CommonResponse;
import com.example.algoproject.errors.response.ResponseService;
import com.example.algoproject.problem.domain.Problem;
import com.example.algoproject.problem.service.ProblemService;
import com.example.algoproject.solution.domain.Language;
import com.example.algoproject.solution.domain.Solution;
import com.example.algoproject.solution.dto.request.AddSolution;
import com.example.algoproject.solution.dto.request.CommitFileRequest;
import com.example.algoproject.solution.dto.request.UpdateSolution;
import com.example.algoproject.solution.dto.response.SolutionInfo;
import com.example.algoproject.solution.dto.response.SolutionListInfo;
import com.example.algoproject.solution.repository.SolutionRepository;
import com.example.algoproject.study.domain.Study;
import com.example.algoproject.study.service.StudyService;
import com.example.algoproject.user.domain.User;
import com.example.algoproject.user.dto.CustomUserDetailsVO;
import com.example.algoproject.user.service.UserService;
import com.example.algoproject.util.PathUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.sql.Timestamp;
import java.util.*;

@Slf4j
@RequiredArgsConstructor
@Service
public class SolutionService {

    private final SolutionRepository solutionRepository;
    private final UserService userService;
    private final ProblemService problemService;
    private final StudyService studyService;
    private final BelongsToService belongsToService;

    private final ResponseService responseService;
    private final PathUtil pathUtil;

    public CommonResponse create(CustomUserDetailsVO cudVO, AddSolution addSolution) throws IOException {

        User user = userService.findByUserId(cudVO.getUsername());
        Problem problem = problemService.findById(addSolution.getProblemId());
        Study study = studyService.findByStudyId(problem.getSession().getStudy().getStudyId());
        Optional<Solution> alreadyExist = solutionRepository.findByProblemAndUser(problem, user);

        if (alreadyExist.isPresent()) // ?????? ??????????????? ?????? ????????? ????????? ????????? ??????
            throw new AlreadyExistSolutionException();

        String gitHubPath = pathUtil.makeGitHubPath(problem, user.getName());
        log.info("github repository path : " + gitHubPath);

        long date = System.currentTimeMillis(); // ????????? ????????? ?????? ??????
        String commitMessage = pathUtil.makeCommitMessage(problem, user.getName()); // ??????????????? ??????
        String fileName = problem.getNumber() + "." + addSolution.getLanguage(); // ***?????? ??????????????? ?????? ?????? ???????????? ?????? ?????? ???????????????....

        /* github??? file commit */
        checkFileResponse(user, addSolution.getCode(), fileName, commitMessage, gitHubPath, study.getRepositoryName()); // code
        checkFileResponse(user, addSolution.getReadMe(), "README.md", commitMessage, gitHubPath, study.getRepositoryName()); // readMe

        /* DB??? ?????? */
        solutionRepository.save(new Solution(user, problem, addSolution.getCode(), addSolution.getReadMe(), new Timestamp(date), addSolution.getLanguage()));

        return responseService.getSuccessResponse();
    }

    public CommonResponse detail(CustomUserDetailsVO cudVO, Long solutionId) {

        Solution solution = solutionRepository.findById(solutionId).orElseThrow(NotExistSolutionException::new);

        return responseService.getSingleResponse(new SolutionInfo(solution.getCode(), solution.getReadMe(), solution.getDate(), solution.getReviews()));

    }

    public CommonResponse list(CustomUserDetailsVO cudVO, Long problemId) {

        Problem problem = problemService.findById(problemId);
        Study study = studyService.findByStudyId(problem.getSession().getStudy().getStudyId());
        List<BelongsTo> belongs =  belongsToService.findByStudy(study);
        List<Solution> solutions = solutionRepository.findByProblem(problem); // null??? ?????? ??????
        List<SolutionListInfo> list = new ArrayList<>();

        for (User member: getMemberList(belongs)) { // ?????? ???????????? ????????? ?????????, probelmId??? ??? ????????? ????????? ???????????? true ??????. ??? ???????????? false ??????.
            SolutionListInfo info = new SolutionListInfo(false, null, member.getName(), member.getImageUrl(), "none"); // language enum ???????????? null ??????.. ?????? nont?????? ??????

            for (Solution solution: solutions) {
                if (solution.getUser().equals(member)) {
                    info.setSolutionId(solution.getId());
                    info.setLanguage(solution.getLanguage());
                    info.setSolve(true);
                }
            }
            list.add(info);
        }
        return responseService.getListResponse(list);
    }

    public CommonResponse update(CustomUserDetailsVO cudVO, Long solutionId, UpdateSolution updateSolution) throws IOException {

        User user = userService.findByUserId(cudVO.getUsername());
        Solution solution = solutionRepository.findById(solutionId).orElseThrow(NotExistSolutionException::new);
        Problem problem = problemService.findById(updateSolution.getProblemId());
        Study study = studyService.findByStudyId(problem.getSession().getStudy().getStudyId());

        String gitHubPath = pathUtil.makeGitHubPath(solution.getProblem(), user.getName());

        String commitMessage = pathUtil.makeCommitMessage(problem, user.getName()); // ??????????????? ??????
        String fileName = problem.getNumber() + "." + updateSolution.getLanguage(); // ***?????? ??????????????? ?????? ?????? ???????????? ?????? ?????? ???????????????....

        /* github??? file commit */
        checkFileResponse(user, updateSolution.getCode(), fileName, commitMessage, gitHubPath, study.getRepositoryName());
        checkFileResponse(user, updateSolution.getReadMe(), "README.md", commitMessage, gitHubPath, study.getRepositoryName());

        solution.setDate(new Timestamp(System.currentTimeMillis()));
        solution.setCode(updateSolution.getCode());
        solution.setReadMe(updateSolution.getReadMe());
        solution.setLanguage(Language.valueOf(updateSolution.getLanguage()));
        solutionRepository.save(solution);

        return responseService.getSuccessResponse();
    }

    public CommonResponse delete(CustomUserDetailsVO cudVO, Long solutionId) {

        Solution solution = solutionRepository.findById(solutionId).orElseThrow(NotExistSolutionException::new);

        if (!cudVO.getUsername().equals(solution.getUser().getId())) // ??? ????????? ????????? ?????? ??????
            throw new NotMySolutionException();

        solutionRepository.delete(solution); // ????????? ??????

        return responseService.getSuccessResponse();
    }

    public Solution findById(Long solutionId) {
        return solutionRepository.findById(solutionId).orElseThrow(NotExistSolutionException::new);
    }

    public void save(Solution solution) {
        solutionRepository.save(solution);
    }

    /*
    private method
    */

    private void checkFileResponse(User user, String content, String fileName, String commitMessage, String path, String repoName) throws IOException {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Accept", "application/vnd.github.v3+json");
        headers.add("User-Agent", "api-test");
        headers.add("Authorization", "token " + user.getAccessToken());

        RestTemplate restTemplate = new RestTemplate();

        HttpEntity entity = new HttpEntity<>(headers); // http entity??? header ?????????
        try { // ???????????? ?????? ??????.
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    "https://api.github.com/repos/" + user.getName() + "/" + repoName + "/contents/" + path + fileName,
                    HttpMethod.GET,
                    entity,
                    new ParameterizedTypeReference<>() {
                    });

            commitFileResponse(response.getBody().get("sha").toString(), user, content, fileName, path, repoName, commitMessage);
        } catch (HttpClientErrorException e) { // ???????????? ?????? ?????? x. ?????? ???????????? ????????? ?????? 404 ?????? ???.
            commitFileResponse(null, user, content, fileName, path, repoName, commitMessage);
        }
    }

    /* github file commit ????????? */
    private Map<String, Object> commitFileResponse(String sha, User user, String content, String fileName, String path, String repoName, String commitMessage) throws IOException {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Accept", "application/vnd.github.v3+json");
        headers.add("User-Agent", "api-test");
        headers.add("Authorization", "token " + user.getAccessToken());

        CommitFileRequest request = new CommitFileRequest();
        request.setMessage(commitMessage);
        request.setContent(Base64.getEncoder().encodeToString(content.getBytes())); // ?????? base64??? ????????? ???????????? (??????)

        if (sha != null) { // ?????? ?????? ???????????? ?????? sha ????????? ???????????????
            request.setSha(sha);
        }

        HttpEntity<CommitFileRequest> entity = new HttpEntity<>(request, headers);

        RestTemplate restTemplate = new RestTemplate();

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                "https://api.github.com/repos/" + user.getName() + "/" + repoName + "/contents/" + path + fileName,
                HttpMethod.PUT,
                entity,
                new ParameterizedTypeReference<>() {
                });
        log.info("github path : " + path + fileName);

        return response.getBody();
    }

    private List<User> getMemberList(List<BelongsTo> belongs) {

        List<User> members = new ArrayList<>();

        for (BelongsTo belongsTo : belongs)
            members.add(belongsTo.getMember());

        return members;
    }

}