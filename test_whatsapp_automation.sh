#!/bin/bash

# Script de testing para automatización de WhatsApp
# Uso: ./test_whatsapp_automation.sh

set -e

echo "=================================="
echo "Testing WhatsApp Automation"
echo "=================================="
echo ""

# Colors
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Check if device is connected
echo -e "${YELLOW}Checking device connection...${NC}"
if ! adb devices | grep -q "device$"; then
    echo -e "${RED}No Android device found. Please connect a device.${NC}"
    exit 1
fi
echo -e "${GREEN}✓ Device connected${NC}"
echo ""

# Install APK
echo -e "${YELLOW}Installing APK...${NC}"
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
echo -e "${GREEN}✓ APK installed${NC}"
echo ""

# Check accessibility service
echo -e "${YELLOW}Checking accessibility service status...${NC}"
SERVICE_ENABLED=$(adb shell settings get secure enabled_accessibility_services | grep "com.spatium.deamon.db.temi")
if [ -z "$SERVICE_ENABLED" ]; then
    echo -e "${RED}⚠ Accessibility service not enabled${NC}"
    echo "Please enable it manually:"
    echo "1. Go to Settings > Accessibility"
    echo "2. Find 'WhatsAppAccessibilityService'"
    echo "3. Enable it"
    echo ""
    read -p "Press Enter when ready..."
fi
echo -e "${GREEN}✓ Accessibility service enabled${NC}"
echo ""

# Start logcat monitoring
echo -e "${YELLOW}Starting logcat monitoring...${NC}"
adb logcat -c
adb logcat > whatsapp_test_logs.txt &
LOGCAT_PID=$!
echo -e "${GREEN}✓ Logcat started (PID: $LOGCAT_PID)${NC}"
echo ""

# Launch app
echo -e "${YELLOW}Launching app...${NC}"
adb shell am start -n com.spatium.deamon.db.temi/.ui.MainActivity
echo -e "${GREEN}✓ App launched${NC}"
echo ""

# Instructions
echo -e "${YELLOW}=================================="
echo "Manual Testing Instructions"
echo "==================================${NC}"
echo ""
echo "1. Navigate to PartyActivity"
echo "2. Take a photo"
echo "3. Click 'Me encanta' to share via WhatsApp"
echo "4. Observe the automatic sending process"
echo "5. Check the logs for detailed information"
echo ""
echo "Logcat is being saved to: whatsapp_test_logs.txt"
echo "Filter logs with: grep WhatsAppA11y whatsapp_test_logs.txt"
echo ""
echo "Press Ctrl+C when done testing to stop logcat"
echo ""

# Wait for user to stop
trap "echo -e '${YELLOW}Stopping logcat...${NC}'; kill $LOGCAT_PID; echo -e '${GREEN}✓ Logcat stopped${NC}'; exit 0" INT

# Keep script running
while true; do
    sleep 1
done
