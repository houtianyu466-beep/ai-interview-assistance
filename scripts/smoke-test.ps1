# =====================================================================
# AI 面试平台 端到端冒烟测试
# 用法：先启动应用（mvn spring-boot:run），再执行
#   pwsh -File scripts/smoke-test.ps1
# 覆盖：登录 → 岗位 → 简历 → 创建面试 → 开始 → 逐题作答 → 报告 → 管理端统计
# =====================================================================
$ErrorActionPreference = 'Stop'
$base = if ($env:AI_BASE_URL) { $env:AI_BASE_URL } else { 'http://127.0.0.1:8080/api/v1' }

function Invoke-Api {
    param(
        [string]$Method,
        [string]$Path,
        [object]$Body,
        [string]$Token
    )
    $headers = @{}
    if ($Token) { $headers['Authorization'] = "Bearer $Token" }
    $params = @{
        Uri         = "$base$Path"
        Method      = $Method
        Headers     = $headers
        TimeoutSec  = 120
    }
    if ($null -ne $Body) {
        $json = if ($Body -is [string]) { $Body } else { $Body | ConvertTo-Json -Depth 10 -Compress }
        $params['ContentType'] = 'application/json; charset=utf-8'
        $params['Body'] = [System.Text.Encoding]::UTF8.GetBytes($json)
    }
    return Invoke-RestMethod @params
}

function Assert-True {
    param([bool]$Condition, [string]$Message)
    if (-not $Condition) { throw "断言失败：$Message" }
    Write-Host "  [PASS] $Message" -ForegroundColor Green
}

Write-Host "`n=== 1. 候选人登录 ===" -ForegroundColor Cyan
$login = Invoke-Api -Method POST -Path '/auth/login' -Body @{ username = 'candidate'; password = '123456' }
Assert-True ($login.code -eq 0) '登录返回 code=0'
$token = $login.data.accessToken
Assert-True ([bool]$token) '拿到 accessToken'
Write-Host ("  当前用户：" + $login.data.user.realName + " / " + $login.data.user.role)

Write-Host "`n=== 2. 查询岗位与简历 ===" -ForegroundColor Cyan
$jobs = Invoke-Api -Method GET -Path '/jobs?page=1&size=10' -Token $token
Assert-True ($jobs.data.total -ge 1) '岗位列表非空'
$job = $jobs.data.records | Where-Object { $_.status -eq 'OPEN' -and $_.title -match 'Java' } | Select-Object -First 1
if (-not $job) { $job = $jobs.data.records | Where-Object { $_.status -eq 'OPEN' } | Select-Object -First 1 }
Write-Host ("  使用岗位：" + $job.title + "（" + $job.id + "）")

$resumes = Invoke-Api -Method GET -Path '/resumes/my' -Token $token
Assert-True ($resumes.data.Count -ge 1) '简历列表非空'
$resume = $resumes.data | Where-Object { $_.status -eq 'PARSED' } | Select-Object -First 1
Write-Host ("  使用简历：" + $resume.fileName + "（" + $resume.id + "）")

Write-Host "`n=== 3. 创建面试并生成题目 ===" -ForegroundColor Cyan
$interview = Invoke-Api -Method POST -Path '/interviews' -Token $token -Body @{
    positionId     = $job.id
    resumeId       = $resume.id
    mode           = 'TECH'
    totalQuestions = 5
}
Assert-True ($interview.code -eq 0) '创建面试成功'
Assert-True ($interview.data.status -eq 'CREATED') '状态为 CREATED'
$interviewId = $interview.data.id
Write-Host ("  面试 id：" + $interviewId + "，题数：" + $interview.data.totalQuestions)

Write-Host "`n=== 4. 开始面试并逐题作答 ===" -ForegroundColor Cyan
$current = Invoke-Api -Method POST -Path "/interviews/$interviewId/start" -Token $token
Assert-True ($current.data.seq -eq 1) '第一题 seq=1'
$question = $current.data
Write-Host ("  第1题：" + $question.content)

$answerText = @'
我会先说明数据结构：HashMap 底层是数组 + 链表 + 红黑树。
JDK8 中当链表长度达到 8 且数组长度达到 64 时转为红黑树，把查询复杂度从 O(n) 降到 O(log n)。
扩容时容量翻倍，并通过 hash & (n-1) 重新分布元素。实际项目中我会注意初始容量设置，避免频繁扩容。
'@

$finished = $false
$reportId = $null
$round = 0
while (-not $finished -and $round -lt 10) {
    $round++
    $result = Invoke-Api -Method POST -Path "/interviews/$interviewId/answers" -Token $token -Body @{
        questionId      = $question.questionId
        answer          = $answerText.Trim()
        thinkingSeconds = 45
    }
    $score = $result.data.score
    Assert-True ($score -ge 0 -and $score -le 100) ("第 $round 题评分有效：" + $score)
    if ($result.data.finished) {
        $finished = $true
        $reportId = $result.data.reportId
        Write-Host ("  面试结束，reportId=" + $reportId) -ForegroundColor Yellow
    } else {
        $question = $result.data.nextQuestion
        Assert-True ($null -ne $question) '返回下一题'
    }
}
Assert-True $finished '五题作答完成并结束面试'

Write-Host "`n=== 5. 查看面试报告 ===" -ForegroundColor Cyan
$report = Invoke-Api -Method GET -Path "/interviews/$interviewId/report" -Token $token
Assert-True ($report.data.overallScore -ge 0) '报告总分有效'
Assert-True ($report.data.questions.Count -eq 5) '报告包含 5 道题'
Write-Host ("  总分：" + $report.data.overallScore + "，建议：" + $report.data.suggestion)
Write-Host ("  优势：" + ($report.data.strengths -join '；'))

Write-Host "`n=== 6. 面试记录与消息回放 ===" -ForegroundColor Cyan
$detail = Invoke-Api -Method GET -Path "/interviews/$interviewId" -Token $token
Assert-True ($detail.data.interview.status -eq 'FINISHED') '面试状态为 FINISHED'
$messages = Invoke-Api -Method GET -Path "/interviews/$interviewId/messages" -Token $token
Assert-True ($messages.data.Count -ge 10) '对话消息已持久化'

Write-Host "`n=== 7. 管理端统计 ===" -ForegroundColor Cyan
$adminLogin = Invoke-Api -Method POST -Path '/auth/login' -Body @{ username = 'admin'; password = '123456' }
$adminToken = $adminLogin.data.accessToken
$stats = Invoke-Api -Method GET -Path '/admin/stats/overview' -Token $adminToken
Assert-True ($stats.data.interviewCount -ge 1) '统计包含面试数'
Assert-True ($stats.data.last7Days.Count -eq 7) '近 7 天趋势为 7 条'
Write-Host ("  用户数：" + $stats.data.userCount + "，面试数：" + $stats.data.interviewCount + "，平均分：" + $stats.data.averageScore)
$users = Invoke-Api -Method GET -Path '/admin/users?page=1&size=10' -Token $adminToken
Assert-True ($users.data.total -ge 3) '用户列表返回 3 个演示账号'

Write-Host "`n=== 8. 权限校验 ===" -ForegroundColor Cyan
try {
    Invoke-Api -Method GET -Path '/admin/stats/overview' -Token $token | Out-Null
    throw '候选人访问管理端竟然成功了'
} catch {
    Assert-True ($_.Exception.Response.StatusCode.value__ -eq 403) '候选人访问管理端返回 403'
}

Write-Host "`n=== 9. 简历文本解析（异步）与岗位匹配 ===" -ForegroundColor Cyan
$textResume = Invoke-Api -Method POST -Path '/resumes/text' -Token $token -Body @{
    content = @'
李雷，男，本科学历，6 年 Java 后端开发经验。
联系邮箱 lilei@example.com，手机 13900002222。
技能：Java、Spring Cloud、MySQL、Redis、Kafka、Docker、Kubernetes。
项目：主导订单中心微服务拆分，基于 Seata 解决分布式事务，峰值 QPS 5000。
'@
}
Assert-True ($textResume.code -eq 0) '文本简历创建成功'
$textResumeId = $textResume.data.id

$parsedStatus = $null
for ($i = 0; $i -lt 20; $i++) {
    Start-Sleep -Milliseconds 500
    $detail = Invoke-Api -Method GET -Path "/resumes/$textResumeId" -Token $token
    $parsedStatus = $detail.data.resume.status
    if ($parsedStatus -eq 'PARSED' -or $parsedStatus -eq 'FAILED') { break }
}
Assert-True ($parsedStatus -eq 'PARSED') ("简历异步解析完成，当前状态：" + $parsedStatus)

$match = Invoke-Api -Method GET -Path "/resumes/$textResumeId/match?positionId=$($job.id)" -Token $token
Assert-True ($match.data.score -ge 0 -and $match.data.score -le 100) ("岗位匹配分有效：" + $match.data.score)

Write-Host "`n全部冒烟用例通过 ✅" -ForegroundColor Green
Write-Host ("面试报告可在 Swagger 或 GET /interviews/" + $interviewId + "/report 查看") -ForegroundColor Gray
