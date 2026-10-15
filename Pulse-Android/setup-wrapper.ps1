# Downloads Gradle's official wrapper JAR. Run once before opening Pulse in Android Studio.
# Source: the Gradle project's tagged release on GitHub.
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$target = Join-Path $root 'gradle\wrapper\gradle-wrapper.jar'
$url = 'https://raw.githubusercontent.com/gradle/gradle/v9.4.1/gradle/wrapper/gradle-wrapper.jar'
Write-Host 'Downloading Gradle 9.4.1 wrapper from the official Gradle GitHub repository...'
Invoke-WebRequest -Uri $url -OutFile $target -UseBasicParsing
if ((Get-Item $target).Length -lt 10000) { throw 'Downloaded wrapper JAR is unexpectedly small. Delete it and retry.' }
Write-Host "Downloaded wrapper: $target"
Write-Host 'To validate it independently, compare the SHA-256 against https://gradle.org/release-checksums/'
Write-Host 'You can now open this Pulse folder with Android Studio.'
