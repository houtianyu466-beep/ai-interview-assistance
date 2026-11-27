package com.aiinterview.knowledge.controller;

import com.aiinterview.common.api.Result;
import com.aiinterview.common.log.OperationLog;
import com.aiinterview.knowledge.dto.KnowledgeDocumentVO;
import com.aiinterview.knowledge.dto.KnowledgeHitVO;
import com.aiinterview.knowledge.dto.KnowledgeIngestRequest;
import com.aiinterview.knowledge.dto.KnowledgeSearchRequest;
import com.aiinterview.knowledge.service.KnowledgeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 知识库接口：文档管理、检索与统计。
 *
 * <p>全部接口登录即可用（由 Spring Security 的 {@code anyRequest().authenticated()} 兜底）；
 * 写操作额外记录操作日志。当前没有角色划分：任何登录用户都可以维护知识库，
 * 后续如需收紧可在 Security 配置里对 {@code POST/DELETE} 加 ADMIN 限制。</p>
 */
@Tag(name = "知识库")
@RestController
@RequestMapping("/api/v1/knowledge")
@Validated
public class KnowledgeController {

    private final KnowledgeService knowledgeService;

    /**
     * 构造器注入。
     *
     * @param knowledgeService 知识库服务
     */
    public KnowledgeController(KnowledgeService knowledgeService) {
        this.knowledgeService = knowledgeService;
    }

    /**
     * 查询知识文档列表（含分块数量）。
     *
     * @return 文档列表
     */
    @Operation(summary = "查询知识文档列表")
    @GetMapping("/documents")
    public Result<List<KnowledgeDocumentVO>> documents() {
        return Result.ok(knowledgeService.list());
    }

    /**
     * 新增知识文档：服务端负责切块、可选向量化与索引重建。
     *
     * @param request 录入请求
     * @return 新建文档
     */
    @Operation(summary = "新增知识文档")
    @OperationLog("新增知识文档")
    @PostMapping("/documents")
    public Result<KnowledgeDocumentVO> ingest(@Valid @RequestBody KnowledgeIngestRequest request) {
        return Result.ok(knowledgeService.ingest(request));
    }

    /**
     * 删除知识文档及其全部分块。
     *
     * @param id 文档 id
     * @return 空结果
     */
    @Operation(summary = "删除知识文档")
    @OperationLog("删除知识文档")
    @DeleteMapping("/documents/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        knowledgeService.delete(id);
        return Result.ok();
    }

    /**
     * 检索知识库。
     *
     * @param request 检索请求
     * @return 命中分块列表
     */
    @Operation(summary = "检索知识库")
    @PostMapping("/search")
    public Result<List<KnowledgeHitVO>> search(@Valid @RequestBody KnowledgeSearchRequest request) {
        int topK = request.topK() == null ? 0 : request.topK();
        return Result.ok(knowledgeService.search(request.query(), topK));
    }

    /**
     * 知识库统计：文档数、分块数、向量化状态与模型。
     *
     * @return 统计结果
     */
    @Operation(summary = "知识库统计")
    @GetMapping("/stats")
    public Result<Map<String, Object>> stats() {
        return Result.ok(knowledgeService.stats());
    }
}
