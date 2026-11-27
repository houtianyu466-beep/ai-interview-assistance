<template>
  <div class="guide">
    <PageHeader title="面试指南" subtitle="给第一次面试的你：从流程到开口，一步一步来">
      <template #actions>
        <el-button type="primary" :icon="VideoPlay" @click="goPractice">去练一场</el-button>
      </template>
    </PageHeader>

    <section class="guide-hero card">
      <div class="guide-hero__main">
        <el-tag type="primary" effect="light" round size="small">零经验友好</el-tag>
        <h3>不用背答案，先把话说清楚</h3>
        <p class="muted">
          第一次面试紧张很正常。按「流程 → 准备 → 表达」的顺序看完，再去练一场，你会踏实很多。
        </p>
      </div>
      <div class="guide-hero__meta">
        <span><el-icon><Reading /></el-icon> 6 个部分</span>
        <span><el-icon><Timer /></el-icon> 约 8 分钟读完</span>
        <span><el-icon><Microphone /></el-icon> 可配合语音练习</span>
      </div>
    </section>

    <el-tabs v-model="activeTab" class="guide-tabs">
      <el-tab-pane label="面试流程" name="flow">
        <section class="card panel">
          <h3 class="panel__title">一场面试通常是这样走完的</h3>
          <p class="panel__desc">不同公司会有增减，但大方向差不多。心里有数，就不会被流程吓到。</p>
          <ol class="flow">
            <li v-for="(step, index) in flowSteps" :key="step.title" class="flow__item">
              <span class="flow__index">{{ index + 1 }}</span>
              <div class="flow__body">
                <h4>
                  {{ step.title }}
                  <el-tag size="small" effect="plain" type="info">{{ step.tag }}</el-tag>
                </h4>
                <p class="muted">{{ step.desc }}</p>
              </div>
            </li>
          </ol>
        </section>
      </el-tab-pane>

      <el-tab-pane label="准备清单" name="checklist">
        <section class="card panel">
          <div class="panel__head">
            <div>
              <h3 class="panel__title">第一次面试准备清单</h3>
              <p class="panel__desc">面试前一晚逐项打勾，第二天不用临时抱佛脚。</p>
            </div>
            <el-tag effect="light" type="success">已完成 {{ checkedCount }} / {{ checklist.length }}</el-tag>
          </div>

          <div class="checklist">
            <el-checkbox
              v-for="item in checklist"
              :key="item.key"
              v-model="item.checked"
              class="check-item"
            >
              <span class="check-item__body">
                <strong>{{ item.title }}</strong>
                <small>{{ item.desc }}</small>
              </span>
            </el-checkbox>
          </div>
        </section>
      </el-tab-pane>

      <el-tab-pane label="自我介绍" name="intro">
        <section class="card panel">
          <h3 class="panel__title">30 秒自我介绍：三段就够了</h3>
          <p class="panel__desc">面试官只想知道三件事：你是谁、你做过什么、你为什么适合这个岗位。</p>

          <el-row :gutter="16" class="intro-grid">
            <el-col v-for="(block, index) in introBlocks" :key="block.title" :xs="24" :md="8">
              <div class="intro-card">
                <span class="intro-card__index">{{ index + 1 }}</span>
                <h4>{{ block.title }}</h4>
                <p class="muted">{{ block.desc }}</p>
                <p class="intro-card__example">{{ block.example }}</p>
              </div>
            </el-col>
          </el-row>

          <div class="sample">
            <div class="sample__head">
              <el-icon><ChatLineRound /></el-icon>
              <strong>完整示例（约 30 秒）</strong>
            </div>
            <p class="sample__text">
              我是张伟，本科毕业，做后端开发 3 年，主要在做电商交易系统。<br />
              最近一年我负责订单链路的性能优化，把下单接口的 P95 从 800ms 降到 200ms，也扛住了大促 3 倍流量。<br />
              看到贵司这个岗位强调高并发和稳定性，这正是我过去一年在解决的问题，所以我想来试试。
            </p>
            <p class="sample__note muted">念一遍大约 30 秒。先写下来，再脱稿练到不用想就能说。</p>
          </div>
        </section>
      </el-tab-pane>

      <el-tab-pane label="STAR 法则" name="star">
        <section class="card panel">
          <h3 class="panel__title">讲项目故事，用 STAR 就够了</h3>
          <p class="panel__desc">面试官要听的是「你做了什么、带来了什么结果」，而不是项目说明书。</p>

          <div class="star-grid">
            <div v-for="item in starItems" :key="item.letter" class="star-card">
              <span class="star-card__letter">{{ item.letter }}</span>
              <div>
                <h4>{{ item.title }}</h4>
                <p class="muted">{{ item.desc }}</p>
              </div>
            </div>
          </div>

          <div class="sample sample--star">
            <div class="sample__head">
              <el-icon><DataLine /></el-icon>
              <strong>示例：后端接口性能优化</strong>
            </div>
            <dl class="star-example">
              <template v-for="row in starExample" :key="row.letter">
                <dt>
                  <span>{{ row.letter }}</span>
                  {{ row.title }}
                </dt>
                <dd>{{ row.desc }}</dd>
              </template>
            </dl>
            <p class="sample__note muted">
              结果尽量带数字；没有数字就说清影响范围。行动部分多说「我」做了什么，而不是「我们」。
            </p>
          </div>
        </section>
      </el-tab-pane>

      <el-tab-pane label="常见问题" name="questions">
        <section class="card panel">
          <h3 class="panel__title">常见问题分四类准备</h3>
          <p class="panel__desc">不用背标准答案，每类准备 2-3 个自己的例子，临场组合。</p>

          <el-row :gutter="16">
            <el-col v-for="group in questionGroups" :key="group.title" :xs="24" :md="12">
              <div class="question-card">
                <div class="question-card__head">
                  <span class="question-card__icon">
                    <el-icon><component :is="group.icon" /></el-icon>
                  </span>
                  <h4>{{ group.title }}</h4>
                </div>
                <div class="question-card__tags">
                  <el-tag
                    v-for="question in group.questions"
                    :key="question"
                    size="small"
                    effect="plain"
                    type="info"
                  >
                    {{ question }}
                  </el-tag>
                </div>
                <p class="question-card__tip">{{ group.tip }}</p>
              </div>
            </el-col>
          </el-row>
        </section>
      </el-tab-pane>

      <el-tab-pane label="语音作答" name="voice">
        <section class="card panel">
          <h3 class="panel__title">对着麦克风作答的 4 个建议</h3>
          <p class="panel__desc">
            真实面试是「说」出来的。练习时录音回听，比默读十遍更有效。
          </p>

          <el-row :gutter="16">
            <el-col v-for="tip in voiceTips" :key="tip.title" :xs="24" :md="12">
              <div class="voice-card">
                <span class="voice-card__icon">
                  <el-icon><component :is="tip.icon" /></el-icon>
                </span>
                <div>
                  <h4>{{ tip.title }}</h4>
                  <p class="muted">{{ tip.desc }}</p>
                </div>
              </div>
            </el-col>
          </el-row>

          <div class="voice-cta">
            <div>
              <h4>听一遍自己的回答</h4>
              <p class="muted">练完一场后回听录音，你会立刻发现语速、停顿和口头禅的问题。</p>
            </div>
            <el-button type="primary" @click="goPractice">去练一道</el-button>
          </div>
        </section>
      </el-tab-pane>
    </el-tabs>

    <section class="guide-cta card">
      <div>
        <h3>看完了，练一场试试</h3>
        <p class="muted">选一个方向，AI 会结合你的简历出题，练完还有逐题复盘。</p>
      </div>
      <el-button type="primary" size="large" @click="goPractice">开始练习</el-button>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref, type Component } from 'vue'
import { useRouter } from 'vue-router'
import {
  ChatDotRound,
  ChatLineRound,
  Collection,
  DataLine,
  Microphone,
  Promotion,
  Reading,
  Timer,
  VideoPlay,
} from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'

const router = useRouter()

const activeTab = ref('flow')

const flowSteps = [
  {
    title: '投递简历',
    tag: '第 1 步',
    desc: '一页纸写清你会什么、做过什么，尽量让关键词和 JD 对齐。',
  },
  {
    title: '笔试 / 在线测评',
    tag: '第 2 步',
    desc: '技术题或性格测评，先易后难，给主观题留足时间。',
  },
  {
    title: '一面（技术初面）',
    tag: '第 3 步',
    desc: '基础题加项目细节，重点看你能不能把一件事讲明白。',
  },
  {
    title: '二面（技术终面）',
    tag: '第 4 步',
    desc: '更深一点的原理、设计与权衡，会不断追问「为什么」。',
  },
  {
    title: 'HR 面',
    tag: '第 5 步',
    desc: '动机、稳定性、薪资与到岗时间，如实回答，别硬编。',
  },
  {
    title: '收到 offer',
    tag: '第 6 步',
    desc: '确认薪资、试用期和书面 offer，再考虑提离职。',
  },
]

const checklist = reactive([
  {
    key: 'jd',
    title: '了解岗位',
    desc: '把 JD 里的 3 个关键词抄下来，各准备一段对应经历',
    checked: false,
  },
  {
    key: 'intro',
    title: '准备自我介绍',
    desc: '30 秒版本，练到不用想就能说出口',
    checked: false,
  },
  {
    key: 'stories',
    title: '准备 3 个项目故事',
    desc: '每个都能按 STAR 讲 1-2 分钟，重点说清你的贡献',
    checked: false,
  },
  {
    key: 'ask',
    title: '准备反问问题',
    desc: '至少 2 个，关于团队、业务或成长，别问能百度到的',
    checked: false,
  },
  {
    key: 'device',
    title: '检查设备与环境',
    desc: '摄像头、麦克风、网络提前测试，背景干净、光线充足',
    checked: false,
  },
  {
    key: 'early',
    title: '提前 10 分钟上线',
    desc: '检查声音，深呼吸三次，把简历和笔记放在手边',
    checked: false,
  },
])

const checkedCount = computed(() => checklist.filter((item) => item.checked).length)

const introBlocks = [
  {
    title: '我是谁',
    desc: '一句话说清身份：学历、年限、方向。',
    example: '我是张伟，本科毕业，做后端开发 3 年。',
  },
  {
    title: '我做过什么',
    desc: '挑一个和岗位最相关的项目，讲清职责和结果。',
    example: '最近一年负责订单系统，把下单接口 P95 从 800ms 降到 200ms。',
  },
  {
    title: '我为什么合适',
    desc: '把经历和岗位要求对上，给出一个具体理由。',
    example: '这个岗位需要高并发经验，我正好做过大促流量治理。',
  },
]

const starItems = [
  { letter: 'S', title: '情境 Situation', desc: '什么背景、什么问题，一两句话说清。' },
  { letter: 'T', title: '任务 Task', desc: '你的目标是什么，最好有可衡量的标准。' },
  { letter: 'A', title: '行动 Action', desc: '你具体做了什么，为什么这么做，踩过什么坑。' },
  { letter: 'R', title: '结果 Result', desc: '带来了什么变化，尽量用数字说话。' },
]

const starExample = [
  {
    letter: 'S',
    title: '情境',
    desc: '大促前压测发现订单查询接口 P95 达到 1.2 秒，列表页经常转圈。',
  },
  {
    letter: 'T',
    title: '任务',
    desc: '两周内把 P95 压到 300ms 以内，并且不能改接口契约。',
  },
  {
    letter: 'A',
    title: '行动',
    desc: '用慢查询日志定位到缺索引和 N+1 查询；补联合索引、改批量查询，热点数据加 Redis 缓存并设置合理过期时间。',
  },
  {
    letter: 'R',
    title: '结果',
    desc: 'P95 降到 200ms，大促零故障；顺手补上了接口监控和压测脚本。',
  },
]

interface QuestionGroup {
  title: string
  icon: Component
  questions: string[]
  tip: string
}

const questionGroups: QuestionGroup[] = [
  {
    title: '技术基础',
    icon: Collection,
    questions: ['String 为什么不可变？', '线程池参数怎么设？', 'MySQL 索引失效的场景？'],
    tip: '先说结论，再讲原因，最后补一个你踩过的坑。',
  },
  {
    title: '项目深挖',
    icon: DataLine,
    questions: ['这个项目你负责哪部分？', '最难的点是什么？', '如果重做你会怎么改？'],
    tip: '用 STAR 讲，重点准备「为什么这么做」和「有没有别的方案」。',
  },
  {
    title: '行为面',
    icon: ChatDotRound,
    questions: ['讲一次失败的经历', '和同事有分歧怎么处理？', '为什么离开上一家？'],
    tip: '真实、具体、不甩锅；失败题重点讲复盘和改进。',
  },
  {
    title: '反问环节',
    icon: Promotion,
    questions: ['团队最近在解决什么问题？', '新人前三个月的目标？', '技术栈和发布流程？'],
    tip: '问团队、成长和业务；薪资和加班可以留到 HR 面再谈。',
  },
]

interface VoiceTip {
  title: string
  desc: string
  icon: Component
}

const voiceTips: VoiceTip[] = [
  {
    title: '语速',
    desc: '每分钟 180-220 字，比平时慢一点。紧张时更容易越说越快。',
    icon: Timer,
  },
  {
    title: '停顿',
    desc: '结论后停 1 秒，给面试官反应时间；不会就先说「我想一下」。',
    icon: Microphone,
  },
  {
    title: '先说结论',
    desc: '用「结论 → 原因 → 例子」回答，别从三年前的背景讲起。',
    icon: ChatLineRound,
  },
  {
    title: '控制口头禅',
    desc: '把「然后、那个、就是说」换成短暂停顿，录音回听最有效。',
    icon: Reading,
  },
]

function goPractice(): void {
  router.push({ name: 'practice-create' })
}
</script>

<style scoped lang="scss">
.guide-hero {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--ai-space-6);
  margin-bottom: var(--ai-space-4);
  padding: 24px 28px;
  background:
    radial-gradient(circle at 92% 10%, rgba(47, 107, 255, 0.12), transparent 45%),
    #fff;

  &__main {
    max-width: 620px;
  }

  &__main h3 {
    margin-top: var(--ai-space-3);
    font-size: 20px;
    font-weight: 600;
  }

  &__main p {
    margin-top: var(--ai-space-2);
    font-size: 13px;
    line-height: 1.7;
  }

  &__meta {
    display: flex;
    flex-direction: column;
    gap: var(--ai-space-3);
    flex-shrink: 0;
    font-size: 13px;
    color: var(--ai-text-secondary);

    span {
      display: flex;
      align-items: center;
      gap: 6px;
    }
  }
}

.guide-tabs {
  :deep(.el-tabs__header) {
    margin-bottom: var(--ai-space-4);
  }

  :deep(.el-tabs__item) {
    font-size: 14px;
  }
}

.panel {
  &__head {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: var(--ai-space-3);
  }

  &__title {
    font-size: 17px;
    font-weight: 600;
  }

  &__desc {
    margin-top: var(--ai-space-2);
    font-size: 13px;
    line-height: 1.7;
    color: var(--ai-text-secondary);
  }
}

/* ---------- 流程 ---------- */
.flow {
  margin: var(--ai-space-4) 0 0;
  padding: 0;
  list-style: none;

  &__item {
    position: relative;
    display: flex;
    gap: var(--ai-space-3);
    padding-bottom: var(--ai-space-4);

    &:not(:last-child)::before {
      content: '';
      position: absolute;
      left: 14px;
      top: 30px;
      bottom: 2px;
      width: 2px;
      background: var(--ai-border);
    }

    &:last-child {
      padding-bottom: 0;
    }
  }

  &__index {
    position: relative;
    z-index: 1;
    width: 30px;
    height: 30px;
    border-radius: 50%;
    background: var(--ai-primary-soft);
    color: var(--ai-primary);
    font-size: 13px;
    font-weight: 600;
    display: flex;
    align-items: center;
    justify-content: center;
    flex-shrink: 0;
  }

  &__body h4 {
    display: flex;
    align-items: center;
    gap: var(--ai-space-2);
    font-size: 14px;
    font-weight: 600;
  }

  &__body p {
    margin-top: 4px;
    font-size: 13px;
    line-height: 1.6;
  }
}

/* ---------- 清单 ---------- */
.checklist {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--ai-space-2);
  margin-top: var(--ai-space-4);
}

.check-item {
  height: auto;
  margin-right: 0;
  padding: 12px 14px;
  border: 1px solid var(--ai-border);
  border-radius: var(--ai-radius-md);
  white-space: normal;
  align-items: flex-start;

  :deep(.el-checkbox__label) {
    display: block;
    line-height: 1.5;
    white-space: normal;
  }

  :deep(.el-checkbox__input) {
    margin-top: 2px;
  }

  &__body {
    display: flex;
    flex-direction: column;

    strong {
      font-size: 14px;
      font-weight: 500;
      color: var(--ai-text);
    }

    small {
      margin-top: 2px;
      font-size: 12px;
      color: var(--ai-text-secondary);
      white-space: normal;
    }
  }
}

.check-item.is-checked {
  border-color: var(--ai-primary);
  background: var(--ai-primary-soft);
}

/* ---------- 自我介绍 ---------- */
.intro-grid {
  margin-top: var(--ai-space-4);

  :deep(.el-col) {
    margin-bottom: var(--ai-space-3);
  }
}

.intro-card {
  height: 100%;
  padding: 18px;
  background: var(--ai-bg);
  border-radius: var(--ai-radius-md);

  &__index {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 24px;
    height: 24px;
    border-radius: 50%;
    background: #fff;
    color: var(--ai-primary);
    font-size: 12px;
    font-weight: 600;
  }

  h4 {
    margin-top: var(--ai-space-2);
    font-size: 15px;
    font-weight: 600;
  }

  p {
    margin-top: var(--ai-space-1);
    font-size: 13px;
    line-height: 1.6;
  }

  &__example {
    margin-top: var(--ai-space-2) !important;
    padding-top: var(--ai-space-2);
    border-top: 1px dashed var(--ai-border);
    color: var(--ai-text);
  }
}

.sample {
  margin-top: var(--ai-space-2);
  padding: 18px 20px;
  border: 1px dashed var(--ai-border);
  border-left: 3px solid var(--ai-primary);
  border-radius: var(--ai-radius-md);
  background: #fbfcff;

  &__head {
    display: flex;
    align-items: center;
    gap: var(--ai-space-2);
    font-size: 14px;
    color: var(--ai-primary);
  }

  &__text {
    margin-top: var(--ai-space-3);
    font-size: 14px;
    line-height: 1.9;
    color: var(--ai-text);
  }

  &__note {
    margin-top: var(--ai-space-3);
    font-size: 12px;
    line-height: 1.6;
  }
}

/* ---------- STAR ---------- */
.star-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--ai-space-3);
  margin-top: var(--ai-space-4);
}

.star-card {
  display: flex;
  gap: var(--ai-space-3);
  padding: 16px;
  border: 1px solid var(--ai-border);
  border-radius: var(--ai-radius-md);

  &__letter {
    width: 30px;
    height: 30px;
    border-radius: 8px;
    background: var(--ai-primary);
    color: #fff;
    font-size: 15px;
    font-weight: 700;
    display: flex;
    align-items: center;
    justify-content: center;
    flex-shrink: 0;
  }

  h4 {
    font-size: 14px;
    font-weight: 600;
  }

  p {
    margin-top: 4px;
    font-size: 12px;
    line-height: 1.6;
  }
}

.star-example {
  margin: var(--ai-space-3) 0 0;

  dt {
    display: flex;
    align-items: center;
    gap: var(--ai-space-2);
    margin-top: var(--ai-space-3);
    font-size: 13px;
    font-weight: 600;

    span {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      width: 20px;
      height: 20px;
      border-radius: 6px;
      background: var(--ai-primary-soft);
      color: var(--ai-primary);
      font-size: 11px;
    }
  }

  dd {
    margin: 4px 0 0 28px;
    font-size: 13px;
    line-height: 1.7;
    color: var(--ai-text-secondary);
  }
}

/* ---------- 常见问题 ---------- */
.question-card {
  height: calc(100% - var(--ai-space-4));
  margin-top: var(--ai-space-4);
  padding: 18px;
  border: 1px solid var(--ai-border);
  border-radius: var(--ai-radius-md);

  &__head {
    display: flex;
    align-items: center;
    gap: var(--ai-space-2);
  }

  &__icon {
    width: 32px;
    height: 32px;
    border-radius: 9px;
    background: var(--ai-primary-soft);
    color: var(--ai-primary);
    display: flex;
    align-items: center;
    justify-content: center;
  }

  &__head h4 {
    font-size: 15px;
    font-weight: 600;
  }

  &__tags {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
    margin-top: var(--ai-space-3);
  }

  &__tip {
    margin-top: var(--ai-space-3);
    padding-top: var(--ai-space-2);
    border-top: 1px dashed var(--ai-border);
    font-size: 12px;
    line-height: 1.6;
    color: var(--ai-text-secondary);
  }
}

/* ---------- 语音 ---------- */
.voice-card {
  display: flex;
  gap: var(--ai-space-3);
  height: calc(100% - var(--ai-space-4));
  margin-top: var(--ai-space-4);
  padding: 16px 18px;
  background: var(--ai-bg);
  border-radius: var(--ai-radius-md);

  &__icon {
    width: 34px;
    height: 34px;
    border-radius: 10px;
    background: #fff;
    color: var(--ai-primary);
    display: flex;
    align-items: center;
    justify-content: center;
    flex-shrink: 0;
  }

  h4 {
    font-size: 14px;
    font-weight: 600;
  }

  p {
    margin-top: 4px;
    font-size: 13px;
    line-height: 1.6;
  }
}

.voice-cta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--ai-space-4);
  margin-top: var(--ai-space-4);
  padding: 18px 20px;
  border-radius: var(--ai-radius-md);
  background: var(--ai-primary-soft);

  h4 {
    font-size: 15px;
    font-weight: 600;
  }

  p {
    margin-top: 4px;
    font-size: 13px;
  }
}

/* ---------- 底部 CTA ---------- */
.guide-cta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--ai-space-4);
  margin-top: var(--ai-space-4);

  h3 {
    font-size: 17px;
    font-weight: 600;
  }

  p {
    margin-top: 4px;
    font-size: 13px;
  }
}

@media (max-width: 1100px) {
  .star-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 768px) {
  .guide-hero {
    flex-direction: column;
    align-items: flex-start;
    gap: var(--ai-space-4);
    padding: 20px;
  }

  .guide-hero__meta {
    flex-direction: row;
    flex-wrap: wrap;
    gap: var(--ai-space-3);
  }

  .checklist {
    grid-template-columns: 1fr;
  }

  .star-grid {
    grid-template-columns: 1fr;
  }

  .sample {
    padding: 16px;
  }

  .voice-cta,
  .guide-cta {
    flex-direction: column;
    align-items: stretch;
  }

  .voice-cta :deep(.el-button),
  .guide-cta :deep(.el-button) {
    width: 100%;
    margin-left: 0;
  }
}
</style>
