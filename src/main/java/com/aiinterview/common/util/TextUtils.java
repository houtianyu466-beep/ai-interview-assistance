package com.aiinterview.common.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 文本处理工具：主要在「AI 不可用时的规则降级」与关键词命中判断中使用。
 */
public final class TextUtils {

    private TextUtils() {
    }

    public static boolean isBlank(String text) {
        return text == null || text.isBlank();
    }

    public static String defaultIfBlank(String text, String fallback) {
        return isBlank(text) ? fallback : text;
    }

    public static String truncate(String text, int maxLength) {
        if (text == null) {
            return null;
        }
        return text.length() <= maxLength ? text : text.substring(0, maxLength) + "...";
    }

    /** 按英文/中文逗号、分号、换行切分，并去重去空。 */
    public static List<String> splitCsv(String text) {
        if (isBlank(text)) {
            return List.of();
        }
        String[] parts = text.split("[,，;；\\n\\r]");
        Set<String> result = new LinkedHashSet<>();
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return new ArrayList<>(result);
    }

    public static String joinCsv(Collection<String> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        return String.join(",", values);
    }

    /** 返回在文本中命中的关键词（忽略大小写）。 */
    public static List<String> hits(String text, Collection<String> keywords) {
        List<String> result = new ArrayList<>();
        if (isBlank(text) || keywords == null) {
            return result;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        for (String keyword : keywords) {
            if (keyword == null || keyword.isBlank()) {
                continue;
            }
            if (lower.contains(keyword.toLowerCase(Locale.ROOT))) {
                result.add(keyword.trim());
            }
        }
        return result;
    }

    /** 粗粒度字数统计：中文字符按 1 个字计，英文按单词计。 */
    public static int countWords(String text) {
        if (isBlank(text)) {
            return 0;
        }
        int chinese = 0;
        for (char c : text.toCharArray()) {
            if (c >= '\u4e00' && c <= '\u9fff') {
                chinese++;
            }
        }
        String[] englishWords = text.replaceAll("[\\u4e00-\\u9fff]", " ")
                .trim().split("\\s+");
        int english = englishWords.length == 1 && englishWords[0].isEmpty() ? 0 : englishWords.length;
        return chinese + english;
    }

    public static String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }
}
