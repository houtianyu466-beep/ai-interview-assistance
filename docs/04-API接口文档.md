# API 接口文档

> ⚠️ **产品定位已调整（v2.0）**：当前主流程是「面试练习」接口（第 0 章）。
> 第 4-8 章的岗位、题库、面试官视角接口属于旧招聘链路，前端已不再使用，仅保留兼容。
>
> Base URL：`http://localhost:8080`
> 在线文档：`/swagger-ui.html`（可直接调试）
> OpenAPI JSON：`/v3/api-docs`
> 所有接口前缀：`/api/v1`

---

## 0. 练习接口（当前主流程）

> 完整产品说明见 [《练习工具产品重构方案》](07-练习工具产品重构方案.md)。
> 知识库检索、AI 教练、视频房间、`/ws/call` 信令、`/ws/asr` 流式转写、PDF 简历见 [《RAG · Agent · 视频通话实现》](08-RAG-Agent-视频通话实现.md) 第 2-6 章。

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/v1/practice/targets` | 练习方向列表（8 个内置方向） |
| GET | `/api/v1/practice/targets/{id}` | 方向详情 |
| POST | `/api/v1/practice/sessions` | 创建练习（targetId / resumeId? / mode / totalQuestions） |
| GET | `/api/v1/practice/sessions/my` | 我的练习（page/size/status） |
| GET | `/api/v1/practice/sessions/{id}` | 练习详情 |
| POST | `/api/v1/practice/sessions/{id}/start` | 开始并返回第一题 |
| GET | `/api/v1/practice/sessions/{id}/current` | 当前题（刷新恢复） |
| POST | `/api/v1/practice/sessions/{id}/answers` | 提交答案（可带 audioPath） |
| POST | `/api/v1/practice/sessions/{id}/hint` | 获取提示（不给答案） |
| POST | `/api/v1/practice/sessions/{id}/skip` | 跳过本题（不计分） |
| POST | `/api/v1/practice/sessions/{id}/finish` | 提前结束 |
| GET | `/api/v1/practice/sessions/{id}/review` | 练习复盘 |
| GET | `/api/v1/practice/sessions/{id}/messages` | 对话回放 |
| POST | `/api/v1/practice/sessions/{id}/cancel` | 取消练习 |
| POST | `/api/v1/voice/transcribe` | 语音转写（multipart，字段 `file`，可选 `language`） |

**创建练习**

```json
{"targetId":"2125...","resumeId":"2125...","mode":"TECH","totalQuestions":5}
```

**复盘响应（节选）**

```json
{
  "session": {"id":"...","targetTitle":"Java 后端工程师","status":"FINISHED","totalQuestions":5,"currentIndex":5},
  "overallScore": 72.5,
  "level": "GOOD",
  "levelLabel": "良好",
  "dimensionScores": {"accuracy":78,"depth":66,"expression":80,"structure":70},
  "strengths": ["技术基础扎实"],
  "weaknesses": ["原理深度不足"],
  "nextSteps": ["重点改进：原理深度不足", "加强深度：先给结论，再补 2 个论据"],
  "questions": [{"seq":1,"content":"…","answer":"…","score":78,"comment":"…","referenceAnswer":"…"}]
}
```

**语音转写**

```bash
curl -X POST http://localhost:8080/api/v1/voice/transcribe \
  -H "Authorization: Bearer <token>" \
  -F "file=@answer.webm" -F "language=zh"
```

默认 `app.voice.provider=mock` 时返回 503 与配置提示；配置为 `openai` 后调用真实 ASR。

---

## 1. 通用约定

### 1.1 请求头

| Header | 必填 | 说明 |
|---|---|---|
| `Authorization` | 除公开接口外必填 | `Bearer <accessToken>` |
| `Content-Type` | POST/PUT 必填 | `application/json; charset=utf-8`，文件上传为 `multipart/form-data` |

### 1.2 统一响应

```json
{
  "code": 0,
  "message": "成功",
  "data": { },
  "timestamp": 1795680354000
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| code | int | 0 成功，其余见错误码 |
| message | string | 提示信息 |
| data | any | 业务数据 |
| timestamp | long | 服务端时间戳 |

### 1.3 分页响应

```json
{
  "total": 42,
  "current": 1,
  "size": 10,
  "pages": 5,
  "records": []
}
```

分页参数：`page`（从 1 开始，默认 1）、`size`（默认 10，最大 100）。

### 1.4 错误码

| code | HTTP | 含义 |
|---|---|---|
| 0 | 200 | 成功 |
| 40000 | 400 | 参数错误 / 业务校验失败 |
| 40100 | 401 | 未登录或令牌失效 |
| 40300 | 403 | 无权限（角色不足或访问他人数据） |
| 40400 | 404 | 资源不存在 |
| 40900 | 409 | 状态冲突（重复提交、状态不允许） |
| 42900 | 429 | 请求过于频繁 |
| 50000 | 500 | 业务处理失败 |
| 50200 | 503 | AI 服务不可用 |
| 50099 | 500 | 系统内部错误 |

### 1.5 数据类型约定

- 所有 `Long` 类型 id 以**字符串**返回（如 `"2125863319389638658"`），避免前端精度丢失；
- 时间格式：`yyyy-MM-dd HH:mm:ss`；
- 枚举以名称字符串返回（如 `"CANDIDATE"`、`"IN_PROGRESS"`）。

### 1.6 枚举取值

| 枚举 | 取值 |
|---|---|
| UserRole | CANDIDATE / INTERVIEWER / ADMIN |
| UserStatus | ACTIVE / DISABLED |
| JobLevel | JUNIOR / MIDDLE / SENIOR |
| JobStatus | DRAFT / OPEN / CLOSED |
| ResumeStatus | UPLOADED / PARSING / PARSED / FAILED |
| InterviewMode | TECH / HR / COMPREHENSIVE |
| InterviewStatus | CREATED / IN_PROGRESS / FINISHED / CANCELLED |
| Recommendation | PASS / HOLD / REJECT |
| MessageRole | SYSTEM / AI / CANDIDATE |
| MessageType | QUESTION / ANSWER / SCORE / SYSTEM |

---

## 2. 认证接口 `/api/v1/auth`

### 2.1 注册（公开）

`POST /api/v1/auth/register`

| 字段 | 类型 | 必填 | 校验 |
|---|---|---|---|
| username | string | 是 | 3-32 位，字母/数字/下划线 |
| password | string | 是 | 6-64 位 |
| realName | string | 否 | — |
| email | string | 否 | 邮箱格式 |
| phone | string | 否 | 中国大陆手机号 |

```bash
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"tom","password":"123456","realName":"汤姆","email":"tom@example.com"}'
```

响应（注册成功直接登录）：

```json
{
  "code": 0,
  "message": "成功",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiIs...",
    "tokenType": "Bearer",
    "expiresInSeconds": 14400,
    "user": {
      "id": "2125863319389638658",
      "username": "tom",
      "realName": "汤姆",
      "role": "CANDIDATE",
      "status": "ACTIVE",
      "createdAt": "2026-11-26 16:04:46"
    }
  }
}
```

> 注册角色固定为 `CANDIDATE`。面试官/管理员账号由管理员创建或数据库初始化。

### 2.2 登录（公开）

`POST /api/v1/auth/login`

| 字段 | 类型 | 必填 |
|---|---|---|
| username | string | 是 |
| password | string | 是 |

```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"candidate","password":"123456"}'
```

错误场景：

| 场景 | code | HTTP |
|---|---|---|
| 用户名或密码错误 | 40100 | 401 |
| 账号被禁用 | 40300 | 403 |

### 2.3 当前用户

`GET /api/v1/auth/me` → `UserVO`

### 2.4 登出

`POST /api/v1/auth/logout`

将当前令牌的 `jti` 加入黑名单（TTL 为剩余有效期）。重复登出/令牌非法均返回成功（幂等）。

---

## 3. 用户接口 `/api/v1/users`

### 3.1 修改资料

`PUT /api/v1/users/me`

```json
{"realName":"张伟","email":"zhangwei@example.com","phone":"13800000003","avatar":"https://..."}
```

### 3.2 修改密码

`PUT /api/v1/users/me/password`

```json
{"oldPassword":"123456","newPassword":"654321"}
```

校验：旧密码正确、新密码 6-64 位、新旧不能相同。

---

## 4. 岗位接口 `/api/v1/jobs`

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| GET | `/api/v1/jobs` | 登录 | 分页查询 |
| GET | `/api/v1/jobs/{id}` | 登录 | 详情 |
| POST | `/api/v1/jobs` | ADMIN / INTERVIEWER | 新建 |
| PUT | `/api/v1/jobs/{id}` | ADMIN / INTERVIEWER | 更新 |
| PATCH | `/api/v1/jobs/{id}/status?status=OPEN` | ADMIN / INTERVIEWER | 变更状态 |
| DELETE | `/api/v1/jobs/{id}` | ADMIN | 删除 |

查询参数：`keyword`（标题/部门模糊）、`status`、`page`、`size`。

创建/更新请求体：

```json
{
  "title": "Java 后端工程师",
  "department": "交易中台",
  "level": "SENIOR",
  "description": "负责交易中台核心服务...",
  "requirements": "5 年以上 Java 经验，熟悉 Spring Boot、MySQL、Redis...",
  "status": "OPEN"
}
```

响应 `JobVO`：

```json
{
  "id": "2125...",
  "title": "Java 后端工程师",
  "department": "交易中台",
  "level": "SENIOR",
  "description": "...",
  "requirements": "...",
  "status": "OPEN",
  "createdBy": "2125...",
  "createdAt": "2026-11-26 16:04:46",
  "updatedAt": "2026-11-26 16:04:46"
}
```

约束：删除仍有 `CREATED/IN_PROGRESS` 面试的岗位返回 409。

---

## 5. 题库接口 `/api/v1/questions`

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| GET | `/api/v1/questions` | 登录 | 分页（positionId / category / difficulty / keyword） |
| GET | `/api/v1/questions/{id}` | 登录 | 详情 |
| POST | `/api/v1/questions` | ADMIN / INTERVIEWER | 新增 |
| PUT | `/api/v1/questions/{id}` | ADMIN / INTERVIEWER | 更新 |
| DELETE | `/api/v1/questions/{id}` | ADMIN / INTERVIEWER | 删除 |

请求体：

```json
{
  "positionId": "2125...",
  "category": "JAVA",
  "difficulty": 3,
  "content": "HashMap 的底层结构是什么？",
  "referenceAnswer": "数组+链表+红黑树...",
  "keywords": "数组,链表,红黑树",
  "tags": "集合,高频"
}
```

---

## 6. 简历接口 `/api/v1/resumes`

### 6.1 上传简历

`POST /api/v1/resumes`（`multipart/form-data`）

| 参数 | 类型 | 说明 |
|---|---|---|
| file | file | 支持 `txt` / `md` / `docx`，≤10MB；`pdf` 会返回「请改用 docx 或粘贴文本」 |

```bash
curl -X POST http://localhost:8080/api/v1/resumes \
  -H "Authorization: Bearer <token>" \
  -F "file=@D:/resume.docx"
```

返回 `ResumeVO`，此时 `status=UPLOADED`（异步解析中会变 `PARSING` → `PARSED`）：

```json
{
  "id": "2125...",
  "fileName": "张伟-Java.docx",
  "yearsOfExperience": null,
  "education": null,
  "skills": null,
  "status": "UPLOADED",
  "createdAt": "2026-11-26 16:10:00"
}
```

### 6.2 文本录入简历

`POST /api/v1/resumes/text`

```json
{"content":"李雷，本科，6 年 Java 经验，熟悉 Spring Cloud、MySQL、Redis..."}
```

适合粘贴 PDF 内容或快速体验，同样触发异步解析。

### 6.3 我的简历

`GET /api/v1/resumes/my` → `List<ResumeVO>`，按创建时间倒序。

### 6.4 简历详情

`GET /api/v1/resumes/{id}` → `ResumeDetailVO`

```json
{
  "resume": { "id": "2125...", "status": "PARSED", "skills": "Java,Spring Boot,MySQL" },
  "rawText": "张伟，本科，5 年 Java...",
  "parsed": {
    "name": "张伟",
    "email": "zhangwei@example.com",
    "phone": "13800000003",
    "yearsOfExperience": 5,
    "education": "本科",
    "skills": ["Java", "Spring Boot", "MySQL", "Redis"],
    "workExperiences": [{"company": "某公司", "title": "高级 Java 工程师", "period": "2021.06-至今", "description": "..."}],
    "projects": [{"name": "订单服务重构", "role": "技术负责人", "period": "2023", "description": "..."}],
    "highlights": ["有高并发经验"],
    "summary": "5 年 Java 后端经验..."
  }
}
```

### 6.5 删除简历

`DELETE /api/v1/resumes/{id}`；被面试引用时返回 409。

### 6.6 岗位匹配度

`GET /api/v1/resumes/{id}/match?positionId={jobId}`

```json
{
  "score": 82.0,
  "matchedSkills": ["Java", "Spring Boot", "MySQL", "Redis"],
  "missingSkills": ["Kubernetes"],
  "advice": "技术栈匹配度较高，建议补充容器化经验。"
}
```

---

## 7. 面试接口 `/api/v1/interviews`

### 7.1 创建面试

`POST /api/v1/interviews`

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| positionId | string | 是 | 必须是 `OPEN` 状态岗位 |
| resumeId | string | 否 | 传则必须是本人 `PARSED` 简历；不传则用通用题 |
| mode | enum | 是 | TECH / HR / COMPREHENSIVE |
| totalQuestions | int | 否 | 3-15，默认 5 |

```bash
curl -X POST http://localhost:8080/api/v1/interviews \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{"positionId":"2125...","resumeId":"2125...","mode":"TECH","totalQuestions":5}'
```

响应：

```json
{
  "id": "2125...",
  "positionId": "2125...",
  "positionTitle": "Java 后端工程师",
  "resumeId": "2125...",
  "mode": "TECH",
  "status": "CREATED",
  "totalQuestions": 5,
  "currentIndex": 0,
  "createdAt": "2026-11-26 16:20:00"
}
```

创建时同步生成全部题目并落库；AI 不可用时自动用题库/模板补齐。

### 7.2 开始面试

`POST /api/v1/interviews/{id}/start`

- 仅 `CREATED` 可开始，重复开始返回 409；
- 返回第一题：

```json
{
  "questionId": "2125...",
  "seq": 1,
  "totalQuestions": 5,
  "content": "请介绍一下你项目里最能体现技术深度的模块...",
  "category": "JAVA",
  "difficulty": 2,
  "referenceAnswer": "需要说明核心原理、适用场景，并结合项目经验..."
}
```

### 7.3 获取当前题

`GET /api/v1/interviews/{id}/questions/current`

用于断线重连后恢复现场；已答完返回 409。

### 7.4 提交答案

`POST /api/v1/interviews/{id}/answers`

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| questionId | string | 是 | 当前题目 id |
| answer | string | 是 | ≤10000 字 |
| thinkingSeconds | int | 否 | 思考耗时 |

```json
{
  "questionId": "2125...",
  "answer": "HashMap 底层是数组 + 链表 + 红黑树...",
  "thinkingSeconds": 45
}
```

响应（非最后一题）：

```json
{
  "questionId": "2125...",
  "score": 78.0,
  "dimensionScores": {"accuracy": 80, "depth": 74, "expression": 79, "structure": 76},
  "comment": "回答准确，原理说明到位，建议补充扩容细节。",
  "hits": ["数组+链表", "红黑树"],
  "misses": ["扩容机制"],
  "finished": false,
  "nextQuestion": {
    "questionId": "2125...",
    "seq": 2,
    "totalQuestions": 5,
    "content": "Spring Bean 的生命周期包含哪些阶段？",
    "category": "SPRING",
    "difficulty": 3
  }
}
```

最后一题：

```json
{"finished": true, "reportId": "2125...", "score": 81.0}
```

错误场景：

| 场景 | code | HTTP |
|---|---|---|
| 题号与游标不匹配 | 40900 | 409 |
| 该题已作答 | 40900 | 409 |
| 重复提交（乐观锁冲突） | 40900 | 409 |
| 非本人面试 | 40300 | 403 |

### 7.5 提前结束

`POST /api/v1/interviews/{id}/finish` → `ReportVO`

已结束再次调用返回已有报告（幂等）。

### 7.6 取消面试

`POST /api/v1/interviews/{id}/cancel`；已结束返回 409。

### 7.7 查看报告

`GET /api/v1/interviews/{id}/report`（本人 / INTERVIEWER / ADMIN）→ `ReportVO`

```json
{
  "id": "2125...",
  "interviewId": "2125...",
  "overallScore": 78.0,
  "dimensionScores": {"accuracy": 78.5, "depth": 72.0, "expression": 80.0, "structure": 76.0},
  "strengths": ["技术基础扎实，能准确说出核心概念", "项目描述有条理"],
  "weaknesses": ["原理深度不足", "缺少量化指标"],
  "suggestion": "HOLD",
  "summary": "整体达到中级 Java 工程师水平，建议二面重点考察分布式。",
  "createdAt": "2026-11-26 16:30:00",
  "questions": [
    {
      "seq": 1,
      "content": "HashMap 的底层结构？",
      "answer": "...",
      "score": 78.0,
      "dimensionScores": {"accuracy": 80, "depth": 74, "expression": 79, "structure": 76},
      "comment": "回答准确..."
    }
  ]
}
```

### 7.8 重新生成报告

`POST /api/v1/interviews/{id}/report/regenerate`（仅 INTERVIEWER / ADMIN，且面试状态为 FINISHED）

### 7.9 对话回放

`GET /api/v1/interviews/{id}/messages`

```json
[
  {"id": "2125...", "role": "SYSTEM", "type": "SYSTEM", "content": "面试已创建，共 5 道题", "createdAt": "2026-11-26 16:20:00"},
  {"id": "2125...", "role": "AI", "type": "QUESTION", "content": "请介绍一下...", "createdAt": "2026-11-26 16:20:10"},
  {"id": "2125...", "role": "CANDIDATE", "type": "ANSWER", "content": "HashMap...", "createdAt": "2026-11-26 16:21:00"},
  {"id": "2125...", "role": "AI", "type": "SCORE", "content": "本题得分：78", "createdAt": "2026-11-26 16:21:01"}
]
```

### 7.10 切屏上报

`POST /api/v1/interviews/{id}/switch`，累加 `switch_count`（反作弊数据）。

### 7.11 面试列表

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| GET | `/api/v1/interviews/my` | 登录 | 我的面试（status/keyword/page/size） |
| GET | `/api/v1/interviews` | ADMIN / INTERVIEWER | 全部面试 |

### 7.12 面试详情

`GET /api/v1/interviews/{id}` → `InterviewDetailVO`（面试信息 + 题目简表）

---

## 8. 管理端接口 `/api/v1/admin`

> 类级 `@PreAuthorize("hasRole('ADMIN')")`，候选人/面试官访问返回 403。

### 8.1 统计概览

`GET /api/v1/admin/stats/overview`

```json
{
  "userCount": 3,
  "candidateCount": 1,
  "interviewerCount": 1,
  "interviewCount": 12,
  "finishedCount": 9,
  "inProgressCount": 2,
  "averageScore": 76.5,
  "last7Days": [{"date": "2026-11-20", "count": 1}, "..."],
  "statusDistribution": [{"status": "FINISHED", "count": 9}, {"status": "IN_PROGRESS", "count": 2}],
  "hotPositions": [{"positionId": "2125...", "title": "Java 后端工程师", "interviewCount": 7}]
}
```

### 8.2 用户列表

`GET /api/v1/admin/users?keyword=&role=&status=&page=1&size=10`

### 8.3 启用/禁用用户

`PATCH /api/v1/admin/users/{id}/status`

```json
{"status": "DISABLED"}
```

约束：不能禁用自己；不能禁用最后一个可用的管理员（返回 409）。

---

## 9. WebSocket 实时面试

### 9.1 连接

```
ws://localhost:8080/ws/interview?token=<accessToken>&interviewId=<面试id>
```

握手阶段校验令牌与面试归属，失败返回 401 并拒绝升级。

### 9.2 客户端消息

```json
{"type":"START"}
{"type":"ANSWER","content":"我的回答...","thinkingSeconds":45,"data":{"questionId":"2125..."}}
{"type":"SWITCH"}
{"type":"PING"}
```

### 9.3 服务端消息

| type | data | 说明 |
|---|---|---|
| CONNECTED | `{interviewId}` | 连接成功 |
| QUESTION | `CurrentQuestionVO` | 题目下发 |
| SCORE | `AnswerResultVO` | 本题评分 + 下一题 |
| FINISHED | `{reportId}` | 面试结束 |
| SWITCH_ACK | — | 切屏已记录 |
| PONG | — | 心跳响应 |
| ERROR | — | 错误提示（content 为原因） |

示例：

```json
{"type":"SCORE","content":"回答准确，原理说明到位","data":{"questionId":"2125...","score":78.0,"dimensionScores":{"accuracy":80},"finished":false,"nextQuestion":{"questionId":"2125...","seq":2,"content":"..."}}}
```

### 9.4 与 HTTP 的等价关系

WebSocket 只是实时通道，底层调用同一套 `InterviewService`。断线后可用 HTTP 接口继续：

- `GET /interviews/{id}/questions/current` 恢复当前题；
- `POST /interviews/{id}/answers` 继续作答。

---

## 10. 完整调用示例

```bash
# 1. 登录
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"candidate","password":"123456"}' | jq -r '.data.accessToken')

# 2. 查岗位
curl -s http://localhost:8080/api/v1/jobs?page=1&size=10 -H "Authorization: Bearer $TOKEN"

# 3. 查简历
curl -s http://localhost:8080/api/v1/resumes/my -H "Authorization: Bearer $TOKEN"

# 4. 创建面试
curl -s -X POST http://localhost:8080/api/v1/interviews \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"positionId":"<jobId>","resumeId":"<resumeId>","mode":"TECH","totalQuestions":5}'

# 5. 开始
curl -s -X POST http://localhost:8080/api/v1/interviews/<id>/start -H "Authorization: Bearer $TOKEN"

# 6. 作答
curl -s -X POST http://localhost:8080/api/v1/interviews/<id>/answers \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"questionId":"<qid>","answer":"...","thinkingSeconds":45}'

# 7. 报告
curl -s http://localhost:8080/api/v1/interviews/<id>/report -H "Authorization: Bearer $TOKEN"
```

Windows 下可直接运行封装好的脚本：

```powershell
powershell -ExecutionPolicy Bypass -File scripts/smoke-test.ps1
node scripts/ws-test.mjs
```

---

## 11. 调试建议

1. 打开 `/swagger-ui.html`，右上角 **Authorize** 填入登录返回的 `accessToken`（无需加 `Bearer` 前缀），即可直接调所有接口；
2. 409 优先看「状态机」与「题号游标」，而不是重试；
3. 403 分两种：角色不足（管理端）与资源归属（他人面试/简历）；
4. AI 相关异常先看 `ai_call_log` 表的 `success/fallback/error_message`；
5. 需要看 SQL 时，开发环境已开启 MyBatis 控制台日志。
