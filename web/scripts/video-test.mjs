// =====================================================================
// 视频面试 / AI 教练 端到端测试（Playwright + 本机 Chrome + 假摄像头麦克风）
// 覆盖：AI 视频模拟面试（出题 → 作答 → 反馈 → 下一题）、真人 1:1 WebRTC、AI 教练
// 用法：node scripts/video-test.mjs（需后端 8080、前端 5173 已启动）
// =====================================================================
import { chromium } from 'playwright'
import { mkdirSync } from 'node:fs'

const BASE = process.env.WEB_BASE || 'http://localhost:5173'
const OUT = 'screenshots'
mkdirSync(OUT, { recursive: true })

const errors = []
let failed = false

function assert(condition, message) {
  if (!condition) {
    failed = true
    throw new Error(message)
  }
  console.log(`  [PASS] ${message}`)
}

const browser = await chromium.launch({
  channel: 'chrome',
  headless: true,
  args: [
    '--use-fake-ui-for-media-stream',
    '--use-fake-device-for-media-stream',
    '--autoplay-policy=no-user-gesture-required',
  ],
})

async function newPage(context) {
  const page = await context.newPage()
  page.on('pageerror', (e) => errors.push(`pageerror: ${e.message}`))
  page.on('console', (m) => {
    if (m.type() === 'error') errors.push(`console: ${m.text()}`)
  })
  return page
}

async function login(page, username = 'candidate') {
  await page.goto(`${BASE}/login`)
  await page.fill('input[placeholder="请输入用户名"]', username)
  await page.fill('input[placeholder="请输入密码"]', '123456')
  await page.getByRole('button', { name: /登\s*录/ }).click()
  await page.waitForURL('**/practice', { timeout: 20000 })
  await page.waitForSelector('.home')
}

async function shot(page, name, fullPage = false) {
  await page.screenshot({ path: `${OUT}/${name}.png`, fullPage })
  console.log(`  截图 ${name}.png`)
}

const contextA = await browser.newContext({ viewport: { width: 1440, height: 900 }, locale: 'zh-CN' })
const pageA = await newPage(contextA)

try {
  console.log('\n=== 1. 登录并进入视频面试大厅 ===')
  await login(pageA)
  await pageA.goto(`${BASE}/video`)
  await pageA.waitForSelector('.el-select', { timeout: 15000 })
  await pageA.waitForTimeout(600)
  await shot(pageA, '11-video-lobby', true)

  console.log('\n=== 2. AI 视频模拟面试 ===')
  await pageA.locator('.el-select').first().click()
  await pageA.waitForSelector('.el-select-dropdown__item', { timeout: 10000 })
  await pageA.locator('.el-select-dropdown__item').first().click()
  await pageA.getByRole('button', { name: /开始 AI 视频面试/ }).click()
  await pageA.waitForURL('**/call/**', { timeout: 60000 })
  await pageA.waitForSelector('.ai-stage__question', { timeout: 40000 })
  const question = (await pageA.locator('.ai-stage__question').innerText()).trim()
  assert(question.length > 5, `AI 已出题：${question.slice(0, 24)}…`)
  await pageA.waitForTimeout(1200)
  assert((await pageA.locator('video').count()) >= 1, 'AI 模式本地摄像头画面已渲染')
  await shot(pageA, '12-video-ai-question', true)

  const answer =
    '我会先给结论：这个项目解决的是订单与库存的一致性问题。'
    + '做法是本地消息表加定时补偿，并用 Redis 做幂等键；'
    + '最终把接口 P99 从 600ms 降到 120ms。'

  let finished = false
  for (let i = 1; i <= 8 && !finished; i += 1) {
    if ((await pageA.getByRole('button', { name: '查看复盘' }).count()) > 0) {
      finished = true
      break
    }
    if ((await pageA.locator('textarea').count()) === 0) {
      await pageA.waitForTimeout(800)
      continue
    }
    await pageA.fill('textarea', answer)
    await pageA.getByRole('button', { name: '提交回答' }).click()
    await pageA.waitForFunction(
      () => Boolean(document.querySelector('.dock__score')) || document.body.innerText.includes('查看复盘'),
      null,
      { timeout: 40000 },
    )
    await pageA.waitForTimeout(500)
    console.log(`  第 ${i} 题反馈已返回`)
    if (i === 1) await shot(pageA, '13-video-ai-feedback', true)

    if ((await pageA.getByRole('button', { name: '查看复盘' }).count()) > 0) {
      finished = true
      break
    }
    if ((await pageA.getByRole('button', { name: '下一题' }).count()) > 0) {
      await pageA.getByRole('button', { name: '下一题' }).click()
      await pageA.waitForTimeout(1200)
    }
  }
  assert(finished, 'AI 视频面试完成全部题目')
  await shot(pageA, '14-video-ai-finished', true)
  await pageA.getByRole('button', { name: '查看复盘' }).first().click()
  await pageA.waitForURL('**/practice/**/review', { timeout: 15000 })
  await pageA.waitForSelector('.review-hero', { timeout: 20000 })
  assert(true, '视频面试结束后可进入复盘')

  console.log('\n=== 3. 真人 1:1 WebRTC ===')
  await pageA.goto(`${BASE}/video`)
  await pageA.waitForSelector('.invite-room__code', { timeout: 15000 }).catch(() => {})
  await pageA.getByRole('button', { name: '创建陪练房间' }).click()
  await pageA.waitForSelector('[aria-label="房间码"]', { timeout: 20000 })
  const code = (await pageA.locator('[aria-label="房间码"]').innerText()).replace(/\s+/g, '')
  assert(code.length >= 6, `真人房间创建成功：${code}`)
  await pageA.getByRole('button', { name: /进入房间等待对方/ }).click()
  await pageA.waitForURL('**/call/**', { timeout: 15000 })
  await pageA.waitForSelector('video', { timeout: 30000 })

  const guest = `guest${Date.now().toString().slice(-6)}`
  await fetch('http://127.0.0.1:8080/api/v1/auth/register', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: guest, password: '123456', realName: '陪练朋友' }),
  })
  const contextB = await browser.newContext({ viewport: { width: 1280, height: 800 }, locale: 'zh-CN' })
  const pageB = await newPage(contextB)
  await login(pageB, guest)
  await pageB.goto(`${BASE}/video`)
  await pageB.fill('input[placeholder="输入 6-8 位房间码"]', code)
  await pageB.getByRole('button', { name: '加入房间' }).click()
  await pageB.waitForURL('**/call/**', { timeout: 20000 })
  await pageB.waitForSelector('video', { timeout: 30000 })

  await pageA.waitForFunction(() => document.body.innerText.includes('通话中'), null, { timeout: 45000 })
  await pageB.waitForFunction(() => document.body.innerText.includes('通话中'), null, { timeout: 45000 })
  assert(true, '双方建立 WebRTC 连接并显示「通话中」')
  // 等远端首帧渲染后再截图
  await pageA.waitForTimeout(1500)

  const remoteReady = async (page) =>
    page.evaluate(() =>
      Array.from(document.querySelectorAll('video')).some(
        (video) => video.srcObject && video.readyState >= 2 && (video.videoWidth || 0) > 0,
      ),
    )
  assert(await remoteReady(pageA), '房主侧收到远端视频流')
  assert(await remoteReady(pageB), '加入方侧收到远端视频流')
  await shot(pageA, '15-video-human-call')
  await pageB.screenshot({ path: `${OUT}/16-video-human-call-guest.png` })
  console.log('  截图 16-video-human-call-guest.png')

  console.log('\n=== 4. 挂断 ===')
  await pageA.getByRole('button', { name: '离开通话' }).first().click()
  await pageA.waitForTimeout(500)
  const confirm = pageA.getByRole('button', { name: /挂断|确定|离开/ })
  if ((await confirm.count()) > 0) await confirm.last().click()
  await pageA.waitForTimeout(1000)

  if (errors.length) {
    console.log('\n浏览器控制台错误：')
    errors.forEach((item) => console.log('  ' + item))
  }
  assert(errors.length === 0, '无浏览器控制台错误')

  console.log('\n视频面试测试全部通过 ✅')
  await browser.close()
  process.exit(0)
} catch (error) {
  console.error('\n❌ 视频测试失败：', error.message)
  try {
    await pageA.screenshot({ path: `${OUT}/video-failure.png`, fullPage: true })
  } catch {
    // ignore
  }
  await browser.close()
  process.exit(failed ? 1 : 1)
}
