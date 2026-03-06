# WhatsApp Automation - Quick Reference Guide

## 🚀 Quick Start

### Build and Install
```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Enable Accessibility Service
1. Settings > Accessibility
2. Find "WhatsAppAccessibilityService"
3. Enable it

### Monitor Logs
```bash
adb logcat | grep WhatsAppA11y
```

### View All Logs
```bash
adb logcat > whatsapp_logs.txt
```

## 📁 File Structure

```
app/src/main/java/com/spatium/deamon/db/temi/ui/
├── whatsapp/
│   ├── WhatsAppConstants.kt           (IDs, texts, timeouts)
│   ├── WhatsAppSendResult.kt          (Sealed results)
│   ├── WhatsAppAccessibilityLogger.kt (Logging)
│   ├── WhatsAppNodeFinder.kt          (Search engine)
│   └── WhatsAppClickPerformer.kt      (Click strategies)
├── WhatsAppAccessibilityService.kt    (Main service)
├── SharedData.kt                      (State management)
└── PhotoPreviewActivity.kt            (UI entry point)
```

## 🔍 Debugging Tips

### Enable Verbose Logging
In `WhatsAppAccessibilityLogger.kt`:
```kotlin
private const val ENABLE_VERBOSE_LOGGING = true
private const val ENABLE_NODE_DUMPING = true
```

### Dump Node Hierarchy
The service automatically dumps the hierarchy in the first 2 attempts.

### Common Issues

| Issue | Solution |
|-------|----------|
| Service not working | Check accessibility settings |
| Button not found | Check logs for strategy used |
| Click failing | Try different click method manually |
| Timeout | Increase MAX_TOTAL_ATTEMPTS_MS |

## ⚙️ Configuration

### Timeouts
In `WhatsAppConstants.kt`:
```kotlin
const val EVENT_DEBOUNCE_MS = 300L
const val INITIAL_SEARCH_DELAY_MS = 500L
const val RETRY_DELAY_MS = 200L
const val MAX_SEARCH_RETRIES = 5
const val MAX_TOTAL_ATTEMPTS_MS = 5000L
```

### Search Strategies
Priority order in `WhatsAppNodeFinder.kt`:
1. View ID (95% confidence)
2. Content Description (85%)
3. Text Matching (80%)
4. ClassName + Position (65%)
5. Visual Heuristic (40-60%)

## 📊 Log Tags

| Tag | Purpose |
|-----|---------|
| WhatsAppA11y | Main service logs |
| SharedData | State changes |
| PhotoPreviewActivity | UI logs |

## 🧪 Testing Checklist

- [ ] Standard WhatsApp (com.whatsapp)
- [ ] WhatsApp Business (com.whatsapp.w4b)
- [ ] Retries on failure
- [ ] Timeout handling
- [ ] Error messages
- [ ] Return to app after sending

## 📞 Support

For issues:
1. Check logs: `adb logcat | grep WhatsAppA11y`
2. Review `WHATSAPP_AUTOMATION_PLAN.md`
3. Enable verbose logging
4. Check node hierarchy dump
