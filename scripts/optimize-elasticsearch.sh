#!/bin/bash

# Script to optimize Elasticsearch for WorkFitAI Platform

echo "🔧 Optimizing Elasticsearch for lower memory usage..."

ES_HOST="localhost:9200"
ES_USER="elastic"
ES_PASS="workfitai123"

# Function to make ES API calls
es_api() {
    curl -s -u "$ES_USER:$ES_PASS" -H "Content-Type: application/json" "$@"
}

echo ""
echo "📊 Current Memory Usage:"
docker stats --no-stream --format "table {{.Name}}\t{{.MemUsage}}\t{{.MemPerc}}" | grep -E "NAME|elasticsearch"

echo ""
echo "🗂️  Current Indices:"
es_api "http://$ES_HOST/_cat/indices?v&h=index,docs.count,store.size" | head -15

echo ""
echo "⚙️  Applying optimizations..."

# 1. Giảm số lượng replicas (không cần trong dev)
echo "  → Disabling replicas for all indices..."
es_api -X PUT "http://$ES_HOST/_all/_settings" -d '{
  "index": {
    "number_of_replicas": 0
  }
}' > /dev/null

# 2. Giảm refresh interval (tăng performance, giảm memory)
echo "  → Increasing refresh interval to 30s..."
es_api -X PUT "http://$ES_HOST/_all/_settings" -d '{
  "index": {
    "refresh_interval": "30s"
  }
}' > /dev/null

# 3. Disable dynamic mapping cho logs (giảm overhead)
echo "  → Optimizing log index mapping..."
es_api -X PUT "http://$ES_HOST/_index_template/workfitai-logs-template" -d '{
  "index_patterns": ["workfitai-logs-*"],
  "template": {
    "settings": {
      "number_of_shards": 1,
      "number_of_replicas": 0,
      "refresh_interval": "30s",
      "index.queries.cache.enabled": true,
      "index.requests.cache.enable": true
    },
    "mappings": {
      "dynamic": "strict",
      "properties": {
        "@timestamp": { "type": "date" },
        "service": { "type": "keyword" },
        "level": { "type": "keyword" },
        "message": { "type": "text", "index": false },
        "log_type": { "type": "keyword" },
        "action": { "type": "keyword" },
        "user": { "type": "keyword" },
        "trace_id": { "type": "keyword" },
        "container_name": { "type": "keyword" }
      }
    }
  }
}' > /dev/null

# 4. Force merge old indices (giảm segments)
echo "  → Force merging old indices..."
YESTERDAY=$(date -u -v-1d '+%Y.%m.%d' 2>/dev/null || date -u -d '1 day ago' '+%Y.%m.%d')
es_api -X POST "http://$ES_HOST/workfitai-logs-$YESTERDAY/_forcemerge?max_num_segments=1" > /dev/null 2>&1

# 5. Clear cache
echo "  → Clearing caches..."
es_api -X POST "http://$ES_HOST/_cache/clear" > /dev/null

echo ""
echo "✅ Optimization complete!"
echo ""
echo "📈 New Memory Usage:"
sleep 2
docker stats --no-stream --format "table {{.Name}}\t{{.MemUsage}}\t{{.MemPerc}}" | grep -E "NAME|elasticsearch"

echo ""
echo "💡 Additional Recommendations:"
echo "  1. Elasticsearch is now limited to 512MB heap (down from 1GB)"
echo "  2. Log messages are stored but NOT indexed for full-text search (saves memory)"
echo "  3. Use Kibana filters on indexed fields: service, level, log_type, action, user"
echo "  4. If you don't need ELK stack, stop it with: ./dev.sh infra down && docker-compose --profile infra up -d"
echo ""
echo "🚀 To disable ELK completely, edit docker-compose.yml and remove 'logging' profile from services"
