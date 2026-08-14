param(
    [Parameter(Mandatory = $true)][string]$ApkPath,
    [Parameter(Mandatory = $true)][long]$VersionCode,
    [Parameter(Mandatory = $true)][string]$VersionName,
    [Parameter(Mandatory = $true)][string]$DownloadUrl,
    [Parameter(Mandatory = $true)][string]$PrivateKeyPath,
    [Parameter(Mandatory = $true)][string]$OutputPath,
    [string]$OpenSslPath = 'openssl',
    [int]$ValidDays = 90
)

$ErrorActionPreference = 'Stop'
$resolvedApk = Resolve-Path -LiteralPath $ApkPath
$resolvedKey = Resolve-Path -LiteralPath $PrivateKeyPath
$apk = Get-Item -LiteralPath $resolvedApk
$apkSha256 = (Get-FileHash -LiteralPath $resolvedApk -Algorithm SHA256).Hash.ToLowerInvariant()
$certSha256 = '41091d4667b59d051895abdf1e01c32e83cb1aea0e8ca2b253c9020d6e29e937'
$issuedAt = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
$expiresAt = [DateTimeOffset]::UtcNow.AddDays($ValidDays).ToUnixTimeMilliseconds()
$releaseId = "kid-$VersionName-$VersionCode"

$fields = [ordered]@{
    schemaVersion = 1
    releaseId = $releaseId
    channel = 'stable'
    packageName = 'com.familyguard.kid'
    versionCode = $VersionCode
    versionName = $VersionName
    minSupportedVersionCode = 3
    url = $DownloadUrl
    sizeBytes = $apk.Length
    apkSha256 = $apkSha256
    signingCertSha256 = $certSha256
    mandatory = $false
    deadlineAt = 0
    rolloutPercent = 100
    issuedAt = $issuedAt
    expiresAt = $expiresAt
    signatureAlgorithm = 'SHA256withECDSA'
}

$canonical = (($fields.GetEnumerator() | ForEach-Object { "$($_.Key)=$($_.Value.ToString().ToLowerInvariant())" }) -join "`n") + "`n"
# Preserve case for string fields; only booleans need lowercase JSON spelling.
$canonical = "schemaVersion=1`nreleaseId=$releaseId`nchannel=stable`npackageName=com.familyguard.kid`nversionCode=$VersionCode`nversionName=$VersionName`nminSupportedVersionCode=3`nurl=$DownloadUrl`nsizeBytes=$($apk.Length)`napkSha256=$apkSha256`nsigningCertSha256=$certSha256`nmandatory=false`ndeadlineAt=0`nrolloutPercent=100`nissuedAt=$issuedAt`nexpiresAt=$expiresAt`nsignatureAlgorithm=SHA256withECDSA`n"

$outputDirectory = Split-Path -Parent $OutputPath
if ($outputDirectory) { New-Item -ItemType Directory -Path $outputDirectory -Force | Out-Null }
$tempDirectory = Join-Path ([System.IO.Path]::GetTempPath()) ("phoneguard-update-" + [guid]::NewGuid())
New-Item -ItemType Directory -Path $tempDirectory | Out-Null
try {
    $payloadPath = Join-Path $tempDirectory 'payload.txt'
    $signaturePath = Join-Path $tempDirectory 'signature.der'
    [System.IO.File]::WriteAllText($payloadPath, $canonical, [System.Text.UTF8Encoding]::new($false))
    & $OpenSslPath dgst -sha256 -sign $resolvedKey -out $signaturePath $payloadPath
    if ($LASTEXITCODE -ne 0) { throw "OpenSSL signing failed with exit code $LASTEXITCODE" }
    $manifest = [ordered]@{}
    $fields.GetEnumerator() | ForEach-Object { $manifest[$_.Key] = $_.Value }
    $manifest.manifestSignature = [Convert]::ToBase64String([System.IO.File]::ReadAllBytes($signaturePath))
    $json = $manifest | ConvertTo-Json -Depth 4
    [System.IO.File]::WriteAllText($OutputPath, $json + "`n", [System.Text.UTF8Encoding]::new($false))
} finally {
    if (Test-Path -LiteralPath $tempDirectory) { Remove-Item -LiteralPath $tempDirectory -Recurse -Force }
}

[PSCustomObject]@{
    ReleaseId = $releaseId
    VersionCode = $VersionCode
    VersionName = $VersionName
    SizeBytes = $apk.Length
    ApkSha256 = $apkSha256
    ExpiresAt = $expiresAt
    OutputPath = (Resolve-Path -LiteralPath $OutputPath).Path
}
