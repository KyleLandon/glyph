# Registers every Glyph scheduled task on this machine (docs/MINI_PC.md).
# Run once as Administrator, signed in as the Windows user that will run the
# servers (Docker Desktop, JAVA_HOME and the DDNS env vars are per-user).
# Idempotent: re-running replaces the tasks.
#
#   Glyph Servers    at logon     scripts\start-all.ps1 (whole network)
#   Glyph DDNS       every 5 min  scripts\run-ddns-hidden.vbs
#   Glyph Auto Pull  every 2 min  scripts\auto-pull.ps1
#   Glyph Backup     daily 04:30  scripts\backup.ps1
#
#   .\register-tasks.ps1
#   .\register-tasks.ps1 -BackupDestination D:\glyph-backups -BackupRetentionDays 7

param(
    [string]$BackupDestination = "",
    [int]$BackupRetentionDays = 7
)

$ErrorActionPreference = "Stop"
$user = "$env:USERDOMAIN\$env:USERNAME"

$action = New-ScheduledTaskAction -Execute "powershell.exe" `
    -Argument "-NoProfile -WindowStyle Hidden -ExecutionPolicy Bypass -File `"$PSScriptRoot\start-all.ps1`""
$trigger = New-ScheduledTaskTrigger -AtLogOn -User $user
$trigger.Delay = "PT30S"
$settings = New-ScheduledTaskSettingsSet -StartWhenAvailable `
    -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries `
    -ExecutionTimeLimit (New-TimeSpan -Minutes 15)
Register-ScheduledTask -TaskName "Glyph Servers" -Action $action -Trigger $trigger `
    -Settings $settings -User $user -Force | Out-Null
Write-Output "Registered 'Glyph Servers' (at logon of $user)"

$action = New-ScheduledTaskAction -Execute "wscript.exe" -Argument "`"$PSScriptRoot\run-ddns-hidden.vbs`""
$trigger = New-ScheduledTaskTrigger -Once -At (Get-Date).AddMinutes(1) `
    -RepetitionInterval (New-TimeSpan -Minutes 5) -RepetitionDuration (New-TimeSpan -Days 3650)
$settings = New-ScheduledTaskSettingsSet -StartWhenAvailable `
    -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries `
    -ExecutionTimeLimit (New-TimeSpan -Minutes 2) -MultipleInstances IgnoreNew
Register-ScheduledTask -TaskName "Glyph DDNS" -Action $action -Trigger $trigger `
    -Settings $settings -User $user -Force | Out-Null
Write-Output "Registered 'Glyph DDNS' (every 5 min)"

& "$PSScriptRoot\auto-pull.ps1" -Register

$backupArgs = @{ Register = $true; RetentionDays = $BackupRetentionDays }
if ($BackupDestination) { $backupArgs.Destination = $BackupDestination }
& "$PSScriptRoot\backup.ps1" @backupArgs
