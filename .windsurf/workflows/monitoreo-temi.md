---
description: Monitoreo del robot
auto_execution_mode: 3
---

incia esto para monitoriar en vivo los logs del robot
adb connect 192.168.52.37:5555

& "C:\Users\samir\AppData\Local\Android\Sdk\platform-tools\adb.exe" `
  -s 192.168.52.37:5555 logcat | Select-String "TemiBridge|TemiController"