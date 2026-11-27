// =====================================================================
// AI 面试平台 WebSocket 实时通道冒烟测试（Node.js 22+，使用内置 WebSocket 与 fetch）
// 用法：先启动应用，再执行  node scripts/ws-test.mjs
// =====================================================================
const BASE = process.env.AI_BASE_URL || 'http://127.0.0.1:8080';

async function api(path, { method = 'GET', body, token } = {}) {
  const res = await fetch(`${BASE}${path}`, {
    method,
    headers: {
      'Content-Type': 'application/json; charset=utf-8',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: body ? JSON.stringify(body) : undefined,
  });
  const json = await res.json();
  if (json.code !== 0) {
    throw new Error(`${path} 失败: ${res.status} ${json.message}`);
  }
  return json.data;
}

function assert(cond, message) {
  if (!cond) throw new Error(`断言失败：${message}`);
  console.log(`  [PASS] ${message}`);
}

const ANSWER = 'HashMap 底层是数组加链表加红黑树，JDK8 中链表长度达到 8 且数组长度达到 64 时转为红黑树，'
  + '查询复杂度从 O(n) 降到 O(log n)。我在项目里会预设初始容量避免频繁扩容，并注意并发场景改用 ConcurrentHashMap。';

async function main() {
  console.log('\n=== 1. 准备数据 ===');
  const login = await api('/api/v1/auth/login', { method: 'POST', body: { username: 'candidate', password: '123456' } });
  const token = login.accessToken;
  const jobs = await api('/api/v1/jobs?page=1&size=20', { token });
  const job = jobs.records.find((j) => j.status === 'OPEN' && j.title.includes('Java')) || jobs.records[0];
  const resumes = await api('/api/v1/resumes/my', { token });
  const resume = resumes.find((r) => r.status === 'PARSED');
  assert(!!resume, `找到已解析简历：${resume.fileName}`);

  const interview = await api('/api/v1/interviews', {
    method: 'POST', token,
    body: { positionId: job.id, resumeId: resume.id, mode: 'TECH', totalQuestions: 3 },
  });
  assert(interview.status === 'CREATED', `创建面试成功，id=${interview.id}`);

  console.log('\n=== 2. 建立 WebSocket 连接 ===');
  const url = `ws://127.0.0.1:8080/ws/interview?token=${encodeURIComponent(token)}&interviewId=${interview.id}`;
  const ws = new WebSocket(url);
  const inbox = [];
  const waiters = [];
  ws.addEventListener('message', (event) => {
    const msg = JSON.parse(event.data);
    console.log('  <-', msg.type);
    const waiter = waiters.shift();
    if (waiter) waiter(msg); else inbox.push(msg);
  });
  const nextMessage = (timeoutMs = 60000) => new Promise((resolve, reject) => {
    if (inbox.length) return resolve(inbox.shift());
    const timer = setTimeout(() => reject(new Error('等待 WebSocket 消息超时')), timeoutMs);
    waiters.push((msg) => { clearTimeout(timer); resolve(msg); });
  });
  await new Promise((resolve, reject) => {
    ws.addEventListener('open', resolve);
    ws.addEventListener('error', () => reject(new Error('WebSocket 连接失败')));
  });
  assert(ws.readyState === WebSocket.OPEN, 'WebSocket 已连接');

  const connected = await nextMessage();
  assert(connected.type === 'CONNECTED', '收到 CONNECTED');

  console.log('\n=== 3. PING / SWITCH ===');
  ws.send(JSON.stringify({ type: 'PING' }));
  assert((await nextMessage()).type === 'PONG', 'PING 返回 PONG');
  ws.send(JSON.stringify({ type: 'SWITCH' }));
  assert((await nextMessage()).type === 'SWITCH_ACK', 'SWITCH 返回 SWITCH_ACK');

  console.log('\n=== 4. START 并逐题作答 ===');
  ws.send(JSON.stringify({ type: 'START' }));
  const first = await nextMessage();
  assert(first.type === 'QUESTION', 'START 下发第一题');
  let question = first.data;
  assert(question.seq === 1, `题号从 1 开始，题目：${question.content.slice(0, 24)}...`);

  let finished = false;
  let reportId = null;
  for (let i = 0; i < 3 && !finished; i++) {
    console.log('  发送 ANSWER questionId=' + question.questionId);
    ws.send(JSON.stringify({
      type: 'ANSWER',
      content: ANSWER,
      thinkingSeconds: 30,
      data: { questionId: question.questionId },
    }));
    const msg = await nextMessage();
    if (msg.type === 'ERROR') { throw new Error('服务端返回 ERROR: ' + msg.content); }
    if (msg.type === 'FINISHED') {
      finished = true;
      reportId = msg.data?.reportId;
      console.log(`  面试结束，reportId=${reportId}`);
    } else {
      assert(msg.type === 'SCORE', `第 ${i + 1} 题评分：${msg.data?.score}`);
      question = msg.data?.nextQuestion;
      assert(!!question, '收到下一题');
    }
  }
  assert(finished, '3 题作答完成并收到 FINISHED');
  ws.close();

  console.log('\n=== 5. 校验报告 ===');
  const report = await api(`/api/v1/interviews/${interview.id}/report`, { token });
  assert(report.overallScore >= 0, `报告总分：${report.overallScore}`);
  assert(report.questions.length === 3, '报告包含 3 道题');
  console.log(`  维度分：${JSON.stringify(report.dimensionScores)}`);
  console.log(`  建议：${report.suggestion}`);

  console.log('\nWebSocket 冒烟测试全部通过 ✅');
  process.exit(0);
}

main().catch((err) => {
  console.error('\n❌ 测试失败：', err.message);
  process.exit(1);
});
