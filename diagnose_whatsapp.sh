#!/bin/bash

# Script para diagnosticar problemas con el servicio de accesibilidad de WhatsApp
# Uso: ./diagnose_whatsapp.sh

echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "🔍 DIAGNÓSTICO WHATSAPP AUTOMATION"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

# Colores
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

echo -e "${BLUE}1. Verificando conexión del dispositivo...${NC}"
if adb devices | grep -q "device$"; then
    echo -e "${GREEN}✓ Dispositivo conectado${NC}"
else
    echo -e "${RED}✗ No hay dispositivo conectado${NC}"
    echo "Por favor conecta un dispositivo Android y habilita el debugging USB"
    exit 1
fi
echo ""

echo -e "${BLUE}2. Verificando instalación de la app...${NC}"
if adb shell pm list packages | grep -q "com.spatium.deamon.db.temi"; then
    echo -e "${GREEN}✓ App instalada${NC}"
else
    echo -e "${RED}✗ App NO instalada${NC}"
    echo "Por favor instala la app primero"
    exit 1
fi
echo ""

echo -e "${BLUE}3. Verificando servicio de accesibilidad...${NC}"
SERVICE_ENABLED=$(adb shell settings get secure enabled_accessibility_services | grep "com.spatium.deamon.db.temi")
if [ -n "$SERVICE_ENABLED" ]; then
    echo -e "${GREEN}✓ Servicio de accesibilidad HABILITADO${NC}"
    echo "Servicios: $SERVICE_ENABLED"
else
    echo -e "${RED}✗ Servicio de accesibilidad NO habilitado${NC}"
    echo ""
    echo "Por favor habilita el servicio:"
    echo "1. Ve a Configuración > Accesibilidad"
    echo "2. Busca 'WhatsAppAccessibilityService'"
    echo "3. Actívalo"
    echo ""
    read -p "Presiona Enter cuando hayas habilitado el servicio..."
fi
echo ""

echo -e "${BLUE}4. Iniciando monitoreo de logs...${NC}"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
echo -e "${YELLOW}INSTRUCCIONES:${NC}"
echo "1. Abre la app en el robot"
echo "2. Toma una foto"
echo "3. Selecciona 'Me encanta'"
echo "4. Observa los logs a continuación"
echo ""
echo -e "${YELLOW}Presiona Ctrl+C cuando termines${NC}"
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

# Limpiar logcat anterior
adb logcat -c

# Filtrar logs relevantes
adb logcat -v time | grep -E "WhatsAppA11y|SharedData|PhotoPreviewActivity" &
LOGCAT_PID=$!

# Esperar
wait $LOGCAT_PID
