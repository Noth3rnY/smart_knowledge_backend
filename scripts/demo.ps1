param([string]$BaseUrl = 'http://localhost:8080')
$ErrorActionPreference = 'Stop'
$apiRoot = "$($BaseUrl.TrimEnd('/'))/api/v1"
function New-Id { return [guid]::NewGuid().ToString() }
function Invoke-Api([string]$Method, [string]$Path, $Body, [string]$Token) {
    $parameters = @{ Method=$Method; Uri="$apiRoot$Path"; ContentType='application/json; charset=utf-8' }
    if ($Token) { $parameters.Headers = @{ Authorization="Bearer $Token" } }
    if ($null -ne $Body) { $parameters.Body = [System.Text.Encoding]::UTF8.GetBytes(($Body | ConvertTo-Json -Depth 30 -Compress)) }
    $result = Invoke-RestMethod @parameters
    if ($result.code -ne 'OK') { throw "请求失败: $($result.code)" }
    return $result.data
}
function Require($Condition, [string]$Message) { if (-not $Condition) { throw $Message } }
$username = 'demo_' + (New-Id).Replace('-', '').Substring(0,16)
$password = 'Demo!' + (New-Id)
Invoke-Api POST '/auth/register' @{username=$username; password=$password; nickname='课程演示用户'} '' | Out-Null
$deviceA = New-Id
$a = Invoke-Api POST '/auth/login' @{username=$username; password=$password; deviceUuid=$deviceA; deviceName='手机'; platform='android'} ''
$tokenA = $a.accessToken
Invoke-Api POST '/subscriptions/demo-activate' @{requestId=(New-Id)} $tokenA | Out-Null
Invoke-Api PUT '/me/preferences' @{syncEnabled=$true; backupEnabled=$true; aiEnabled=$true; consentVersion='2026-10-v1'} $tokenA | Out-Null
Write-Host '1. 账号、订阅与云服务授权完成'
$noteId = New-Id
$original = @{id=$noteId; baseVersion=0; title='Flutter 界面渲染与掉帧优化'; content="# 性能笔记`n减少无效重建，检查 SQL 查询。"; tags=@(@{name='Flutter'; source='manual'})}
Invoke-Api POST '/sync/push' @{deviceUuid=$deviceA; requestId=(New-Id); changes=@(@{operation='upsert'; note=$original})} $tokenA | Out-Null
$deviceB = New-Id
$b = Invoke-Api POST '/auth/login' @{username=$username; password=$password; deviceUuid=$deviceB; deviceName='电脑'; platform='desktop'} ''
$tokenB = $b.accessToken
$pulled = Invoke-Api GET '/sync/pull?after=0' $null $tokenB
Require ($pulled.items.Count -eq 1) '第二设备没有收到笔记'
Write-Host '2. 第一设备上传、第二设备拉取完成'
$updated = @{id=$noteId; baseVersion=1; title=$original.title; content='手机修改后的云端稿'; tags=$original.tags}
Invoke-Api PUT "/notes/$noteId" $updated $tokenA | Out-Null
$offline = @{id=$noteId; baseVersion=1; title=$original.title; content='电脑离线修改后的稿件'; tags=$original.tags}
$conflicted = Invoke-Api POST '/sync/push' @{deviceUuid=$deviceB; requestId=(New-Id); changes=@(@{operation='upsert'; note=$offline})} $tokenB
$conflictId = $conflicted.results[0].conflictId
Require ($null -ne $conflictId) '没有检测到版本冲突'
$resolved = Invoke-Api POST "/sync/conflicts/$conflictId/resolve" @{serverVersion=2; choice='submitted'} $tokenB
Require ($resolved.content -eq $offline.content) '冲突解决没有保留选中的版本'
Write-Host '3. 双方修改均保留，冲突选择完成'
$backup = Invoke-Api POST '/backups' @{name='课程演示备份'} $tokenA
$download = Invoke-Api GET "/backups/$($backup.id)/download" $null $tokenA
Require ($download.notes.Count -eq 1) '备份快照内容不完整'
$restored = Invoke-Api POST "/backups/$($backup.id)/restore" $null $tokenA
Require ($restored.notes[0].id -ne $noteId) '恢复应生成新笔记'
Write-Host '4. 备份下载与恢复为新笔记完成'
$aiRequest = @{requestId=(New-Id); title=$original.title; content=$original.content; existingTags=@('Flutter')}
$suggestions = Invoke-Api POST '/ai/tags' $aiRequest $tokenA
Invoke-Api POST '/ai/tags' $aiRequest $tokenA | Out-Null
$usage = Invoke-Api GET '/subscriptions/me' $null $tokenA
Require ($usage.aiUsedThisMonth -eq 1) '重复 AI 请求不应重复扣额'
Write-Host "5. AI 标签完成：$($suggestions.tags -join '、')；模式=$($suggestions.provider)，本月用量=$($usage.aiUsedThisMonth)"
Write-Host "演示通过。账号=$username，笔记=$noteId，备份=$($backup.id)"
