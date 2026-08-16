# 温言桌面版打包脚本：installDist → 精简 JRE（jdeps+jlink）→ jpackage app-image → exe 安装包
# 用法：powershell -File desktop/packaging/package.ps1 [-SkipInstaller]
# 产物：desktop/dist-package/温言-1.8.2/（绿色版）+ desktop/dist-package/温言-1.8.2.exe（安装包）
param([switch]$SkipInstaller)

$ErrorActionPreference = 'Stop'
$VERSION = '1.9.3'
$APP_NAME = '温言'
$JDK = 'C:\Users\Khalil\Android\jdk-21.0.12+8'
$ROOT = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)   # app/
$DESKTOP = Join-Path $ROOT 'desktop'
$OUT = Join-Path $DESKTOP 'dist-package'

Set-Location $ROOT

Write-Host "==> [1/5] gradle installDist"
& .\gradlew.bat :desktop:installDist --console=plain -q
if ($LASTEXITCODE -ne 0) { throw 'gradle installDist failed' }
$INSTALL = Join-Path $DESKTOP 'build\install\desktop'
if (-not (Test-Path "$INSTALL\bin\desktop.bat")) {
  # 兼容历史 timestamp 目录（H6 已移除 buildDir hack，仅旧构建残留）
  $INSTALL = Get-ChildItem -Directory $DESKTOP | Where-Object Name -like 'build.*' |
    Sort-Object LastWriteTime -Descending | Select-Object -First 1 |
    ForEach-Object { Join-Path $_.FullName 'install\desktop' }
}
if (-not (Test-Path "$INSTALL\bin\desktop.bat")) { throw "installDist not found under $DESKTOP\build or build.*" }
Write-Host "    installDist: $INSTALL"

Write-Host "==> [2/5] jdeps 探测模块依赖"
$JARS = (Get-ChildItem "$INSTALL\lib\*.jar" | ForEach-Object FullName) -join ';'
$jdepsOut = & "$JDK\bin\jdeps.exe" --multi-release 21 --ignore-missing-deps --recursive `
  --print-module-deps --class-path "$JARS" "$INSTALL\lib\desktop.jar" 2>&1
$MODULES = ($jdepsOut | Select-String -Pattern '^[a-z][a-z0-9_.]*(,[a-z][a-z0-9_.]*)*$' | Select-Object -First 1).Line
if (-not $MODULES) { throw "jdeps failed:`n$jdepsOut" }
# java.desktop：浏览打开 + ImageIO 图片压缩（jdeps 对 service loader 类可能漏检）
if ($MODULES -notmatch 'java.desktop') { $MODULES = "$MODULES,java.desktop" }
# TLS 加密提供者：OkHttp/HttpURLConnection 走 ServiceLoader 动态加载，jdeps 静态探测不到
# ——缺失 jdk.crypto.ec 会导致所有 HTTPS 握手 handshake_failure（桌面版外联全废的坑）
foreach ($m in @('jdk.crypto.ec', 'jdk.crypto.cryptoki', 'jdk.crypto.mscapi')) {
  if ($MODULES -notmatch [regex]::Escape($m)) { $MODULES = "$MODULES,$m" }
}
Write-Host "    modules: $MODULES"

Write-Host "==> [3/5] jlink 裁剪 JRE"
$JRE = Join-Path $OUT 'jre'
if (Test-Path $JRE) { Remove-Item -Recurse -Force $JRE }
& "$JDK\bin\jlink.exe" --add-modules $MODULES --strip-debug --no-man-pages --no-header-files `
  --compress=zip-6 --output $JRE
if ($LASTEXITCODE -ne 0) { throw 'jlink failed' }

Write-Host "==> [4/5] jpackage app-image"
$APPDIR = Join-Path $OUT "$APP_NAME-$VERSION"
# app-image 先输出到独立临时目录（避免与 dest 目录里残留的同名文件顶撞/锁），再整体挪为绿色版目录
$TMPIMG = Join-Path $OUT 'app-image-tmp'
foreach ($d in @($TMPIMG, $APPDIR)) {
  if (Test-Path $d) { Remove-Item -Recurse -Force $d }
}
& "$JDK\bin\jpackage.exe" --type app-image --name $APP_NAME --app-version $VERSION `
  --vendor 'moondrop' --runtime-image $JRE `
  --input "$INSTALL\lib" --main-jar desktop.jar --main-class com.wenyan.desktop.MainKt `
  --icon "$DESKTOP\packaging\wenyan.ico" --dest $TMPIMG --verbose
if ($LASTEXITCODE -ne 0) { throw 'jpackage app-image failed' }
# $TMPIMG\$APP_NAME → $APPDIR（Move-Item 整体挪动，目标已预清）
Move-Item (Join-Path $TMPIMG $APP_NAME) $APPDIR -Force -ErrorAction Stop
Remove-Item -Recurse -Force $TMPIMG -ErrorAction SilentlyContinue
if (-not (Test-Path $APPDIR)) { throw "app-image move failed: $APPDIR 不存在" }

if ($SkipInstaller) { Write-Host "==> [5/5] 跳过安装包（-SkipInstaller）"; exit 0 }

Write-Host "==> [5/5] jpackage exe 安装包（需要 WiX 3.x）"
# WiX 定位：系统安装优先，免安装包（%TEMP%\wix314）兜底；都没有则从 gh-proxy 镜像下载免安装版
$wixBin = @(
  "$env:WIX\bin", 'C:\Program Files (x86)\WiX Toolset v3.14\bin',
  'C:\Program Files (x86)\WiX Toolset v3.11\bin', "$env:TEMP\wix314"
) | Where-Object { $_ -and (Test-Path "$_\candle.exe") } | Select-Object -First 1
if (-not $wixBin) {
  Write-Host '    WiX 未安装，下载免安装二进制（gh-proxy 镜像）…'
  $zip = "$env:TEMP\wix314.zip"
  Invoke-WebRequest -Uri 'https://gh-proxy.com/https://github.com/wixtoolset/wix3/releases/download/wix3141rtm/wix314-binaries.zip' -OutFile $zip
  Expand-Archive -Path $zip -DestinationPath "$env:TEMP\wix314" -Force
  $wixBin = "$env:TEMP\wix314"
}
Write-Host "    WiX: $wixBin"
$env:PATH = "$wixBin;$env:PATH"
& "$JDK\bin\jpackage.exe" --type exe --name $APP_NAME --app-version $VERSION `
  --vendor 'moondrop' --app-image $APPDIR `
  --icon "$DESKTOP\packaging\wenyan.ico" --dest $OUT `
  --win-dir-chooser --win-menu --win-menu-group $APP_NAME --win-shortcut --win-per-user-install
if ($LASTEXITCODE -ne 0) { throw 'jpackage exe failed' }

Write-Host "`n产物："
Get-ChildItem $OUT | Select-Object Name, Length | Format-Table
