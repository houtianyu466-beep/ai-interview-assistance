package com.aiinterview.knowledge.service;

import com.aiinterview.common.util.TextUtils;
import com.aiinterview.knowledge.config.RagProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 文本分块器：把长文档切成适合检索的语义块。
 *
 * <p>切分策略：</p>
 * <ol>
 *     <li>先按空行/换行拆成段落，段落本身不超过目标长度时整段保留；</li>
 *     <li>超长段落再按中文标点（。！？；…，英文 !?;）断句，单句仍超长时按目标长度硬切；</li>
 *     <li>按目标长度（默认 600 字符）顺序装箱，形成分块；</li>
 *     <li>相邻分块之间保留 {@code chunkOverlap}（默认 80 字符）重叠，重叠起点尽量落在句子边界，
 *         避免把一句话从中间截断导致检索时语义丢失；</li>
 *     <li>空白分块直接丢弃，任何非空文档至少产出 1 个分块。</li>
 * </ol>
 */
@Component
public class ChunkSplitter {

    /** 未配置或配置非法时使用的默认分块长度 */
    private static final int DEFAULT_CHUNK_SIZE = 600;
    /** 未配置或配置非法时使用的默认重叠长度 */
    private static final int DEFAULT_CHUNK_OVERLAP = 80;

    /** 视为句子结束的标点，用于断句与重叠起点对齐 */
    private static final String SENTENCE_END = "。！？；…!?;";

    private final RagProperties properties;

    /**
     * 构造器注入。
     *
     * @param properties RAG 配置，提供分块长度与重叠长度
     */
    public ChunkSplitter(RagProperties properties) {
        this.properties = properties;
    }

    /**
     * 把文档正文切分为若干分块。
     *
     * @param content 文档正文，允许为空
     * @return 分块列表；入参为空白时返回空列表，否则至少 1 个分块
     */
    public List<String> split(String content) {
        if (TextUtils.isBlank(content)) {
            return List.of();
        }
        int chunkSize = properties.getChunkSize() > 0 ? properties.getChunkSize() : DEFAULT_CHUNK_SIZE;
        int configuredOverlap = properties.getChunkOverlap() >= 0
                ? properties.getChunkOverlap() : DEFAULT_CHUNK_OVERLAP;
        // 重叠不能超过半个分块，否则会退化成大量重复内容
        int overlap = Math.min(configuredOverlap, chunkSize / 2);

        String normalized = content.replace("\r\n", "\n").replace('\r', '\n').trim();
        List<String> units = new ArrayList<>();
        for (String paragraph : normalized.split("\n+")) {
            String text = paragraph.trim();
            if (text.isEmpty()) {
                continue;
            }
            if (text.length() <= chunkSize) {
                units.add(text);
                continue;
            }
            for (String sentence : splitSentences(text)) {
                for (String piece : hardSplit(sentence, chunkSize)) {
                    String trimmed = piece.trim();
                    if (!trimmed.isEmpty()) {
                        units.add(trimmed);
                    }
                }
            }
        }

        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String unit : units) {
            if (current.isEmpty()) {
                current.append(unit);
                continue;
            }
            if (current.length() + 1 + unit.length() <= chunkSize) {
                current.append('\n').append(unit);
                continue;
            }
            String finished = current.toString().trim();
            if (!finished.isEmpty()) {
                chunks.add(finished);
            }
            // 新分块以旧分块尾部作为重叠，长度上限要扣掉当前段落与换行符
            String tail = overlapTail(finished, Math.min(overlap, chunkSize - unit.length() - 1));
            current = new StringBuilder(tail);
            if (!current.isEmpty()) {
                current.append('\n');
            }
            current.append(unit);
        }
        String last = current.toString().trim();
        if (!last.isEmpty()) {
            chunks.add(last);
        }
        if (chunks.isEmpty()) {
            // 理论上不会走到这里（非空文档必有 unit），兜底保证至少 1 块
            chunks.add(normalized.length() <= chunkSize ? normalized : normalized.substring(0, chunkSize));
        }
        return chunks;
    }

    /**
     * 按中文/英文句末标点断句，连续标点会跟随前一句。
     *
     * @param text 待断句文本
     * @return 句子列表，保持原有顺序
     */
    private static List<String> splitSentences(String text) {
        List<String> sentences = new ArrayList<>();
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < text.length(); index++) {
            char current = text.charAt(index);
            builder.append(current);
            if (SENTENCE_END.indexOf(current) < 0) {
                continue;
            }
            while (index + 1 < text.length() && SENTENCE_END.indexOf(text.charAt(index + 1)) >= 0) {
                builder.append(text.charAt(++index));
            }
            sentences.add(builder.toString());
            builder.setLength(0);
        }
        if (!builder.isEmpty()) {
            sentences.add(builder.toString());
        }
        return sentences;
    }

    /**
     * 把超长句子按目标长度硬切，保证每个片段不超过 {@code chunkSize}。
     *
     * @param sentence  句子
     * @param chunkSize 目标长度
     * @return 片段列表
     */
    private static List<String> hardSplit(String sentence, int chunkSize) {
        String text = sentence.trim();
        if (text.isEmpty()) {
            return List.of();
        }
        List<String> parts = new ArrayList<>();
        for (int start = 0; start < text.length(); start += chunkSize) {
            parts.add(text.substring(start, Math.min(start + chunkSize, text.length())));
        }
        return parts;
    }

    /**
     * 取上一分块尾部作为重叠内容，并尽量从句子边界开始。
     *
     * @param text      上一分块内容
     * @param maxLength 允许的最大重叠长度
     * @return 重叠文本；无可用重叠时返回空串
     */
    private static String overlapTail(String text, int maxLength) {
        if (maxLength <= 0 || text.isEmpty()) {
            return "";
        }
        int start = Math.max(0, text.length() - maxLength);
        String tail = text.substring(start).trim();
        int boundary = -1;
        for (int index = 0; index < tail.length() - 1; index++) {
            if (SENTENCE_END.indexOf(tail.charAt(index)) >= 0) {
                boundary = index;
                break;
            }
        }
        if (boundary >= 0 && boundary + 1 < tail.length()) {
            return tail.substring(boundary + 1).trim();
        }
        return tail;
    }
}
