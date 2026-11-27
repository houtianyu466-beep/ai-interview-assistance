package com.aiinterview.knowledge;

import com.aiinterview.knowledge.entity.KnowledgeChunk;
import com.aiinterview.knowledge.service.Bm25Index;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link Bm25Index} 单元测试。
 *
 * <p>不依赖 Spring 容器与数据库：直接构造分块、重建索引、调用检索，
 * 验证中文 bigram 分词、英文关键词命中、空查询与无命中的真实行为。</p>
 */
class Bm25IndexTest {

    /** 与「分布式事务」强相关，且该词组出现 3 次 */
    private static final String DISTRIBUTED_STRONG = "分布式事务常见方案包括 TCC、本地消息表与 Saga。"
            + "强一致场景优先分布式事务的 TCC 模式，最终一致场景优先本地消息表，分布式事务的关键是幂等与补偿。";

    /** 与「分布式事务」弱相关：只出现 1 次，且文本更长 */
    private static final String DISTRIBUTED_WEAK = "面试官如果追问分布式事务，可以先说明业务为什么需要它："
            + "跨库写入无法依赖单机事务，然后再讲你实际用过的方案，并补充幂等、补偿与对账这些容易被忽略的细节，"
            + "最后说明当时为什么没有选择强一致的方案，以及上线后出现过什么问题。";

    /** 只包含英文关键词 Redis，不含中文查询词 */
    private static final String REDIS = "Redis 缓存穿透可以用布隆过滤器或空值缓存解决，"
            + "热点 key 失效时用互斥锁重建，避免请求直接打穿数据库。";

    @Test
    @DisplayName("中文 bigram：查询「分布式事务」命中相关分块且按分数降序")
    void shouldMatchChineseBigram() {
        Bm25Index index = new Bm25Index();
        index.rebuild(List.of(
                chunk(1L, 100L, 0, DISTRIBUTED_STRONG),
                chunk(2L, 200L, 0, REDIS),
                chunk(3L, 300L, 0, DISTRIBUTED_WEAK)), Map.of());

        List<Bm25Index.SearchResult> results = index.search("分布式事务", 5);

        assertThat(results).hasSize(2);
        assertThat(results.get(0).chunkId()).isEqualTo(1L);
        assertThat(results).extracting(Bm25Index.SearchResult::chunkId).doesNotContain(2L);
        assertThat(results.get(0).score()).isGreaterThan(0.0);
        // 两次出现「分布式事务」的分块应比只出现一次且更长的分块得分高
        assertThat(results.get(0).score()).isGreaterThan(results.get(1).score());
        for (int i = 1; i < results.size(); i++) {
            assertThat(results.get(i - 1).score()).isGreaterThanOrEqualTo(results.get(i).score());
        }
        // 分词确认为 bigram：连续中文串不会被当成一个整词
        assertThat(Bm25Index.tokenize("分布式事务")).contains("分布", "布式", "式事", "事务");
    }

    @Test
    @DisplayName("英文关键词：查询 Redis 命中对应分块")
    void shouldMatchEnglishKeyword() {
        Bm25Index index = new Bm25Index();
        index.rebuild(List.of(
                chunk(1L, 100L, 0, DISTRIBUTED_STRONG),
                chunk(2L, 200L, 0, REDIS),
                chunk(3L, 300L, 0, DISTRIBUTED_WEAK)), Map.of());

        List<Bm25Index.SearchResult> results = index.search("Redis", 5);

        assertThat(results).isNotEmpty();
        assertThat(results.get(0).chunkId()).isEqualTo(2L);
        assertThat(results.get(0).content()).contains("Redis");
        // 大小写不影响命中
        assertThat(index.search("redis", 5).get(0).chunkId()).isEqualTo(2L);
    }

    @Test
    @DisplayName("空查询、空白查询、无命中与空索引都返回空列表")
    void shouldReturnEmptyForBlankOrUnmatchedQuery() {
        Bm25Index index = new Bm25Index();
        index.rebuild(List.of(
                chunk(1L, 100L, 0, DISTRIBUTED_STRONG),
                chunk(2L, 200L, 0, REDIS),
                chunk(3L, 300L, 0, DISTRIBUTED_WEAK)), Map.of());

        assertThat(index.search(null, 5)).isEmpty();
        assertThat(index.search("", 5)).isEmpty();
        assertThat(index.search("   \n  ", 5)).isEmpty();
        assertThat(index.search("量子纠缠", 5)).isEmpty();
        assertThat(index.search("分布式事务", 0)).isEmpty();

        Bm25Index empty = new Bm25Index();
        empty.rebuild(List.of(), Map.of());
        assertThat(empty.search("分布式事务", 5)).isEmpty();
    }

    /**
     * 构造一个测试用分块。
     *
     * @param id          分块 id
     * @param documentId  文档 id
     * @param chunkIndex  分块序号
     * @param content     分块正文
     * @return 分块实体
     */
    private static KnowledgeChunk chunk(long id, long documentId, int chunkIndex, String content) {
        KnowledgeChunk chunk = new KnowledgeChunk();
        chunk.setId(id);
        chunk.setDocumentId(documentId);
        chunk.setChunkIndex(chunkIndex);
        chunk.setContent(content);
        return chunk;
    }
}
