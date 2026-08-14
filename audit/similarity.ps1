# PhoneGuard 阶段二：GPL 上游 vs 本地实现相似度比对脚本
# 用法: powershell -File similarity.ps1 <upstream-dir> <local-dir>
param(
    [string]$UpstreamDir = (Join-Path $env:TEMP "pg-audit-src"),
    [string]$LocalDir = "C:\Users\<USERNAME>\phoneguard"
)

$keywords = @('fun','val','var','class','object','interface','if','else','when','for','while','return','import','package','private','public','internal','protected','data','override','suspend','companion','null','true','false','this','super','init','constructor','by','as','is','in','not','and','or','out','lateinit','sealed','enum','abstract','open','final','inline','noinline','crossinline','reified','operator','infix','tailrec','annotation','get','set','field','it','try','catch','finally','throw','require','check','error','TODO','do','elseif')

function Get-Tokens($path) {
    $text = [System.IO.File]::ReadAllText($path)
    $tokens = New-Object System.Collections.Generic.HashSet[string]
    # identifiers
    foreach ($m in [regex]::Matches($text, '[A-Za-z_][A-Za-z0-9_]{2,}')) {
        $t = $m.Value
        if ($keywords -notcontains $t -and $t -notmatch '^[A-Z][A-Z0-9_]{3,}$' -eq $false) { [void]$tokens.Add($t) }
    }
    # string literals (trim to 24 chars)
    foreach ($m in [regex]::Matches($text, '"[^"\r\n]{3,60}"')) {
        $s = $m.Value.Substring(1, $m.Value.Length - 2)
        if ($s.Length -gt 24) { $s = $s.Substring(0, 24) }
        [void]$tokens.Add("S:$s")
    }
    return $tokens
}

function Get-Jaccard($a, $b) {
    $inter = 0
    foreach ($t in $a) { if ($b.Contains($t)) { $inter++ } }
    $union = $a.Count + $b.Count - $inter
    if ($union -eq 0) { return 0.0 }
    return [math]::Round($inter / $union, 3)
}

# pairs: upstream -> local candidates
$pairs = @(
    @{ u = "curbox\app\src\main\java\neth\iecal\curbox\blockers\AppBlocker.kt"; l = @("kid\src\main\java\com\familyguard\kid\guard\GuardAccessibilityService.kt","kid\src\main\java\com\familyguard\kid\guard\BlockActivity.kt","kid\src\main\java\com\familyguard\kid\guard\BlockOverlay.kt","kid\src\main\java\com\familyguard\kid\MainActivity.kt") },
    @{ u = "curbox\app\src\main\java\neth\iecal\curbox\services\AppBlockerService.kt"; l = @("kid\src\main\java\com\familyguard\kid\stats\HeartbeatService.kt","kid\src\main\java\com\familyguard\kid\KidApp.kt","kid\src\main\java\com\familyguard\kid\stats\GuardWorker.kt") },
    @{ u = "curbox\app\src\main\java\neth\iecal\curbox\blockers\AntiUninstallBlocker.kt"; l = @("kid\src\main\java\com\familyguard\kid\protect\KidDeviceAdminReceiver.kt","kid\src\main\java\com\familyguard\kid\protect\KidDevicePolicyController.kt","kid\src\main\java\com\familyguard\kid\protect\KidDeviceAdminService.kt","kid\src\main\java\com\familyguard\kid\guard\ProtectionGuardPolicy.kt") },
    @{ u = "curbox\app\src\main\java\neth\iecal\curbox\utils\TimeGroupWindow.kt"; l = @("core\src\main\java\com\familyguard\core\rules\RuleCalendar.kt","core\src\main\java\com\familyguard\core\rules\RulesEngine.kt") },
    @{ u = "curbox\app\src\main\java\neth\iecal\curbox\utils\RestrictionComparator.kt"; l = @("core\src\main\java\com\familyguard\core\rules\RulesEngine.kt","core\src\main\java\com\familyguard\core\rules\RuleCalendar.kt") },
    @{ u = "apkupdater\app\src\main\kotlin\com\apkupdater\util\SessionInstaller.kt"; l = @("kid\src\main\java\com\familyguard\kid\update\KidUpdateManager.kt","kid\src\main\java\com\familyguard\kid\update\UpdateDeliveryPolicy.kt","core\src\main\java\com\familyguard\core\update\UpdateHttpClient.kt") },
    @{ u = "apkupdater\app\src\main\kotlin\com\apkupdater\worker\UpdatesWorker.kt"; l = @("kid\src\main\java\com\familyguard\kid\update\PeriodicUpdateCheck.kt","kid\src\main\java\com\familyguard\kid\update\UpdateDeliveryPolicy.kt","kid\src\main\java\com\familyguard\kid\update\KidUpdateManager.kt") },
    @{ u = "cst\app\src\main\java\io\github\childscreentime\service\ScreenLockService.java"; l = @("kid\src\main\java\com\familyguard\kid\guard\BlockOverlay.kt","kid\src\main\java\com\familyguard\kid\guard\BlockActivity.kt","kid\src\main\java\com\familyguard\kid\guard\GuardAccessibilityService.kt") },
    @{ u = "cst\app\src\main\java\io\github\childscreentime\service\ScreenTimeWorker.java"; l = @("kid\src\main\java\com\familyguard\kid\stats\GuardWorker.kt","kid\src\main\java\com\familyguard\kid\KidApp.kt") },
    @{ u = "cst\app\src\main\java\io\github\childscreentime\core\DeviceSecurityManager.java"; l = @("kid\src\main\java\com\familyguard\kid\guard\GuardAccessibilityService.kt","kid\src\main\java\com\familyguard\kid\guard\AccessibilityHealth.kt","kid\src\main\java\com\familyguard\kid\protect\KidDevicePolicyController.kt") },
    @{ u = "kidsafe\Application\app\src\main\java\com\mansourappdevelopment\androidapp\kidsafe\adapters\AppAdapter.java"; l = @("admin\src\main\java\com\familyguard\admin\RuleAdapter.kt","admin\src\main\java\com\familyguard\admin\AppLimitRowData.kt") },
    @{ u = "testdpc\src\main\java\com\afwsamples\testdpc\common\PackageInstallationUtils.java"; l = @("kid\src\main\java\com\familyguard\kid\update\KidUpdateManager.kt","core\src\main\java\com\familyguard\core\update\UpdateHttpClient.kt","kid\src\main\java\com\familyguard\kid\update\UpdateDeliveryPolicy.kt") },
    @{ u = "testdpc\src\main\java\com\afwsamples\testdpc\DevicePolicyManagerGatewayImpl.java"; l = @("kid\src\main\java\com\familyguard\kid\protect\KidDevicePolicyController.kt","kid\src\main\java\com\familyguard\kid\protect\KidDeviceAdminReceiver.kt","kid\src\main\java\com\familyguard\kid\protect\ProvisioningModePolicy.kt") },
    @{ u = "hmdm\app\src\main\java\com\hmdm\launcher\helper\ConfigUpdater.java"; l = @("kid\src\main\java\com\familyguard\kid\update\UpdateDeliveryPolicy.kt","kid\src\main\java\com\familyguard\kid\update\PeriodicUpdateCheck.kt","kid\src\main\java\com\familyguard\kid\update\UpdateDeliveryStore.kt") },
    @{ u = "hmdm\app\src\main\java\com\hmdm\launcher\util\InstallUtils.java"; l = @("kid\src\main\java\com\familyguard\kid\update\KidUpdateManager.kt","core\src\main\java\com\familyguard\core\update\UpdateManifest.kt") },
    @{ u = "hmdm\app\src\main\java\com\hmdm\launcher\worker\PushNotificationWorker.java"; l = @("admin\src\main\java\com\familyguard\admin\notify\AdminNotifyService.kt","kid\src\main\java\com\familyguard\kid\stats\HeartbeatService.kt") },
    @{ u = "hmdm\app\src\main\java\com\hmdm\launcher\receiver\BootReceiver.java"; l = @("kid\src\main\java\com\familyguard\kid\update\UpdateRecoveryReceiver.kt","kid\src\main\java\com\familyguard\kid\protect\PackageAddedReceiver.kt","kid\src\main\java\com\familyguard\kid\KidApp.kt") },
    @{ u = "hmdm\app\src\main\java\com\hmdm\launcher\util\RemoteLogger.java"; l = @("kid\src\main\java\com\familyguard\kid\stats\HeartbeatService.kt","core\src\main\java\com\familyguard\core\backend\CloudBaseEvents.kt") }
)

foreach ($p in $pairs) {
    $uPath = Join-Path $UpstreamDir $p.u
    if (-not (Test-Path $uPath)) { Write-Output "MISSING UPSTREAM: $($p.u)"; continue }
    $uTokens = Get-Tokens $uPath
    $uLines = (Get-Content $uPath).Count
    Write-Output ("`n=== {0} ({1} lines, {2} tokens) ===" -f $p.u, $uLines, $uTokens.Count)
    foreach ($lf in $p.l) {
        $lPath = Join-Path $LocalDir $lf
        if (-not (Test-Path $lPath)) { Write-Output ("  MISSING LOCAL: {0}" -f $lf); continue }
        $lTokens = Get-Tokens $lPath
        $j = Get-Jaccard $uTokens $lTokens
        $lLines = (Get-Content $lPath).Count
        Write-Output ("  {0,-75} lines={1,-5} tokens={2,-5} Jaccard={3}" -f $lf, $lLines, $lTokens.Count, $j)
    }
}
