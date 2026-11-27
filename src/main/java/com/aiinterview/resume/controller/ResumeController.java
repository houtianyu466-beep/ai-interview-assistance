package com.aiinterview.resume.controller;

import com.aiinterview.common.api.Result;
import com.aiinterview.common.log.OperationLog;
import com.aiinterview.resume.dto.MatchVO;
import com.aiinterview.resume.dto.ResumeDetailVO;
import com.aiinterview.resume.dto.ResumeTextRequest;
import com.aiinterview.resume.dto.ResumeVO;
import com.aiinterview.resume.service.ResumeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 简历接口：上传文件、粘贴文本、查询列表/详情、删除与岗位匹配。
 *
 * <p>所有接口均需登录，资源归属由 Service 通过 {@link com.aiinterview.security.SecurityUtils} 校验。</p>
 */
@Tag(name = "简历管理")
@RestController
@RequestMapping("/api/v1/resumes")
@Validated
public class ResumeController {

    private final ResumeService resumeService;

    public ResumeController(ResumeService resumeService) {
        this.resumeService = resumeService;
    }

    /**
     * 上传简历文件（txt / md / docx），上传成功后异步解析。
     *
     * @param file 上传文件
     * @return 简历摘要
     */
    @Operation(summary = "上传简历文件")
    @OperationLog("上传简历")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<ResumeVO> upload(@RequestPart("file") MultipartFile file) {
        return Result.ok(resumeService.upload(file));
    }

    /**
     * 手工录入简历文本，录入成功后异步解析。
     *
     * @param request 简历文本
     * @return 简历摘要
     */
    @Operation(summary = "录入文本简历")
    @OperationLog("录入文本简历")
    @PostMapping("/text")
    public Result<ResumeVO> submitText(@Valid @RequestBody ResumeTextRequest request) {
        return Result.ok(resumeService.submitText(request));
    }

    /**
     * 查询当前用户的简历列表（创建时间倒序）。
     *
     * @return 简历摘要列表
     */
    @Operation(summary = "我的简历列表")
    @GetMapping("/my")
    public Result<List<ResumeVO>> myResumes() {
        return Result.ok(resumeService.myResumes());
    }

    /**
     * 查看简历详情。
     *
     * @param id 简历 ID
     * @return 简历详情
     */
    @Operation(summary = "简历详情")
    @GetMapping("/{id}")
    public Result<ResumeDetailVO> detail(@PathVariable Long id) {
        return Result.ok(resumeService.detail(id));
    }

    /**
     * 删除简历（被面试引用时返回 409）。
     *
     * @param id 简历 ID
     * @return 空响应
     */
    @Operation(summary = "删除简历")
    @OperationLog("删除简历")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        resumeService.delete(id);
        return Result.ok();
    }

    /**
     * 简历与岗位匹配分析。
     *
     * @param id         简历 ID
     * @param positionId 岗位 ID
     * @return 匹配结果
     */
    @Operation(summary = "简历岗位匹配")
    @GetMapping("/{id}/match")
    public Result<MatchVO> match(@PathVariable Long id, @RequestParam("positionId") Long positionId) {
        return Result.ok(resumeService.match(id, positionId));
    }
}
