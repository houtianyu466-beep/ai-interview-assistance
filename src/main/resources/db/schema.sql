-- =====================================================================
-- AI 面试平台 数据库脚本
-- 兼容：MySQL 8.0 / H2 2.x (MODE=MySQL)
-- 说明：主键由应用层雪花算法生成（MyBatis-Plus ASSIGN_ID），因此不使用 AUTO_INCREMENT
-- =====================================================================

DROP TABLE IF EXISTS knowledge_chunk;
DROP TABLE IF EXISTS knowledge_document;
DROP TABLE IF EXISTS agent_trace;
DROP TABLE IF EXISTS video_room;
DROP TABLE IF EXISTS sys_operation_log;
DROP TABLE IF EXISTS ai_call_log;
DROP TABLE IF EXISTS interview_report;
DROP TABLE IF EXISTS interview_message;
DROP TABLE IF EXISTS interview_question;
DROP TABLE IF EXISTS interview_session;
DROP TABLE IF EXISTS practice_target;
DROP TABLE IF EXISTS resume;
DROP TABLE IF EXISTS question_bank;
DROP TABLE IF EXISTS job_position;
DROP TABLE IF EXISTS sys_user;

-- ---------------------------------------------------------------------
-- 用户表
-- ---------------------------------------------------------------------
CREATE TABLE sys_user (
    id            BIGINT       NOT NULL,
    username      VARCHAR(64)  NOT NULL,
    password      VARCHAR(100) NOT NULL COMMENT 'BCrypt 加密后的密码',
    real_name     VARCHAR(64),
    email         VARCHAR(128),
    phone         VARCHAR(32),
    role          VARCHAR(20)  NOT NULL COMMENT 'CANDIDATE / INTERVIEWER / ADMIN',
    status        VARCHAR(20)  NOT NULL COMMENT 'ACTIVE / DISABLED',
    avatar        VARCHAR(255),
    last_login_at DATETIME,
    created_at    DATETIME,
    updated_at    DATETIME,
    CONSTRAINT pk_sys_user PRIMARY KEY (id),
    CONSTRAINT uk_sys_user_username UNIQUE (username)
);

-- ---------------------------------------------------------------------
-- 岗位表
-- ---------------------------------------------------------------------
CREATE TABLE job_position (
    id           BIGINT       NOT NULL,
    title        VARCHAR(128) NOT NULL,
    department   VARCHAR(64),
    level        VARCHAR(20) COMMENT 'JUNIOR / MIDDLE / SENIOR',
    description  TEXT COMMENT '岗位职责',
    requirements TEXT COMMENT '任职要求',
    status       VARCHAR(20) NOT NULL COMMENT 'DRAFT / OPEN / CLOSED',
    created_by   BIGINT,
    created_at   DATETIME,
    updated_at   DATETIME,
    CONSTRAINT pk_job_position PRIMARY KEY (id)
);
CREATE INDEX idx_job_position_status ON job_position (status);

-- ---------------------------------------------------------------------
-- 题库表
-- ---------------------------------------------------------------------
CREATE TABLE question_bank (
    id               BIGINT      NOT NULL,
    position_id      BIGINT COMMENT '为空表示通用题',
    category         VARCHAR(32) NOT NULL COMMENT 'JAVA / SPRING / MYSQL / REDIS / ALGORITHM / BEHAVIOR 等',
    difficulty       INT         NOT NULL COMMENT '1-5',
    content          TEXT        NOT NULL,
    reference_answer TEXT,
    keywords         VARCHAR(512) COMMENT '英文逗号分隔，用于规则评分',
    tags             VARCHAR(255),
    source           VARCHAR(20) COMMENT 'MANUAL / AI',
    created_by       BIGINT,
    created_at       DATETIME,
    updated_at       DATETIME,
    CONSTRAINT pk_question_bank PRIMARY KEY (id)
);
CREATE INDEX idx_question_bank_category ON question_bank (category);
CREATE INDEX idx_question_bank_position ON question_bank (position_id);

-- ---------------------------------------------------------------------
-- 简历表
-- ---------------------------------------------------------------------
CREATE TABLE resume (
    id                   BIGINT       NOT NULL,
    user_id              BIGINT       NOT NULL,
    file_name            VARCHAR(255),
    file_path            VARCHAR(512),
    raw_text             TEXT,
    parsed_json          TEXT COMMENT 'AI 解析出的结构化简历 JSON',
    skills               VARCHAR(1024) COMMENT '英文逗号分隔，便于检索',
    years_of_experience  INT,
    education            VARCHAR(64),
    status               VARCHAR(20)  NOT NULL COMMENT 'UPLOADED / PARSING / PARSED / FAILED',
    error_message        VARCHAR(512),
    created_at           DATETIME,
    updated_at           DATETIME,
    CONSTRAINT pk_resume PRIMARY KEY (id)
);
CREATE INDEX idx_resume_user ON resume (user_id);

-- ---------------------------------------------------------------------
-- 练习方向表（面试练习工具的核心字典：Java 后端 / 前端 / 产品经理 ...）
-- ---------------------------------------------------------------------
CREATE TABLE practice_target (
    id                BIGINT       NOT NULL,
    code              VARCHAR(32)  NOT NULL,
    title             VARCHAR(64)  NOT NULL,
    subtitle          VARCHAR(128),
    category          VARCHAR(32)  NOT NULL,
    description       TEXT,
    focus_topics      VARCHAR(512) COMMENT '英文逗号分隔的重点考察方向',
    default_questions INT          NOT NULL DEFAULT 5,
    difficulty        INT          NOT NULL DEFAULT 3,
    icon              VARCHAR(32),
    sort              INT          NOT NULL DEFAULT 0,
    created_at        DATETIME,
    updated_at        DATETIME,
    CONSTRAINT pk_practice_target PRIMARY KEY (id),
    CONSTRAINT uk_practice_target_code UNIQUE (code)
);

-- ---------------------------------------------------------------------
-- 面试会话表
-- ---------------------------------------------------------------------
CREATE TABLE interview_session (
    id                BIGINT      NOT NULL,
    user_id           BIGINT      NOT NULL COMMENT '候选人',
    position_id       BIGINT      NOT NULL,
    resume_id         BIGINT,
    mode              VARCHAR(20) NOT NULL COMMENT 'TECH / HR / COMPREHENSIVE',
    status            VARCHAR(20) NOT NULL COMMENT 'CREATED / IN_PROGRESS / FINISHED / CANCELLED',
    total_questions   INT         NOT NULL DEFAULT 5,
    current_index     INT         NOT NULL DEFAULT 0 COMMENT '已完成答题数',
    started_at        DATETIME,
    finished_at       DATETIME,
    duration_seconds  INT,
    switch_count      INT         NOT NULL DEFAULT 0 COMMENT '切屏次数（前端上报）',
    created_at        DATETIME,
    updated_at        DATETIME,
    CONSTRAINT pk_interview_session PRIMARY KEY (id)
);
CREATE INDEX idx_interview_user ON interview_session (user_id);
CREATE INDEX idx_interview_status ON interview_session (status);

-- ---------------------------------------------------------------------
-- 面试题目表（每个会话生成一份题目快照）
-- ---------------------------------------------------------------------
CREATE TABLE interview_question (
    id               BIGINT       NOT NULL,
    interview_id     BIGINT       NOT NULL,
    seq              INT          NOT NULL COMMENT '题号，从 1 开始',
    content          TEXT         NOT NULL,
    category         VARCHAR(32),
    difficulty       INT,
    reference_answer TEXT,
    answer           TEXT,
    answer_at        DATETIME,
    thinking_seconds INT,
    score            DECIMAL(5,2),
    dimension_scores TEXT COMMENT '维度得分 JSON',
    comment          TEXT COMMENT 'AI 点评',
    created_at       DATETIME,
    updated_at       DATETIME,
    CONSTRAINT pk_interview_question PRIMARY KEY (id)
);
CREATE INDEX idx_interview_question_interview ON interview_question (interview_id, seq);

-- ---------------------------------------------------------------------
-- 面试对话消息表（用于回放）
-- ---------------------------------------------------------------------
CREATE TABLE interview_message (
    id           BIGINT      NOT NULL,
    interview_id BIGINT      NOT NULL,
    role         VARCHAR(20) NOT NULL COMMENT 'SYSTEM / AI / CANDIDATE',
    type         VARCHAR(20) NOT NULL COMMENT 'QUESTION / ANSWER / SCORE / SYSTEM',
    content      TEXT,
    created_at   DATETIME,
    CONSTRAINT pk_interview_message PRIMARY KEY (id)
);
CREATE INDEX idx_interview_message_interview ON interview_message (interview_id);

-- ---------------------------------------------------------------------
-- 面试报告表
-- ---------------------------------------------------------------------
CREATE TABLE interview_report (
    id               BIGINT       NOT NULL,
    interview_id     BIGINT       NOT NULL,
    overall_score    DECIMAL(5,2),
    dimension_scores TEXT COMMENT '维度均分 JSON',
    strengths        TEXT,
    weaknesses       TEXT,
    suggestion       VARCHAR(20) COMMENT 'PASS / HOLD / REJECT',
    summary          TEXT,
    created_at       DATETIME,
    CONSTRAINT pk_interview_report PRIMARY KEY (id),
    CONSTRAINT uk_interview_report_interview UNIQUE (interview_id)
);

-- ---------------------------------------------------------------------
-- AI 调用日志表
-- ---------------------------------------------------------------------
CREATE TABLE ai_call_log (
    id                BIGINT      NOT NULL,
    scene             VARCHAR(32) NOT NULL COMMENT 'RESUME_PARSE / QUESTION_GENERATE / ANSWER_SCORE / REPORT_GENERATE / RESUME_MATCH',
    provider          VARCHAR(32),
    model             VARCHAR(64),
    prompt_tokens     INT,
    completion_tokens INT,
    latency_ms        INT,
    success           TINYINT     NOT NULL DEFAULT 1,
    fallback          TINYINT     NOT NULL DEFAULT 0 COMMENT '是否走了降级逻辑',
    error_message     VARCHAR(512),
    created_at        DATETIME,
    CONSTRAINT pk_ai_call_log PRIMARY KEY (id)
);
CREATE INDEX idx_ai_call_log_scene ON ai_call_log (scene);

-- ---------------------------------------------------------------------
-- 操作日志表
-- ---------------------------------------------------------------------
CREATE TABLE sys_operation_log (
    id         BIGINT       NOT NULL,
    user_id    BIGINT,
    username   VARCHAR(64),
    action     VARCHAR(64),
    method     VARCHAR(255),
    path       VARCHAR(255),
    ip         VARCHAR(64),
    cost_ms    INT,
    success    TINYINT,
    created_at DATETIME,
    CONSTRAINT pk_sys_operation_log PRIMARY KEY (id)
);

-- ---------------------------------------------------------------------
-- 知识库文档表（RAG 知识库：面试方法论 / 技术资料）
-- ---------------------------------------------------------------------
CREATE TABLE knowledge_document (
    id         BIGINT       NOT NULL,
    title      VARCHAR(200) NOT NULL,
    category   VARCHAR(32),
    source     VARCHAR(32)  NOT NULL DEFAULT 'MANUAL',
    content    TEXT,
    status     VARCHAR(20)  NOT NULL DEFAULT 'READY',
    created_at DATETIME,
    updated_at DATETIME,
    CONSTRAINT pk_knowledge_document PRIMARY KEY (id)
);

-- ---------------------------------------------------------------------
-- 知识库分块表（BM25 检索的最小单元；embedding 为可选的向量 JSON）
-- ---------------------------------------------------------------------
CREATE TABLE knowledge_chunk (
    id          BIGINT      NOT NULL,
    document_id BIGINT      NOT NULL,
    chunk_index INT         NOT NULL,
    content     TEXT        NOT NULL,
    tokens      INT,
    embedding   TEXT,
    created_at  DATETIME,
    CONSTRAINT pk_knowledge_chunk PRIMARY KEY (id),
    CONSTRAINT uk_knowledge_chunk_doc_idx UNIQUE (document_id, chunk_index)
);
CREATE INDEX idx_knowledge_chunk_doc ON knowledge_chunk (document_id);

-- ---------------------------------------------------------------------
-- Agent 调用轨迹表（供面试教练 Agent 记录每步工具调用）
-- ---------------------------------------------------------------------
CREATE TABLE agent_trace (
    id         BIGINT      NOT NULL,
    user_id    BIGINT,
    session_id BIGINT,
    scene      VARCHAR(32),
    input      TEXT,
    tool_calls TEXT,
    output     TEXT,
    success    TINYINT,
    latency_ms INT,
    created_at DATETIME,
    CONSTRAINT pk_agent_trace PRIMARY KEY (id)
);
CREATE INDEX idx_agent_trace_session ON agent_trace (session_id);

-- ---------------------------------------------------------------------
-- 视频练习房间表（WebRTC 双人模拟面试）
-- ---------------------------------------------------------------------
CREATE TABLE video_room (
    id                  BIGINT      NOT NULL,
    code                VARCHAR(12) NOT NULL,
    mode                VARCHAR(16) NOT NULL,
    practice_session_id BIGINT,
    host_user_id        BIGINT      NOT NULL,
    status              VARCHAR(16) NOT NULL,
    created_at          DATETIME,
    ended_at            DATETIME,
    CONSTRAINT pk_video_room PRIMARY KEY (id),
    CONSTRAINT uk_video_room_code UNIQUE (code)
);
