package com.aiinterview.knowledge.service;

import com.aiinterview.common.exception.BizException;
import com.aiinterview.common.util.JsonUtils;
import com.aiinterview.common.util.TextUtils;
import com.aiinterview.knowledge.config.RagProperties;
import com.aiinterview.knowledge.dto.KnowledgeDocumentVO;
import com.aiinterview.knowledge.dto.KnowledgeHitVO;
import com.aiinterview.knowledge.dto.KnowledgeIngestRequest;
import com.aiinterview.knowledge.entity.KnowledgeChunk;
import com.aiinterview.knowledge.entity.KnowledgeDocument;
import com.aiinterview.knowledge.mapper.KnowledgeChunkMapper;
import com.aiinterview.knowledge.mapper.KnowledgeDocumentMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 知识库服务：文档灌入、分块索引、真实检索与统计。
 *
 * <p>检索算法（两档，自动升级）：</p>
 * <ol>
 *     <li><b>纯 BM25</b>（默认，{@code app.rag.embedding.provider=none}）：
 *         {@link Bm25Index} 用中文 2-gram + 英文单词分词，按 k1=1.5、b=0.75 计算相关性分数，
 *         返回 topK 个分块。分数完全由语料与查询计算得出，不依赖任何外部服务。</li>
 *     <li><b>BM25 + 向量混合</b>（配置 embedding 后）：先取 BM25 top {@code 3 * topK} 作为候选，
 *         再对查询与候选分块计算余弦相似度，最终分 = 0.65 × 归一化 BM25 + 0.35 × 余弦。
 *         某个分块没有向量（灌库时向量化失败）时不引入负向惩罚，直接退化为归一化 BM25 分。</li>
 * </ol>
 *
 * <p>一致性：{@link #rebuildIndex()} 从数据库全量加载文档与分块后整体替换内存快照，
 * 灌库/删除后都会立即重建，因此检索结果与库内数据保持一致；启动时若数据库尚未就绪，
 * 首次检索会自动重试重建。</p>
 */
@Service
public class KnowledgeService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeService.class);

    /** 手工录入来源 */
    private static final String SOURCE_MANUAL = "MANUAL";
    /** 可检索状态 */
    private static final String STATUS_READY = "READY";
    /** 单次检索条数上限，防御异常入参 */
    private static final int MAX_TOP_K = 50;
    /** 混合检索中 BM25 的权重 */
    private static final double BM25_WEIGHT = 0.65;
    /** 混合检索中余弦相似度的权重 */
    private static final double VECTOR_WEIGHT = 0.35;
    /** 向量落库时保留的小数位（控制 TEXT 体积） */
    private static final double VECTOR_SCALE = 1_000_000.0;
    /** 分类字段长度上限，与 knowledge_document.category 对齐 */
    private static final int MAX_CATEGORY_LENGTH = 32;

    private final KnowledgeDocumentMapper documentMapper;
    private final KnowledgeChunkMapper chunkMapper;
    private final ChunkSplitter chunkSplitter;
    private final Bm25Index bm25Index;
    private final EmbeddingClient embeddingClient;
    private final RagProperties properties;

    /** 索引是否已成功构建，启动期数据库未就绪时用于首次检索时重试 */
    private volatile boolean indexReady;

    /** 重建串行化锁：保证「读库 + 建索引 + 替换快照」整体原子，避免并发重建互相覆盖 */
    private final Object rebuildMonitor = new Object();

    /**
     * 构造器注入。
     *
     * @param documentMapper  知识文档 Mapper
     * @param chunkMapper     知识分块 Mapper
     * @param chunkSplitter   文本分块器
     * @param bm25Index       BM25 索引
     * @param embeddingClient 向量化客户端（未配置时为空实现）
     * @param properties      RAG 配置
     */
    public KnowledgeService(KnowledgeDocumentMapper documentMapper,
                            KnowledgeChunkMapper chunkMapper,
                            ChunkSplitter chunkSplitter,
                            Bm25Index bm25Index,
                            EmbeddingClient embeddingClient,
                            RagProperties properties) {
        this.documentMapper = documentMapper;
        this.chunkMapper = chunkMapper;
        this.chunkSplitter = chunkSplitter;
        this.bm25Index = bm25Index;
        this.embeddingClient = embeddingClient;
        this.properties = properties;
    }

    /**
     * 启动时构建一次内存索引。
     *
     * <p>若此时数据库尚未完成建表（例如 SQL 初始化顺序靠后），这里只记录告警并保持
     * {@code indexReady=false}，首次检索会自动重试，不影响应用启动。</p>
     *
     * <p>重建整体串行化：并发重建时后进入的线程会读取到先前所有已提交的数据，
     * 最终快照不会缺文档。</p>
     */
    @PostConstruct
    public void rebuildIndex() {
        synchronized (rebuildMonitor) {
            try {
                List<KnowledgeDocument> documents = documentMapper.selectList(null);
                Map<Long, String> titles = new HashMap<>();
                for (KnowledgeDocument document : documents) {
                    if (document.getId() != null) {
                        titles.put(document.getId(), document.getTitle());
                    }
                }
                List<KnowledgeChunk> chunks = chunkMapper.selectList(null);
                bm25Index.rebuild(chunks, titles);
                indexReady = true;
                log.info("知识库索引重建完成 documents={} chunks={} embedding={}",
                        documents.size(), chunks.size(), embeddingClient.enabled() ? embeddingClient.model() : "none");
            } catch (RuntimeException e) {
                indexReady = false;
                log.warn("知识库索引重建失败，将在首次检索时重试 reason={}", e.getMessage());
            }
        }
    }

    /**
     * 查询全部知识文档，按创建时间倒序，并带上各自的分块数量。
     *
     * @return 文档列表，库为空时返回空列表
     */
    public List<KnowledgeDocumentVO> list() {
        List<KnowledgeDocument> documents = documentMapper.selectList(Wrappers.<KnowledgeDocument>lambdaQuery()
                .orderByDesc(KnowledgeDocument::getCreatedAt)
                .orderByDesc(KnowledgeDocument::getId));
        if (documents.isEmpty()) {
            return List.of();
        }
        Map<Long, Integer> chunkCounts = chunkCountByDocument();
        return documents.stream()
                .map(document -> KnowledgeDocumentVO.from(document,
                        chunkCounts.getOrDefault(document.getId(), 0)))
                .toList();
    }

    /**
     * 灌入一篇知识文档：保存文档 → 切块 → 逐块生成向量（可选）→ 保存分块 → 重建索引。
     *
     * <p>向量化失败只影响当前分块（{@code embedding} 落 {@code null}），不会导致整篇灌库失败；
     * 该分块仍参与 BM25 检索。</p>
     *
     * @param request 灌库请求
     * @return 新建文档的展示对象（含分块数量）
     * @throws BizException 标题/内容为空或分类超长时抛出 400
     */
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeDocumentVO ingest(KnowledgeIngestRequest request) {
        if (request == null || TextUtils.isBlank(request.title()) || TextUtils.isBlank(request.content())) {
            throw BizException.badRequest("文档标题与内容不能为空");
        }
        String category = request.category() == null ? null : request.category().trim();
        if (category != null && category.length() > MAX_CATEGORY_LENGTH) {
            throw BizException.badRequest("分类不能超过 " + MAX_CATEGORY_LENGTH + " 字");
        }

        KnowledgeDocument document = new KnowledgeDocument();
        document.setTitle(request.title().trim());
        document.setCategory(category);
        document.setSource(SOURCE_MANUAL);
        document.setContent(request.content());
        document.setStatus(STATUS_READY);
        documentMapper.insert(document);

        List<String> pieces = chunkSplitter.split(request.content());
        int chunkIndex = 0;
        for (String piece : pieces) {
            KnowledgeChunk chunk = new KnowledgeChunk();
            chunk.setDocumentId(document.getId());
            chunk.setChunkIndex(chunkIndex++);
            chunk.setContent(piece);
            chunk.setTokens(TextUtils.countWords(piece));
            chunk.setEmbedding(embeddingOf(piece));
            chunkMapper.insert(chunk);
        }
        rebuildIndexAfterCommit();
        log.info("知识文档灌入完成 documentId={} title={} chunks={}", document.getId(), document.getTitle(), pieces.size());
        return KnowledgeDocumentVO.from(document, pieces.size());
    }

    /**
     * 删除知识文档及其全部分块，并重建索引。
     *
     * @param id 文档 id
     * @throws BizException id 为空（400）或文档不存在（404）时抛出
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        if (id == null) {
            throw BizException.badRequest("文档 id 不能为空");
        }
        KnowledgeDocument document = documentMapper.selectById(id);
        if (document == null) {
            throw BizException.notFound("知识文档不存在");
        }
        chunkMapper.delete(Wrappers.<KnowledgeChunk>lambdaQuery()
                .eq(KnowledgeChunk::getDocumentId, id));
        documentMapper.deleteById(id);
        rebuildIndexAfterCommit();
        log.info("知识文档已删除 documentId={} title={}", id, document.getTitle());
    }

    /**
     * 检索知识库，返回带标题与来源的命中分块。
     *
     * <p>纯 BM25 模式返回原始 BM25 分；混合模式返回
     * {@code 0.65 × 归一化 BM25 + 0.35 × 余弦}，排序按最终分降序。
     * 查询为空、知识库关闭、索引为空或没有任何词命中时返回空列表，不抛异常。</p>
     *
     * @param query 查询文本
     * @param topK  返回条数，{@code <= 0} 时使用 {@code app.rag.top-k}
     * @return 命中列表，按相关性降序
     */
    public List<KnowledgeHitVO> search(String query, int topK) {
        if (!properties.isEnabled() || TextUtils.isBlank(query)) {
            return List.of();
        }
        if (!indexReady) {
            rebuildIndex();
        }
        int configuredTopK = Math.min(Math.max(properties.getTopK(), 1), MAX_TOP_K);
        int limit = topK > 0 ? Math.min(topK, MAX_TOP_K) : configuredTopK;
        boolean vectorEnabled = embeddingClient.enabled();
        // 混合检索需要更大的候选池，先多召回 3 倍再按融合分排序
        List<Bm25Index.SearchResult> candidates = bm25Index.search(query, vectorEnabled ? limit * 3 : limit);
        if (candidates.isEmpty()) {
            return List.of();
        }

        List<Double> queryVector = List.of();
        if (vectorEnabled) {
            try {
                List<Double> vector = embeddingClient.embed(query);
                if (vector != null && !vector.isEmpty()) {
                    queryVector = vector;
                }
            } catch (RuntimeException e) {
                log.warn("查询向量生成失败，降级为纯 BM25 检索 reason={}", e.getMessage());
            }
        }
        boolean useVector = !queryVector.isEmpty();
        double maxBm25 = candidates.stream().mapToDouble(Bm25Index.SearchResult::score).max().orElse(0.0);

        List<ScoredHit> scored = new ArrayList<>(candidates.size());
        for (Bm25Index.SearchResult candidate : candidates) {
            double score = candidate.score();
            if (useVector) {
                double normalizedBm25 = maxBm25 > 0 ? candidate.score() / maxBm25 : 0.0;
                List<Double> chunkVector = parseVector(candidate.embedding());
                score = chunkVector == null
                        ? normalizedBm25
                        : BM25_WEIGHT * normalizedBm25
                        + VECTOR_WEIGHT * clampCosine(cosine(queryVector, chunkVector));
            }
            scored.add(new ScoredHit(candidate, score));
        }
        scored.sort(Comparator.<ScoredHit>comparingDouble(ScoredHit::score).reversed()
                .thenComparingLong(hit -> hit.result().chunkId()));

        List<Long> documentIds = scored.stream()
                .map(hit -> hit.result().documentId())
                .filter(id -> id > 0)
                .distinct()
                .toList();
        Map<Long, KnowledgeDocument> documents = documentsByIds(documentIds);

        List<KnowledgeHitVO> hits = new ArrayList<>();
        for (ScoredHit hit : scored) {
            if (hits.size() >= limit) {
                break;
            }
            Bm25Index.SearchResult result = hit.result();
            KnowledgeDocument document = documents.get(result.documentId());
            hits.add(new KnowledgeHitVO(
                    String.valueOf(result.chunkId()),
                    String.valueOf(result.documentId()),
                    document == null ? result.title() : document.getTitle(),
                    document == null ? null : document.getCategory(),
                    result.content(),
                    hit.score(),
                    document == null ? null : document.getSource()));
        }
        return List.copyOf(hits);
    }

    /**
     * 统计当前分块数量。
     *
     * @return 分块数
     */
    public int chunkCount() {
        Long count = chunkMapper.selectCount(null);
        return count == null ? 0 : count.intValue();
    }

    /**
     * 知识库统计：文档数、分块数、是否启用向量化、向量模型。
     *
     * <p>计数使用 int，避免全局 Long→String 序列化把统计数字变成字符串。</p>
     *
     * @return 统计结果，字段顺序固定便于前端展示
     */
    public Map<String, Object> stats() {
        Long documentCount = documentMapper.selectCount(null);
        Long storedChunkCount = chunkMapper.selectCount(null);
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("documentCount", documentCount == null ? 0 : documentCount.intValue());
        stats.put("chunkCount", storedChunkCount == null ? 0 : storedChunkCount.intValue());
        stats.put("embeddingEnabled", embeddingClient.enabled());
        stats.put("embeddingModel", embeddingClient.model());
        return stats;
    }

    /**
     * 在事务提交后重建索引，避免索引读到未提交数据、或并发灌库时快照缺文档。
     *
     * <p>没有活动事务时（例如单元测试直接调用）立即重建。</p>
     */
    private void rebuildIndexAfterCommit() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    rebuildIndex();
                }
            });
        } else {
            rebuildIndex();
        }
    }

    /**
     * 按文档 id 批量查询文档（命中结果通常只有几条，用于回填标题、分类与来源）。
     *
     * @param documentIds 文档 id 列表
     * @return 文档 id → 文档
     */
    private Map<Long, KnowledgeDocument> documentsByIds(List<Long> documentIds) {
        if (documentIds == null || documentIds.isEmpty()) {
            return Map.of();
        }
        List<KnowledgeDocument> documents = documentMapper.selectByIds(documentIds);
        if (documents == null || documents.isEmpty()) {
            return Map.of();
        }
        Map<Long, KnowledgeDocument> result = new HashMap<>();
        for (KnowledgeDocument document : documents) {
            result.put(document.getId(), document);
        }
        return result;
    }

    /**
     * 统计每个文档的分块数量。
     *
     * @return 文档 id → 分块数
     */
    private Map<Long, Integer> chunkCountByDocument() {
        List<KnowledgeChunk> chunks = chunkMapper.selectList(Wrappers.<KnowledgeChunk>lambdaQuery()
                .select(KnowledgeChunk::getDocumentId));
        Map<Long, Integer> counts = new HashMap<>();
        for (KnowledgeChunk chunk : chunks) {
            if (chunk.getDocumentId() != null) {
                counts.merge(chunk.getDocumentId(), 1, Integer::sum);
            }
        }
        return counts;
    }

    /**
     * 生成单个分块的向量 JSON。
     *
     * <p>未启用向量化或调用失败时返回 {@code null}，由调用方把该分块作为「仅 BM25」分块保存。</p>
     *
     * @param text 分块正文
     * @return 向量 JSON，失败或未启用时为 {@code null}
     */
    private String embeddingOf(String text) {
        if (!embeddingClient.enabled() || TextUtils.isBlank(text)) {
            return null;
        }
        try {
            List<Double> vector = embeddingClient.embed(text);
            if (vector == null || vector.isEmpty()) {
                return null;
            }
            List<Double> rounded = new ArrayList<>(vector.size());
            for (Double value : vector) {
                double safe = value == null ? 0.0 : value;
                rounded.add(Math.round(safe * VECTOR_SCALE) / VECTOR_SCALE);
            }
            return JsonUtils.toJson(rounded);
        } catch (RuntimeException e) {
            log.warn("分块向量化失败，该分块仅参与 BM25 检索 length={} reason={}", text.length(), e.getMessage());
            return null;
        }
    }

    /**
     * 解析分块向量 JSON。
     *
     * @param json 向量 JSON
     * @return 向量；为空或解析失败时返回 {@code null}
     */
    private static List<Double> parseVector(String json) {
        if (TextUtils.isBlank(json)) {
            return null;
        }
        try {
            List<Double> vector = JsonUtils.fromJson(json, new TypeReference<List<Double>>() {
            });
            return vector == null || vector.isEmpty() ? null : vector;
        } catch (RuntimeException e) {
            log.debug("分块向量解析失败，按无向量处理 reason={}", e.getMessage());
            return null;
        }
    }

    /**
     * 计算两个向量的余弦相似度。
     *
     * @param left  向量 A
     * @param right 向量 B
     * @return 余弦相似度；维度不一致或零向量时返回 0
     */
    private static double cosine(List<Double> left, List<Double> right) {
        if (left == null || right == null || left.isEmpty() || left.size() != right.size()) {
            return 0.0;
        }
        double dot = 0.0;
        double leftNorm = 0.0;
        double rightNorm = 0.0;
        for (int index = 0; index < left.size(); index++) {
            Double leftValue = left.get(index);
            Double rightValue = right.get(index);
            double x = leftValue == null ? 0.0 : leftValue;
            double y = rightValue == null ? 0.0 : rightValue;
            dot += x * y;
            leftNorm += x * x;
            rightNorm += y * y;
        }
        if (leftNorm <= 0.0 || rightNorm <= 0.0) {
            return 0.0;
        }
        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }

    /**
     * 把余弦相似度夹到 [0, 1]，负相关不参与加分。
     *
     * @param value 余弦相似度
     * @return 夹取后的值
     */
    private static double clampCosine(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    /**
     * 带融合分的检索命中，便于排序后回填文档信息。
     *
     * @param result 原始 BM25 命中
     * @param score  最终相关性分数
     */
    private record ScoredHit(Bm25Index.SearchResult result, double score) {
    }
}
