# AI 面试练习 · 前端

> Vue 3 + TypeScript + Vite + Pinia + Element Plus
> 面向求职者的 AI 模拟面试练习工具：选方向 → 作答（文字/语音）→ 反馈 → 复盘。

---

## 1. 快速开始

前提：后端已启动（`http://localhost:8080`）。

```bash
cd web
npm install
npm run dev
```

打开 http://localhost:5173 。

### 演示账号

| 用户名 | 密码 | 角色 |
|---|---|---|
| candidate | 123456 | 练习者 |

## 2. 已实现页面

| 路由 | 页面 | 说明 |
|---|---|---|
| `/login`、`/register` | 登录 / 注册 | 注册即练习者 |
| `/practice` | 练习工作台 | 继续练习、累计/完成/最近得分、8 个方向、指南入口 |
| `/practice/new` | 开始练习 | 选方向、可选简历、模式、题数 |
| `/practice/:id/room` | 练习房间 | 题目、提示、跳过、文字作答、录音转写、AI 教练、四维反馈、下一题 |
| `/video` | 视频面试大厅 | AI 模拟视频面试、创建/加入真人 1v1 房间 |
| `/call/:code` | 视频通话 | 微信风格全屏：远端画面、本地 PiP、麦克风/摄像头/挂断；AI 模式含 TTS 与字幕 |
| `/practice/:id/review` | 练习复盘 | 总分环、等级、四维、优势/短板、下一步建议、逐题参考答案、打印 |
| `/practice/history` | 练习记录 | 筛选、分页、继续 / 复盘 |
| `/guide` | 面试指南 | 流程、清单、自我介绍、STAR、常见问题、语音建议 |
| `/resumes`、`/resumes/:id` | 我的简历 | 上传/粘贴、解析轮询、结构化详情（用于个性化出题） |
| `/profile` | 个人中心 | 资料、改密 |

## 3. 语音作答

- 浏览器 `MediaRecorder` 录音（优先 `audio/webm;codecs=opus`）；
- 停止后上传到 `POST /api/v1/voice/transcribe`；
- 转写文本**追加到答题框，可修改后再提交**；
- 未配置 ASR（默认 `app.voice.provider=mock`）时，页面提示「未配置」，文字作答不受影响；
- 不支持录音的浏览器会禁用录音并提示改用文字。

启用真实转写（后端环境变量）：

```bash
set APP_ASR_PROVIDER=openai
set APP_ASR_BASE_URL=https://api.openai.com/v1
set APP_ASR_API_KEY=sk-xxxx
set APP_ASR_MODEL=whisper-1
```

## 4. 页面截图

截图由 `npm run test:e2e` 自动生成，位于 `screenshots/`：

| 文件 | 内容 |
|---|---|
| `01-login.png` | 登录页 |
| `02-practice-home.png` | 练习工作台 |
| `03-practice-create.png` | 开始练习 |
| `04-practice-room.png` | 练习房间 |
| `05-practice-hint.png` | 获取提示 |
| `06-practice-feedback.png` | 单题反馈 |
| `07-practice-review.png` | 练习复盘 |
| `08-practice-history.png` | 练习记录 |
| `09-guide.png` | 面试指南 |
| `10-practice-mobile.png` | 移动端工作台 |
| `11-video-lobby.png` | 视频面试大厅 |
| `12-video-ai-question.png` | AI 视频面试出题 |
| `13-video-ai-feedback.png` | AI 视频面试评分 |
| `14-video-ai-finished.png` | AI 视频面试结束 |
| `15-video-human-call.png` | 真人 1v1 通话（房主） |
| `16-video-human-call-guest.png` | 真人 1v1 通话（加入方） |
| `17-coach.png` | AI 教练 + 知识库引用 |

## 5. 技术要点

- **接口层**：`src/api/http.ts` 统一解包 `Result`、转换 `ApiError`、401 广播；
- **练习 API**：`src/api/practice.ts`（13 个方法）、`src/api/voice.ts`；
- **类型**：`src/types/practice.ts` 与后端 DTO 对齐，所有 id 为字符串；
- **状态**：`auth` store 只持久化 token，用户信息每次 `/auth/me` 拉取；
- **房间状态**：进入时按 `CREATED/IN_PROGRESS/FINISHED` 分别 start / 恢复当前题 / 跳复盘；
- **草稿**：`localStorage` 按 `practice:draft:{sessionId}:{questionId}` 保存，提交/切题后清理；
- **AI 教练**：`CoachPanel` 调 `POST /practice/sessions/{id}/coach`，展示工具调用与知识库引用；无模型时提示「知识库检索模式」；
- **视频**：`useCallSocket`（心跳/重连）+ `useWebRTC`（perfect negotiation、ICE 队列）；AI 模式 TTS 用 `speechSynthesis`；
- **流式转写**：`useStreamingTranscription`（AudioWorklet → 16kHz PCM → `/ws/asr`），说完一句即出字；失败自动回退 `useVoiceRecorder` + `/voice/transcribe`；
- **录音资源**：`useVoiceRecorder` 在卸载时释放麦克风轨道与计时器；
- **构建**：`target: esnext` + `terser`，规避本机 esbuild 临时文件权限问题。

## 6. 目录结构

```
web/src
├── api/               # http、practice、voice、resume
├── components/        # PageHeader / StatusTag / EmptyState / practice/*
├── composables/       # useVoiceRecorder
├── layouts/           # AppShell（练习菜单）
├── router/            # 练习路由与守卫
├── stores/            # auth / ui
├── styles/            # 设计变量与全局样式
├── types/             # api.ts / practice.ts
├── utils/             # 存储、格式化、常量
└── views/
    ├── auth/          # 登录、注册
    ├── practice/      # 工作台、创建、房间、复盘、记录
    ├── guide/         # 面试指南
    ├── resume/        # 我的简历
    └── profile/       # 个人中心
```

## 7. 命令

| 命令 | 说明 |
|---|---|
| `npm run dev` | 开发服务器（5173） |
| `npm run build` | 类型检查 + 生产构建 |
| `npm run preview` | 预览构建产物 |
| `npm run typecheck` | 仅类型检查 |
| `npm run test:e2e` | 练习 + AI 教练 UI 冒烟（需本机 Chrome，输出截图） |
| `node scripts/video-test.mjs` | 视频面试 E2E（假摄像头，覆盖 AI 与真人 WebRTC） |
| `node scripts/asr-stream-test.mjs` | 流式转写 E2E（合成中文语音 → WS → VAD → ASR） |

## 8. 环境变量

| 变量 | 开发 | 生产 | 说明 |
|---|---|---|---|
| `VITE_API_BASE` | `/api` | `/api` | 由 Vite/Nginx 代理到后端 |
| `VITE_WS_BASE` | `ws://localhost:8080` | 留空（同源） | 预留实时通道 |

开发代理：`/api` → `http://localhost:8080`，`/ws` → `ws://localhost:8080`。

## 9. 常见问题

| 现象 | 处理 |
|---|---|
| 登录 401 | 后端未启动，或 8080 端口不是本项目 |
| 创建练习一直 loading | 真实模型出题可能 10-30 秒；Mock 模式通常 1-2 秒 |
| 录音点击无反应 | 检查浏览器麦克风权限；无麦克风时用文字作答 |
| 转写提示未配置 | 配置 `APP_ASR_PROVIDER/API_KEY`，或继续用文字 |
| 复盘 409 | 本次练习还未结束，回房间继续 |
| 页面空白且控制台 401 | 清理 `localStorage` 的 `ai-interview:token` 后重新登录 |
