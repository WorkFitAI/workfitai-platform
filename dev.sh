#!/bin/bash

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}🚀 WorkFitAI Platform - Development Script${NC}"
echo "============================================="

# Check if profile is provided
PROFILE=${1:-full}
ACTION=${2:-up}

echo -e "${YELLOW}📋 Profile: $PROFILE${NC}"
echo -e "${YELLOW}📋 Action: $ACTION${NC}"

case $ACTION in
    "up"|"start")
        echo -e "${BLUE}🏗️  Building and starting services...${NC}"
        docker-compose --profile $PROFILE up --build -d
        if [ $? -eq 0 ]; then
            echo -e "${GREEN}✅ Services started successfully!${NC}"
            echo ""
            echo -e "${BLUE}📊 Available endpoints:${NC}"
            echo "• Consul UI: http://localhost:8500"
            echo "• Vault UI: http://localhost:8200 (Token: dev-token)"
            echo "• Kafka UI: http://localhost:8080"
            echo "• Grafana: http://localhost:3001 (admin/admin)"
            echo "• Prometheus: http://localhost:9090"
            echo ""
            echo "• Auth Service: http://localhost:9080"
            echo "• User Service: http://localhost:9081"
            echo "• Job Service: http://localhost:9082"
            echo "• CV Service: http://localhost:9083"
            echo "• Application Service: http://localhost:9084"
            echo "• API Gateway: http://localhost:9085"
            echo "• Monitoring Service: http://localhost:9086"
            echo ""
            echo -e "${YELLOW}💡 Check Vault initialization: curl http://localhost:9086/api/vault/status${NC}"
        else
            echo -e "${RED}❌ Failed to start services${NC}"
            exit 1
        fi
        ;;
    "down"|"stop")
        echo -e "${YELLOW}🛑 Stopping all services...${NC}"
        docker-compose --profile $PROFILE down
        echo -e "${GREEN}✅ All services stopped${NC}"
        ;;
    "restart")
        echo -e "${YELLOW}🔄 Restarting services (removing volumes)...${NC}"
        docker-compose --profile $PROFILE down -v
        docker-compose --profile $PROFILE up --build -d
        echo -e "${GREEN}✅ Services restarted (fresh volumes)${NC}"
        ;;
    "logs")
        SERVICE=${3:-""}
        if [ -n "$SERVICE" ]; then
            echo -e "${BLUE}📝 Showing logs for $SERVICE...${NC}"
            docker-compose logs -f $SERVICE
        else
            echo -e "${BLUE}📝 Showing logs for all services...${NC}"
            docker-compose logs -f
        fi
        ;;
    "build")
        echo -e "${BLUE}🏗️  Building services...${NC}"
        docker-compose --profile $PROFILE build --no-cache
        echo -e "${GREEN}✅ Build completed${NC}"
        ;;
    "status")
        echo -e "${BLUE}📊 Service status:${NC}"
        docker-compose ps
        ;;
    "clean")
        echo -e "${YELLOW}🧹 Cleaning up...${NC}"
        docker-compose --profile $PROFILE down -v --remove-orphans
        docker system prune -f
        echo -e "${GREEN}✅ Cleanup completed${NC}"
        ;;
    *)
        echo -e "${RED}❌ Unknown action: $ACTION${NC}"
        echo ""
        echo -e "${BLUE}Available actions:${NC}"
        echo "  up|start    - Build and start services"
        echo "  down|stop   - Stop all services"
        echo "  restart     - Restart all services"
        echo "  logs [service] - Show logs"
        echo "  build       - Build services only"
        echo "  status      - Show service status"
        echo "  clean       - Stop and clean everything"
        echo ""
        echo -e "${BLUE}Available profiles:${NC}"
        echo "  infra       - Infrastructure only (databases, vault, etc.)"
        echo "  services    - Application services only"
        echo "  full        - Everything (default)"
        echo ""
        echo -e "${BLUE}Examples:${NC}"
        echo "  ./dev.sh full up     # Start everything"
        echo "  ./dev.sh infra up    # Start infrastructure only"
        echo "  ./dev.sh full restart # Restart everything"
        echo "  ./dev.sh full logs monitoring-service # Show monitoring logs"
        exit 1
        ;;
esac