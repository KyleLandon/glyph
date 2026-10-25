' Launches Glyph DDNS with no console window.
' Task Scheduler's powershell -WindowStyle Hidden still flashes briefly;
' WScript Run style 0 does not.
Option Explicit
Dim shell, here, cmd
Set shell = CreateObject("WScript.Shell")
here = CreateObject("Scripting.FileSystemObject").GetParentFolderName(WScript.ScriptFullName)
cmd = "powershell.exe -NoProfile -NonInteractive -ExecutionPolicy Bypass -File """ & _
      here & "\cloudflare-ddns.ps1"""
shell.Run cmd, 0, False
