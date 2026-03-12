$shell = New-Object -ComObject WScript.Shell
$shortcut = $shell.CreateShortcut("C:\ProgramData\Microsoft\Windows\Start Menu\Programs\Android Studio\Android Studio.lnk")
Write-Host "ANDROID_STUDIO_PATH: $($shortcut.TargetPath)"
