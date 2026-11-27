package com.aiinterview.ai;

import com.aiinterview.ai.model.QaPair;

import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.stream.Collectors;

/**
 * 提示词模板集中管理。
 *
 * <p>设计原则：</p>
 * <ol>
 *     <li>System 提示只描述角色与输出格式，User 提示提供本次业务数据；</li>
 *     <li>所有结构化输出都要求「只输出 JSON」，配合 {@code JsonUtils.stripCodeFence} 容错；</li>
 *     <li>Prompt 中显式列出 JSON 字段，减少模型自由发挥导致解析失败的概率。</li>
 * </ol>
 */
public final class PromptTemplates {

    private PromptTemplates() {
    }

    public static String resumeParseSystem() {
        return """
                你是一位资深技术招聘专家，擅长把简历原文解析为结构化数据。
                请严格按要求输出：
                1. 只输出一个 JSON 对象，不要输出解释、markdown 代码块以外的任何文字；
                2. 无法判断的字段使用 null 或空数组，禁止编造；
                3. yearsOfExperience 为整数（单位：年），可根据工作经历起止时间估算；
                4. skills 使用业界通用名称，例如 Java、Spring Boot、MySQL、Redis、Kubernetes；
                5. summary 用 80 字以内概括候选人画像。
                JSON 字段：
                {"name":"","email":"","phone":"","yearsOfExperience":0,"education":"",
                 "skills":[""],"workExperiences":[{"company":"","title":"","period":"","description":""}],
                 "projects":[{"name":"","role":"","period":"","description":""}],
                 "highlights":[""],"summary":""}
                """;
    }

    public static String resumeParseUser(String rawText) {
        return """
                请解析下面的简历原文，输出结构化 JSON。
                <resume>
                %s
                </resume>
                """.formatted(rawText);
    }

    public static String questionGenerateSystem() {
        return """
                你是一位资深技术面试官，负责为候选人设计面试题。
                输出要求：
                1. 只输出一个 JSON 数组，不要输出任何解释；
                2. 数组长度严格等于用户要求的题目数量；
                3. 难度覆盖 2-4 分（1 最简单，5 最难）；
                4. 若提供了候选人简历摘要，每一道题都必须引用简历里的具体项目名、技术栈或成果数字，
                   并以此展开追问，明确禁止出与简历无关的通用八股题；
                5. 若没有提供候选人简历摘要，则围绕应聘岗位、面试模式与练习方向出题；
                6. 用户会给出历史已问题目，必须换角度或换项目，禁止重复；
                7. referenceAnswer 用要点式描述，不超过 200 字；keywords 为 3-6 个关键得分点。
                JSON 数组元素格式：
                [{"content":"","category":"JAVA","difficulty":3,"referenceAnswer":"","keywords":[""]}]
                """;
    }

    /**
     * 出题 User 提示。
     *
     * @param positionTitle  应聘岗位或练习方向名称，可为空
     * @param jobDescription 岗位 JD 或练习方向描述，可为空
     * @param resumeSummary  候选人简历摘要，为空表示本次没有简历
     * @param count          需要生成的题目数量
     * @param mode           面试模式，可为空
     * @param avoidQuestions 历史已问题目，最多取前 30 条、每条截断 60 字，可为空
     * @return User 提示文本
     */
    public static String questionGenerateUser(String positionTitle, String jobDescription,
                                             String resumeSummary, int count, String mode,
                                             List<String> avoidQuestions) {
        String resumeBlock;
        if (resumeSummary == null || resumeSummary.isBlank()) {
            resumeBlock = """
                    【候选人简历摘要】未提供简历，请优先围绕应聘岗位、面试模式与练习方向出题。
                    """;
        } else {
            resumeBlock = """
                    【候选人简历摘要】
                    %s
                    【简历结合要求】每道题都必须引用简历里的具体项目名、技术栈或成果数字（例如项目名、QPS、P99、年限），
                    禁止出与简历无关的通用题；题干要能看出是针对这位候选人的追问。
                    """.formatted(truncate(resumeSummary, 2000));
        }
        return """
                请生成 %d 道面试题。
                【面试模式】%s
                【应聘岗位】%s
                【岗位 JD】%s
                %s
                %s
                要求：题目共 %d 道，请直接输出 JSON 数组。
                """.formatted(count, modeDescription(mode), positionTitle,
                truncate(jobDescription, 2000), resumeBlock, historyBlock(avoidQuestions), count);
    }

    /**
     * 拼接历史题干提示块：最多 30 条，每条截断 60 字。
     *
     * @param avoidQuestions 历史题干列表，可为空
     * @return 历史题干提示块，没有历史时返回空串
     */
    private static String historyBlock(List<String> avoidQuestions) {
        if (avoidQuestions == null || avoidQuestions.isEmpty()) {
            return "";
        }
        StringJoiner joiner = new StringJoiner("\n");
        int limit = Math.min(avoidQuestions.size(), 30);
        for (int i = 0; i < limit; i++) {
            String question = avoidQuestions.get(i);
            if (question == null || question.isBlank()) {
                continue;
            }
            joiner.add((i + 1) + ". " + truncate(question.trim(), 60));
        }
        if (joiner.length() == 0) {
            return "";
        }
        return """
                【历史已问题目（以下题目已经问过，必须换角度或换项目，禁止重复）】
                %s
                """.formatted(joiner);
    }

    public static String answerScoreSystem() {
        return """
                你是严格且专业的面试评分专家。请根据题目、参考答案和候选人回答进行评分。
                输出要求：
                1. 只输出一个 JSON 对象；
                2. score 为 0-100 的总分；
                3. 维度分：accuracy（准确性）、depth（深度/原理）、expression（表达清晰度）、structure（逻辑结构）；
                4. comment 用 60 字以内指出亮点与问题；hits/misses 各列 0-4 条关键点。
                评分标准：
                - 90-100：准确、完整且有深度，能举一反三；
                - 75-89：核心正确，存在少量遗漏；
                - 60-74：部分正确，理解不深入；
                - 0-59：概念错误、答非所问或未作答。
                JSON 格式：
                {"score":0,"dimensions":{"accuracy":0,"depth":0,"expression":0,"structure":0},
                 "comment":"","hits":[""],"misses":[""]}
                """;
    }

    public static String answerScoreUser(String question, String referenceAnswer, String answer, String mode) {
        return """
                【面试模式】%s
                【题目】%s
                【参考答案/评分要点】%s
                【候选人回答】%s
                请输出 JSON 评分结果。
                """.formatted(modeDescription(mode),
                truncate(question, 1000),
                truncate(referenceAnswer, 1500),
                answer == null || answer.isBlank() ? "（候选人未作答）" : truncate(answer, 3000));
    }

    public static String reportGenerateSystem() {
        return """
                你是面试复盘专家，需要根据整场面试的问答与得分生成结构化面试报告。
                输出要求：
                1. 只输出一个 JSON 对象；
                2. overallScore 为 0-100 的整场得分；
                3. dimensionScores 至少包含 accuracy、depth、expression、structure 四项均分；
                4. strengths / weaknesses 各 2-4 条，必须具体、可执行，禁止空话；
                5. suggestion 只能是 PASS / HOLD / REJECT：
                   overallScore>=80 且无严重缺陷为 PASS；60-79 为 HOLD；<60 为 REJECT；
                6. summary 用 120 字以内总结整场表现。
                JSON 格式：
                {"overallScore":0,"dimensionScores":{"accuracy":0,"depth":0,"expression":0,"structure":0},
                 "strengths":[""],"weaknesses":[""],"suggestion":"HOLD","summary":""}
                """;
    }

    public static String reportGenerateUser(String positionTitle, List<QaPair> qaPairs,
                                            double overallScore, Map<String, Double> dimensionScores) {
        StringJoiner joiner = new StringJoiner("\n");
        for (QaPair pair : qaPairs) {
            joiner.add("第%d题：%s".formatted(pair.seq(), truncate(pair.question(), 500)));
            joiner.add("候选人回答：%s".formatted(truncate(pair.answer(), 1200)));
            joiner.add("本题得分：%s".formatted(pair.score() == null ? "未评分" : pair.score()));
            if (pair.comment() != null && !pair.comment().isBlank()) {
                joiner.add("本题点评：%s".formatted(truncate(pair.comment(), 300)));
            }
        }
        String dimensions = dimensionScores.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining(", "));
        return """
                【应聘岗位】%s
                【整场平均分】%.1f
                【维度均分】%s
                【问答明细】
                %s
                请输出整场面试报告 JSON。
                """.formatted(positionTitle, overallScore, dimensions, joiner);
    }

    public static String matchSystem() {
        return """
                你是招聘匹配度分析专家。请对比候选人简历与岗位要求，输出匹配度分析。
                要求：只输出 JSON，score 为 0-100 的匹配分；
                matchedSkills / missingSkills 为技能名称数组；advice 用 80 字以内给出建议。
                JSON 格式：{"score":0,"matchedSkills":[""],"missingSkills":[""],"advice":""}
                """;
    }

    public static String matchUser(String resumeText, String jobTitle, String jobRequirements) {
        return """
                【岗位】%s
                【岗位要求】%s
                【候选人简历】%s
                请输出匹配度分析 JSON。
                """.formatted(truncate(jobTitle, 200), truncate(jobRequirements, 2000), truncate(resumeText, 4000));
    }

    /**
     * 练习提示的 System 提示：只给方向，不给答案。
     */
    public static String hintSystem() {
        return """
                你是面试练习教练，负责在练习者卡壳时给出启发式提示，帮助他自己想出来。
                输出要求：
                1. 只输出一句中文提示，不超过 60 字；
                2. 绝对不要给出完整答案，也不要逐条罗列得分要点；
                3. 只引导思考方向，例如先说结论、从哪几个角度展开、结合什么场景；
                4. 不要输出 JSON、编号、引号或任何解释性前缀。
                """;
    }

    /**
     * 练习提示的 User 提示（无参考资料版本）。
     *
     * @param question        当前题目
     * @param referenceAnswer 参考答案（仅供模型把握方向，不能原样泄露）
     * @param mode            练习模式
     * @return User 提示文本
     */
    public static String hintUser(String question, String referenceAnswer, String mode) {
        return hintUser(question, referenceAnswer, mode, null);
    }

    /**
     * 练习提示的 User 提示（带知识库参考资料版本）。
     *
     * <p>{@code context} 为空时与原来的三参版本完全一致，保证既有调用行为不变；
     * 有资料时追加「可参考以下资料，但不要照读答案」，仍然要求只输出一句 60 字以内的提示。</p>
     *
     * @param question        当前题目
     * @param referenceAnswer 参考答案（仅供模型把握方向，不能原样泄露）
     * @param mode            练习模式
     * @param context         知识库检索到的参考资料，可为空
     * @return User 提示文本
     */
    public static String hintUser(String question, String referenceAnswer, String mode, String context) {
        if (context == null || context.isBlank()) {
            return """
                    【练习模式】%s
                    【当前题目】%s
                    【参考答案/评分要点（仅供你把握方向，禁止原样泄露）】%s
                    请给出一句不超过 60 字的思考方向提示。
                    """.formatted(modeDescription(mode),
                    truncate(question, 1000),
                    truncate(referenceAnswer, 1500));
        }
        return """
                【练习模式】%s
                【当前题目】%s
                【参考答案/评分要点（仅供你把握方向，禁止原样泄露）】%s
                【知识库资料】
                %s
                可参考以下资料，但不要照读答案，只提炼思考方向。
                请给出一句不超过 60 字的思考方向提示。
                """.formatted(modeDescription(mode),
                truncate(question, 1000),
                truncate(referenceAnswer, 1500),
                truncate(context, 2000));
    }

    private static String modeDescription(String mode) {
        return switch (mode == null ? "" : mode) {
            case "TECH" -> "技术面，重点考察技术深度、原理理解与项目落地能力";
            case "HR" -> "HR 面，重点考察求职动机、稳定性、沟通表达与价值观";
            default -> "综合面，兼顾技术能力与软素质";
        };
    }

    private static String truncate(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        return text.length() <= maxLength ? text : text.substring(0, maxLength) + "...";
    }
}
