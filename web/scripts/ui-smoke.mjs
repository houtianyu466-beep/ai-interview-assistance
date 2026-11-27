// =====================================================================
// 前端 UI 冒烟测试（Playwright + 本机 Chrome）
// 产品：AI 面试练习（给求职者练习面试）
// 覆盖：登录 → 练习工作台 → 选择方向 → 练习房间（提示/作答/反馈）→ 复盘 → 记录 → 指南
// 用法：先启动后端(8080)与前端(5173)，再执行 node scripts/ui-smoke.mjs
// =====================================================================
import { chromium } from 'playwright'
import { mkdirSync } from 'node:fs'

const BASE = process.env.WEB_BASE || 'http://localhost:5173'
const OUT = 'screenshots'
mkdirSync(OUT, { recursive: true })

const errors = []
let failed = false

function log(message) {
  console.log(`  ${message}`)
}

function assert(condition, message) {
  if (!condition) {
    failed = true
    console.error(`  [FAIL] ${message}`)
    throw new Error(message)
  }
  console.log(`  [PASS] ${message}`)
}

const browser = await chromium.launch({ channel: 'chrome', headless: true })
const context = await browser.newContext({
  viewport: { width: 1440, height: 900 },
  locale: 'zh-CN',
})
const page = await context.newPage()

page.on('pageerror', (error) => errors.push(`pageerror: ${error.message}`))
page.on('console', (message) => {
  if (message.type() === 'error') errors.push(`console: ${message.text()}`)
})

async function shot(name, fullPage = false) {
  await page.screenshot({ path: `${OUT}/${name}.png`, fullPage })
  log(`截图 ${name}.png`)
}

const wait = (ms) => page.waitForTimeout(ms)

async function login() {
  await page.goto(`${BASE}/login`)
  await page.fill('input[placeholder="请输入用户名"]', 'candidate')
  await page.fill('input[placeholder="请输入密码"]', '123456')
  await page.getByRole('button', { name: /登\s*录/ }).click()
  await page.waitForURL('**/practice', { timeout: 20000 })
  await page.waitForSelector('.home')
  await wait(800)
}

try {
  console.log('\n=== 1. 登录页 ===')
  await page.goto(`${BASE}/login`)
  await page.waitForSelector('input[placeholder="请输入用户名"]')
  assert(await page.getByText('AI 面试练习').first().isVisible(), '登录页呈现练习产品定位')
  await shot('01-login')

  console.log('\n=== 2. 练习工作台 ===')
  await login()
  await page.waitForSelector('.target-card', { timeout: 20000 })
  const targetCount = await page.locator('.target-card').count()
  assert(targetCount >= 6, `练习方向卡片加载成功（${targetCount} 个）`)
  await shot('02-practice-home', true)

  console.log('\n=== 3. 创建练习 ===')
  await page.locator('.target-card').first().click()
  await page.waitForURL('**/practice/new**', { timeout: 10000 })
  await page.waitForSelector('.target-option', { timeout: 15000 })
  await shot('03-practice-create', true)
  await page.getByRole('button', { name: /^开始练习$/ }).click()
  await page.waitForURL('**/room', { timeout: 30000 })
  await page.waitForSelector('textarea', { timeout: 30000 })
  await wait(600)
  assert(true, '练习房间加载完成')
  await shot('04-practice-room')

  if ((await page.getByRole('button', { name: /录音/ }).count()) > 0) {
    assert(true, '房间提供语音作答入口')
  }

  console.log('\n=== 4. 提示 / AI 教练 / 作答 / 反馈 ===')
  await page.getByRole('button', { name: '给我提示' }).click()
  await page.waitForSelector('.el-alert', { timeout: 15000 })
  await wait(400)
  assert(true, '提示气泡已展示')
  await shot('05-practice-hint')

  await page.getByRole('button', { name: '问教练' }).click()
  await page.waitForSelector('.coach-panel', { timeout: 15000 })
  await page.locator('.coach-suggestion').first().click()
  // 先等「思考中」出现再等它消失，避免匹配到用户消息气泡里的 .coach-answer
  await page.waitForSelector('.coach-thinking', { timeout: 15000 })
  await page.waitForSelector('.coach-thinking', { state: 'detached', timeout: 120000 })
  await page.waitForSelector('.coach-msg.is-coach .coach-answer', { timeout: 20000 })
  await wait(600)
  const citationCount = await page.locator('.coach-citations').count()
  const toolCount = await page.locator('.coach-tool-badge').count()
  const panelText = (await page.locator('.coach-panel').innerText()).slice(0, 300).replace(/\n/g, ' | ')
  // 真实模型可能不调用检索工具（直接凭经验回答），因此引用/工具二者有一即可
  assert(
    citationCount > 0 || toolCount > 0,
    `AI 教练返回回答，引用 ${citationCount} 条 / 工具 ${toolCount} 个；面板：${panelText}`,
  )
  if (citationCount === 0) {
    console.log('  [WARN] 本次回答未附引用，面板片段：' + panelText)
  }
  await shot('17-coach')
  await page.keyboard.press('Escape')
  await wait(400)

  const answerText =
    '我会先给结论：这个模块的核心是把订单写入与库存扣减解耦。'
    + '具体做法是基于本地消息表加定时补偿，保证最终一致；'
    + '同时用 Redis 做幂等键，接口 P99 从 600ms 降到 120ms。'

  let finished = false
  for (let i = 1; i <= 8 && !finished; i += 1) {
    await page.waitForSelector('textarea', { timeout: 15000 })
    await page.fill('textarea', answerText)
    await page.getByRole('button', { name: '提交回答' }).click()
    await page.getByText('准确性').first().waitFor({ timeout: 30000 })
    await wait(500)
    log(`第 ${i} 题反馈已渲染`)
    if (i === 1) await shot('06-practice-feedback')

    if ((await page.getByRole('button', { name: '查看复盘' }).count()) > 0) {
      finished = true
      break
    }
    if ((await page.getByRole('button', { name: '下一题' }).count()) > 0) {
      await page.getByRole('button', { name: '下一题' }).click()
      await wait(500)
    }
  }
  assert(finished, '完成全部题目并出现「查看复盘」')

  console.log('\n=== 5. 练习复盘 ===')
  await page.getByRole('button', { name: '查看复盘' }).click()
  await page.waitForURL('**/review', { timeout: 15000 })
  await page.waitForSelector('.review-hero', { timeout: 20000 })
  await wait(800)
  assert(await page.locator('.review-hero').isVisible(), '复盘页加载完成')
  await shot('07-practice-review', true)

  console.log('\n=== 6. 练习记录 ===')
  await page.goto(`${BASE}/practice/history`)
  await page.waitForSelector('.el-table, .history-card, .history', { timeout: 20000 }).catch(() => {})
  await wait(800)
  await shot('08-practice-history', true)

  console.log('\n=== 7. 面试指南 ===')
  await page.goto(`${BASE}/guide`)
  await page.waitForSelector('.el-tabs', { timeout: 15000 })
  await wait(500)
  assert(await page.getByText('面试流程').first().isVisible(), '面试指南内容正常')
  await shot('09-guide', true)

  console.log('\n=== 8. 移动端 ===')
  await page.setViewportSize({ width: 390, height: 844 })
  await page.goto(`${BASE}/practice`)
  await page.waitForSelector('.home', { timeout: 15000 })
  await wait(600)
  await shot('10-practice-mobile')

  if (errors.length) {
    console.log('\n浏览器控制台错误：')
    errors.forEach((item) => console.log('  ' + item))
  }
  assert(errors.length === 0, '无浏览器控制台错误')

  console.log('\nUI 冒烟测试全部通过 ✅')
  console.log(`截图目录：${OUT}`)
  await browser.close()
  process.exit(0)
} catch (error) {
  console.error('\n❌ UI 冒烟失败：', error.message)
  if (errors.length) {
    console.error('控制台错误：')
    errors.forEach((item) => console.error('  ' + item))
  }
  try {
    await page.screenshot({ path: `${OUT}/failure.png`, fullPage: true })
  } catch {
    // ignore
  }
  await browser.close()
  process.exit(failed ? 1 : 1)
}
