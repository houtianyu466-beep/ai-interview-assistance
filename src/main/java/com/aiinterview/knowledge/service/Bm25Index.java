package com.aiinterview.knowledge.service;

import com.aiinterview.common.util.TextUtils;
import com.aiinterview.knowledge.entity.KnowledgeChunk;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 内存版 BM25 索引（k1 = 1.5，b = 0.75）。
 *
 * <p>为什么用 BM25：项目当前只有 DeepSeek（无 embedding 能力），BM25 不依赖任何外部服务，
 * 只要有语料就能算出真实相关性分数，也不存在「伪造引用」的问题。配置 embedding 后，
 * {@link KnowledgeService} 会在 BM25 召回的候选上再算余弦相似度，形成混合检索。</p>
 *
 * <p>分词规则：</p>
 * <ul>
 *     <li>连续 CJK 字符按 2-gram 切分（单字成词时保留单字）；</li>
 *     <li>连续 ASCII 字母/数字串整体小写作为一个词；</li>
 *     <li>过滤长度为 1 的停用词与符号（的/了/是/在/和/与/及/或/有/为/对/从/到/中/上/下 等）。</li>
 * </ul>
 *
 * <p>线程安全：{@link #rebuild} 在写锁内构建全新的不可变快照，随后一次性替换 volatile 引用；
 * 检索只读当前快照，因此重建与查询可以并发进行，不会读到半成品索引。</p>
 */
@Component
public class Bm25Index {

    /** BM25 词频饱和参数 */
    private static final double K1 = 1.5;
    /** BM25 文档长度归一化参数 */
    private static final double B = 0.75;
    /** 单次检索最多返回条数，防御异常入参 */
    private static final int MAX_TOP_K = 100;
    /** 参与打分的查询词元上限，避免长问句中的通用词稀释相关性 */
    private static final int MAX_QUERY_TERMS = 12;

    /** 单字停用词与常见英文停用词：只在「长度为 1」时参与过滤，不影响 2-gram */
    private static final Set<String> STOPWORDS = Set.of(
            "的", "了", "是", "在", "和", "与", "及", "或", "有", "为", "对", "从", "到", "中", "上", "下",
            "等", "就", "也", "都", "而", "被", "把", "让", "这", "那", "我", "你", "他", "她", "它", "们",
            "个", "之", "其", "以", "于", "并", "但", "很", "会", "要", "能", "可", "不", "没", "无", "着",
            "过", "还", "又", "最", "更", "如", "由", "向", "自", "所", "者",
            "a", "an", "the", "of", "to", "is", "in", "on", "and", "or", "for", "by", "at", "be", "as",
            "it", "this", "that", "with", "we", "you", "i");

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    /** 当前生效的索引快照，整体替换保证读一致性 */
    private volatile Snapshot snapshot = Snapshot.EMPTY;

    /**
     * 用全部知识分块重建索引。
     *
     * <p>标题也会参与分词，这样「STAR 法则」「自我介绍模板」这类标题词能直接命中分块；
     * 传入的 {@code docTitles} 同时还用于检索结果回填标题。</p>
     *
     * @param chunks    知识分块列表，允许为空
     * @param docTitles 文档 id → 标题，允许为空
     */
    public void rebuild(List<KnowledgeChunk> chunks, Map<Long, String> docTitles) {
        Map<Long, String> titles = docTitles == null ? Map.of() : docTitles;
        List<IndexedChunk> indexed = new ArrayList<>();
        Map<String, Integer> documentFrequency = new HashMap<>();
        long totalLength = 0L;
        if (chunks != null) {
            for (KnowledgeChunk chunk : chunks) {
                if (chunk == null || chunk.getId() == null || TextUtils.isBlank(chunk.getContent())) {
                    continue;
                }
                Long documentId = chunk.getDocumentId() == null ? 0L : chunk.getDocumentId();
                String title = titles.getOrDefault(documentId, "");
                // 标题重复一次做轻量加权：题目类查询（如「项目经历」）更容易命中对应文档
                List<String> tokens = tokenize(title + "\n" + title + "\n" + chunk.getContent());
                if (tokens.isEmpty()) {
                    continue;
                }
                Map<String, Integer> termFrequency = new HashMap<>();
                for (String token : tokens) {
                    termFrequency.merge(token, 1, Integer::sum);
                }
                for (String token : termFrequency.keySet()) {
                    documentFrequency.merge(token, 1, Integer::sum);
                }
                indexed.add(new IndexedChunk(
                        chunk.getId(),
                        documentId,
                        chunk.getChunkIndex() == null ? 0 : chunk.getChunkIndex(),
                        title,
                        chunk.getContent(),
                        chunk.getEmbedding(),
                        Map.copyOf(termFrequency),
                        tokens.size()));
                totalLength += tokens.size();
            }
        }
        double averageLength = indexed.isEmpty() ? 0.0 : (double) totalLength / indexed.size();
        Snapshot rebuilt = new Snapshot(Map.copyOf(documentFrequency), averageLength, List.copyOf(indexed));
        lock.writeLock().lock();
        try {
            snapshot = rebuilt;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 检索与查询最相关的分块，按 BM25 分数降序返回。
     *
     * @param query 查询文本
     * @param topK  返回条数，超过 {@value #MAX_TOP_K} 时按上限截断
     * @return 命中列表；查询为空或索引为空时返回空列表
     */
    public List<SearchResult> search(String query, int topK) {
        Snapshot current = snapshot;
        if (topK <= 0 || current.chunks.isEmpty()) {
            return List.of();
        }
        List<String> queryTokens = new ArrayList<>(new LinkedHashSet<>(tokenize(query)));
        if (queryTokens.isEmpty()) {
            return List.of();
        }
        int limit = Math.min(topK, MAX_TOP_K);
        int documentCount = current.chunks.size();
        // 长问句会带来大量低信息量 2-gram（如「一下」「说明」「重点」），只保留 IDF 最高的若干词元，
        // 避免通用词累加把真正相关的文档挤下去。
        List<String> weightedTokens = queryTokens.stream()
                .filter(token -> current.documentFrequency.getOrDefault(token, 0) > 0)
                .sorted(Comparator.comparingDouble((String token) -> {
                    int frequency = current.documentFrequency.getOrDefault(token, 0);
                    return Math.log(1.0 + (documentCount - frequency + 0.5) / (frequency + 0.5));
                }).reversed())
                .limit(MAX_QUERY_TERMS)
                .toList();
        if (weightedTokens.isEmpty()) {
            return List.of();
        }
        double averageLength = current.averageLength > 0 ? current.averageLength : 1.0;
        List<SearchResult> results = new ArrayList<>();
        for (IndexedChunk chunk : current.chunks) {
            double score = 0.0;
            for (String token : weightedTokens) {
                Integer frequency = current.documentFrequency.get(token);
                if (frequency == null || frequency == 0) {
                    continue;
                }
                int termFrequency = chunk.termFrequency().getOrDefault(token, 0);
                if (termFrequency == 0) {
                    continue;
                }
                double idf = Math.log(1.0 + (documentCount - frequency + 0.5) / (frequency + 0.5));
                double denominator = termFrequency
                        + K1 * (1.0 - B + B * chunk.length() / averageLength);
                score += idf * termFrequency * (K1 + 1.0) / denominator;
            }
            if (score > 0.0) {
                results.add(new SearchResult(
                        chunk.chunkId(),
                        chunk.documentId(),
                        chunk.chunkIndex(),
                        chunk.title(),
                        chunk.content(),
                        chunk.embedding(),
                        score));
            }
        }
        results.sort(Comparator.<SearchResult>comparingDouble(SearchResult::score).reversed()
                .thenComparingLong(SearchResult::chunkId));
        return results.size() <= limit ? List.copyOf(results) : List.copyOf(results.subList(0, limit));
    }

    /**
     * 返回索引中的分块数量（供健康检查与测试使用）。
     *
     * @return 已索引分块数
     */
    public int size() {
        return snapshot.chunks.size();
    }

    /**
     * 对文本分词：CJK 2-gram + ASCII 单词小写，过滤单字停用词与符号。
     *
     * @param text 待分词文本，允许为 {@code null}
     * @return 词元列表；入参为空白时返回空列表
     */
    public static List<String> tokenize(String text) {
        if (TextUtils.isBlank(text)) {
            return List.of();
        }
        List<String> tokens = new ArrayList<>();
        int index = 0;
        int length = text.length();
        while (index < length) {
            char current = text.charAt(index);
            if (isCjk(current)) {
                int start = index;
                while (index < length && isCjk(text.charAt(index))) {
                    index++;
                }
                String run = text.substring(start, index);
                if (run.length() == 1) {
                    addToken(tokens, run);
                } else {
                    for (int offset = 0; offset + 1 < run.length(); offset++) {
                        addToken(tokens, run.substring(offset, offset + 2));
                    }
                }
            } else if (isAsciiWord(current)) {
                int start = index;
                while (index < length && isAsciiWord(text.charAt(index))) {
                    index++;
                }
                addToken(tokens, text.substring(start, index).toLowerCase(Locale.ROOT));
            } else {
                index++;
            }
        }
        return tokens;
    }

    /**
     * 判断字符是否属于 CJK 统一表意文字。
     *
     * @param value 字符
     * @return CJK 返回 {@code true}
     */
    private static boolean isCjk(char value) {
        return (value >= '\u4e00' && value <= '\u9fff') || (value >= '\u3400' && value <= '\u4dbf');
    }

    /**
     * 判断字符是否属于 ASCII 单词字符（字母或数字）。
     *
     * @param value 字符
     * @return 字母或数字返回 {@code true}
     */
    private static boolean isAsciiWord(char value) {
        return (value >= 'a' && value <= 'z') || (value >= 'A' && value <= 'Z') || (value >= '0' && value <= '9');
    }

    /**
     * 收集一个词元：过滤空白、单字停用词与长度 1 的符号。
     *
     * @param tokens 结果列表
     * @param raw    原始词元
     */
    private static void addToken(List<String> tokens, String raw) {
        if (raw == null) {
            return;
        }
        String token = raw.trim();
        if (token.isEmpty() || STOPWORDS.contains(token)) {
            return;
        }
        if (token.length() == 1 && !Character.isLetterOrDigit(token.charAt(0))) {
            return;
        }
        tokens.add(token);
    }

    /**
     * 一个分块的索引视图（不可变）。
     *
     * @param chunkId       分块 id
     * @param documentId    文档 id
     * @param chunkIndex    分块序号
     * @param title         文档标题
     * @param content       分块正文
     * @param embedding     向量 JSON，可为空
     * @param termFrequency 词 → 词频
     * @param length        词元总数
     */
    private record IndexedChunk(
            long chunkId,
            long documentId,
            int chunkIndex,
            String title,
            String content,
            String embedding,
            Map<String, Integer> termFrequency,
            int length) {
    }

    /**
     * 一次检索命中结果。
     *
     * @param chunkId    分块 id
     * @param documentId 文档 id
     * @param chunkIndex 分块序号
     * @param title      文档标题
     * @param content    分块正文
     * @param embedding  向量 JSON，可为空；混合检索据此计算余弦
     * @param score      原始 BM25 分数
     */
    public record SearchResult(
            long chunkId,
            long documentId,
            int chunkIndex,
            String title,
            String content,
            String embedding,
            double score) {
    }

    /**
     * 不可变索引快照。
     */
    private static final class Snapshot {

        /** 空索引，避免启动时出现 null 判断 */
        private static final Snapshot EMPTY = new Snapshot(Map.of(), 0.0, List.of());

        private final Map<String, Integer> documentFrequency;
        private final double averageLength;
        private final List<IndexedChunk> chunks;

        private Snapshot(Map<String, Integer> documentFrequency, double averageLength, List<IndexedChunk> chunks) {
            this.documentFrequency = documentFrequency;
            this.averageLength = averageLength;
            this.chunks = chunks;
        }
    }
}
