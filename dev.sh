#!/bin/bash

# =========================
# Colors for output
# =========================
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}🚀 WorkFitAI Platform - Development Script${NC}"
echo "============================================="

# =========================
# Env file detection
# =========================
ENV_FILE=".env"
if [ -f ".env.local" ]; then
    ENV_FILE=".env.local"
    echo -e "${GREEN}✅ Using .env.local${NC}"
fi

# =========================
# Args
# =========================
PROFILE=${1:-full}
ACTION=${2:-up}
SERVICE=${3:-""}

echo -e "${YELLOW}📋 Profile: $PROFILE${NC}"
echo -e "${YELLOW}📋 Action: $ACTION${NC}"
[ -n "$SERVICE" ] && echo -e "${YELLOW}📋 Service: $SERVICE${NC}"

# =========================
# Helpers
# =========================
compose() {
    docker-compose --env-file "$ENV_FILE" --profile "$PROFILE" "$@"
}

# =========================
# Actions
# =========================
case $ACTION in
    up|start)
        if [ -n "$SERVICE" ]; then
            echo -e "${BLUE}🏗️  Building & starting service: $SERVICE${NC}"
            compose up --build -d "$SERVICE"
        else
            echo -e "${BLUE}🏗️  Building & starting ALL services (profile: $PROFILE)${NC}"
            compose up --build -d
        fi

        if [ $? -eq 0 ]; then
            echo -e "${GREEN}✅ Services started successfully!${NC}"
        else
            echo -e "${RED}❌ Failed to start services${NC}"
            exit 1
        fi
        ;;
    
    down|stop)
        if [ -n "$SERVICE" ]; then
            echo -e "${YELLOW}🛑 Stopping service: $SERVICE${NC}"
            compose stop "$SERVICE"
        else
            echo -e "${YELLOW}🛑 Stopping all services...${NC}"
            compose down
        fi
        echo -e "${GREEN}✅ Stop completed${NC}"
        ;;
    
    restart)
        if [ -n "$SERVICE" ]; then
            echo -e "${YELLOW}🔄 Restarting service: $SERVICE${NC}"
            compose stop "$SERVICE"
            compose up --build -d "$SERVICE"
        else
            echo -e "${YELLOW}🔄 Restarting ALL services (fresh volumes)...${NC}"
            compose down -v
            compose up --build -d
        fi
        echo -e "${GREEN}✅ Restart completed${NC}"
        ;;
    
    logs)
        if [ -n "$SERVICE" ]; then
            echo -e "${BLUE}📝 Showing logs for $SERVICE...${NC}"
            compose logs -f "$SERVICE"
        else
            echo -e "${BLUE}📝 Showing logs for ALL services...${NC}"
            compose logs -f
        fi
        ;;
    
    build)
        if [ -n "$SERVICE" ]; then
            echo -e "${BLUE}🏗️  Building service: $SERVICE${NC}"
            compose build --no-cache "$SERVICE"
        else
            echo -e "${BLUE}🏗️  Building ALL services...${NC}"
            compose build --no-cache
        fi
        echo -e "${GREEN}✅ Build completed${NC}"
        ;;
    
    status)
        echo -e "${BLUE}📊 Service status:${NC}"
        compose ps
        ;;
    
    clean)
        echo -e "${YELLOW}🧹 Cleaning up everything...${NC}"
        compose down -v --remove-orphans
        docker system prune -f
        echo -e "${GREEN}✅ Cleanup completed${NC}"
        ;;
    
    *)
        echo -e "${RED}❌ Unknown action: $ACTION${NC}"
        echo ""
        echo -e "${BLUE}Available actions:${NC}"
        echo "  up|start [service]     - Build & start services"
        echo "  down|stop [service]   - Stop services"
        echo "  restart [service]     - Restart services"
        echo "  logs [service]        - Show logs"
        echo "  build [service]       - Build services"
        echo "  status                - Show service status"
        echo "  clean                 - Stop and clean everything"
        echo ""
        echo -e "${BLUE}Available profiles:${NC}"
        echo "  infra     - Infrastructure only"
        echo "  services  - Application services only"
        echo "  full      - Everything (default)"
        echo ""
        echo -e "${BLUE}Examples:${NC}"
        echo "  ./dev.sh full up user-service"
        echo "  ./dev.sh services restart notification-service"
        echo "  ./dev.sh infra up"
        exit 1
        ;;
esac
