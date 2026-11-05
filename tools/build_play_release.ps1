[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*){2,}$')]
    [string]$ApplicationId,
    [ValidateRange(8, 2100000000)]
    [int]$VersionCode = 8,
    [ValidatePattern('^[0-9]+\.[0-9]+\.[0-9]+$')]
    [string]$VersionName = '1.0.0',
    [switch]$InitializeUploadKey
)

# Windows DPAPI protects the password at rest. Nothing secret is passed in argv.
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$projectRoot = Split-Path -Parent $PSScriptRoot
$keyDirectory = Join-Path $projectRoot '.private/play-upload'
$keyPath = Join-Path $keyDirectory 'kaptus-upload.jks'
$passwordPath = Join-Path $keyDirectory 'password.dpapi'
$keytoolPath = Join-Path $env:JAVA_HOME 'bin/keytool.exe'
if (-not (Test-Path -LiteralPath $keytoolPath)) { throw 'Set JAVA_HOME to JDK 17 first.' }

$signingNames = @('KAPTUS_UPLOAD_STORE_FILE', 'KAPTUS_UPLOAD_STORE_PASSWORD', 'KAPTUS_UPLOAD_KEY_ALIAS', 'KAPTUS_UPLOAD_KEY_PASSWORD')
$previousEnvironment = @{}
foreach ($name in $signingNames) { $previousEnvironment[$name] = [Environment]::GetEnvironmentVariable($name, 'Process') }

try {
    if (-not (Test-Path -LiteralPath $keyPath)) {
        if (-not $InitializeUploadKey) { throw 'No upload key exists. Run once with -InitializeUploadKey, or supply an existing key before continuing.' }
        if (Test-Path -LiteralPath $passwordPath) { throw 'An existing password file was found without its keystore. Restore the keystore before continuing.' }
        New-Item -ItemType Directory -Path $keyDirectory -Force | Out-Null
        $identity = [Security.Principal.WindowsIdentity]::GetCurrent().User
        $acl = Get-Acl -LiteralPath $keyDirectory
        $acl.SetAccessRuleProtection($true, $false)
        $acl.SetAccessRule([Security.AccessControl.FileSystemAccessRule]::new(
            $identity, 'FullControl', 'ContainerInherit,ObjectInherit', 'None', 'Allow'))
        Set-Acl -LiteralPath $keyDirectory -AclObject $acl

        $randomBytes = [byte[]]::new(32)
        $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
        try { $rng.GetBytes($randomBytes) } finally { $rng.Dispose() }
        $secret = [Convert]::ToBase64String($randomBytes)
        $securePassword = ConvertTo-SecureString $secret -AsPlainText -Force
        ConvertFrom-SecureString $securePassword | Set-Content -LiteralPath $passwordPath -Encoding ascii
        $env:KAPTUS_UPLOAD_STORE_PASSWORD = $secret
        $env:KAPTUS_UPLOAD_KEY_PASSWORD = $secret
        & $keytoolPath -genkeypair -keystore $keyPath -storetype JKS -alias upload `
            -keyalg RSA -keysize 4096 -validity 10000 -dname 'CN=Kaptus Upload' `
            -storepass:env KAPTUS_UPLOAD_STORE_PASSWORD -keypass:env KAPTUS_UPLOAD_KEY_PASSWORD
        if ($LASTEXITCODE -ne 0) { throw 'Upload key creation failed. Inspect the private key directory before retrying.' }
        $secret = $null
        [Array]::Clear($randomBytes, 0, $randomBytes.Length)
    }

    if (-not (Test-Path -LiteralPath $passwordPath)) { throw 'The protected upload-key password is missing.' }
    $securePassword = (Get-Content -LiteralPath $passwordPath -Raw).Trim() | ConvertTo-SecureString
    $env:KAPTUS_UPLOAD_STORE_PASSWORD = [Net.NetworkCredential]::new('', $securePassword).Password
    $env:KAPTUS_UPLOAD_KEY_PASSWORD = $env:KAPTUS_UPLOAD_STORE_PASSWORD
    $env:KAPTUS_UPLOAD_STORE_FILE = $keyPath
    $env:KAPTUS_UPLOAD_KEY_ALIAS = 'upload'

    & $keytoolPath -exportcert -rfc -keystore $keyPath -alias upload `
        -storepass:env KAPTUS_UPLOAD_STORE_PASSWORD -file (Join-Path $keyDirectory 'upload-certificate.pem')
    if ($LASTEXITCODE -ne 0) { throw 'The upload key could not be opened.' }

    Push-Location $projectRoot
    try {
        & ./gradlew.bat testDebugUnitTest lintRelease bundleRelease assembleRelease `
            "-PkaptusApplicationId=$ApplicationId" "-PkaptusVersionCode=$VersionCode" "-PkaptusVersionName=$VersionName" `
            --no-daemon --console=plain
        if ($LASTEXITCODE -ne 0) { throw 'Release verification or build failed.' }
        $destination = Join-Path $projectRoot "build/play-release/$VersionName"
        New-Item -ItemType Directory -Path $destination -Force | Out-Null
        $bundlePath = Join-Path $destination "Kaptus-$VersionName.aab"
        $apkPath = Join-Path $destination "Kaptus-$VersionName.apk"
        Copy-Item -LiteralPath 'app/build/outputs/bundle/release/app-release.aab' -Destination $bundlePath
        Copy-Item -LiteralPath 'app/build/outputs/apk/release/app-release.apk' -Destination $apkPath
        $artifacts = @($bundlePath, $apkPath) | ForEach-Object {
            $file = Get-Item -LiteralPath $_
            [ordered]@{ file = $file.Name; bytes = $file.Length; sha256 = (Get-FileHash -LiteralPath $_ -Algorithm SHA256).Hash.ToLowerInvariant() }
        }
        [ordered]@{
            applicationId = $ApplicationId
            versionCode = $VersionCode
            versionName = $VersionName
            sourceCommit = (git rev-parse HEAD)
            workingTreeHasChanges = [bool](git status --porcelain --untracked-files=no)
            artifacts = @($artifacts)
        } | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $destination 'release-manifest.json') -Encoding utf8
        Write-Output "Release files: $destination"
    } finally { Pop-Location }
} finally {
    foreach ($name in $signingNames) { [Environment]::SetEnvironmentVariable($name, $previousEnvironment[$name], 'Process') }
    $secret = $null
    if ($null -ne (Get-Variable securePassword -ErrorAction SilentlyContinue)) { $securePassword.Dispose() }
}
