package com.example.algoproject.study.domain;

import com.example.algoproject.problem.domain.Problem;
import lombok.Getter;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.util.ArrayList;
import java.util.List;

@Getter
@NoArgsConstructor
@Entity
public class Study {

    @Id
    @Column(name = "study_id")
    private String studyId;

    private String name;

    private String leaderId;

    private String repositoryName;

    private String repositoryUrl;

    @OneToMany(
            mappedBy = "study",
            cascade = {CascadeType.ALL},
            orphanRemoval = true
    )
    private List<Problem> problems = new ArrayList<>();

    public Study(String studyId, String name, String leaderId, String repositoryName, String repositoryUrl) {
        this.studyId = studyId;
        this.name = name;
        this.leaderId = leaderId;
        this.repositoryName = repositoryName;
        this.repositoryUrl = repositoryUrl;
    }

    public void addProblem(Problem problem) {
        this.problems.add(problem);

        // 스터디에 문제가 저장되어있지 않은 경우
        if(problem.getStudy() != this)
            // 문제 저장
            problem.setStudy(this);
    }
}
