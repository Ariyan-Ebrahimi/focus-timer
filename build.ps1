$ErrorActionPreference = "Stop"
$Root = $PSScriptRoot
$Main = Join-Path $Root "app\src\main"
$Out = Join-Path $Root "out"
$Sdk = Join-Path $env:LOCALAPPDATA "Android\Sdk"
$Bt = Join-Path $Sdk "build-tools\37.0.0"
$AndroidJar = Join-Path $Sdk "platforms\android-36\android.jar"

$aapt2 = Join-Path $Bt "aapt2.exe"
$d8 = Join-Path $Bt "d8.bat"
$zipalign = Join-Path $Bt "zipalign.exe"
$apksigner = Join-Path $Bt "apksigner.bat"

foreach ($d in @("$Out\gen", "$Out\classes", "$Out\dex")) {
    New-Item -ItemType Directory -Force -Path $d | Out-Null
}

function Check($step) {
    if ($LASTEXITCODE -ne 0) { throw "$step failed with exit code $LASTEXITCODE" }
}

Write-Host "[1/7] Compiling resources..."
& $aapt2 compile --dir (Join-Path $Main "res") -o (Join-Path $Out "compiled.zip")
Check "aapt2 compile"

Write-Host "[2/7] Linking..."
& $aapt2 link -o (Join-Path $Out "app-unaligned.apk") -I $AndroidJar `
    --manifest (Join-Path $Main "AndroidManifest.xml") `
    --java (Join-Path $Out "gen") `
    --min-sdk-version 26 --target-sdk-version 36 `
    (Join-Path $Out "compiled.zip")
Check "aapt2 link"

Write-Host "[3/7] Compiling Java..."
$javaFiles = @()
$javaFiles += Get-ChildItem (Join-Path $Main "java") -Recurse -Filter *.java | ForEach-Object { $_.FullName }
$javaFiles += Get-ChildItem (Join-Path $Out "gen") -Recurse -Filter *.java | ForEach-Object { $_.FullName }
& javac -source 8 -target 8 -classpath $AndroidJar -d (Join-Path $Out "classes") $javaFiles
Check "javac"

Write-Host "[4/7] Dexing..."
# bundle the classes into one jar: passing every .class file overflows the Windows command line
$classesJar = Join-Path $Out "classes.jar"
if (Test-Path $classesJar) { Remove-Item $classesJar }
& jar cf $classesJar -C (Join-Path $Out "classes") .
Check "jar"
& $d8 --lib $AndroidJar --min-api 26 --output (Join-Path $Out "dex") $classesJar
Check "d8"

Write-Host "[5/7] Adding classes.dex..."
Add-Type -AssemblyName System.IO.Compression.FileSystem
$apkPath = Join-Path $Out "app-unaligned.apk"
$zip = [System.IO.Compression.ZipFile]::Open($apkPath, "Update")
try {
    $dexFiles = Get-ChildItem (Join-Path $Out "dex") -Filter *.dex | Sort-Object Name
    foreach ($dex in $dexFiles) {
        [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip, $dex.FullName, $dex.Name) | Out-Null
    }
} finally {
    $zip.Dispose()
}

Write-Host "[6/7] Aligning..."
$aligned = Join-Path $Out "app-aligned.apk"
& $zipalign -f 4 $apkPath $aligned

Write-Host "[7/7] Signing..."
$ks = Join-Path $Root "debug.keystore"
if (-not (Test-Path $ks)) {
    & keytool -genkeypair -keystore $ks -alias androiddebugkey -storepass android -keypass android `
        -keyalg RSA -keysize 2048 -validity 10950 -dname "CN=Android Debug, O=Android, C=US"
}
$final = Join-Path $Root "TimerApp.apk"
& $apksigner sign --ks $ks --ks-pass pass:android --key-pass pass:android --out $final $aligned
& $apksigner verify --print-certs $final
Write-Host "DONE: $final"
