#!/bin/bash

# Script to propagate log classification changes to all services
# This ensures consistent logging across the entire platform

set -e

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$PROJECT_ROOT"

SERVICES=(
    "user-service"
    "job-service"
    "cv-service"
    "application-service"
    "notification-service"
    "monitoring-service"
)

echo "🔄 Propagating log classification files to all services..."
echo ""

# 1. Copy LogType enum to all services
echo "📝 Step 1: Copying LogType enum..."
for service in "${SERVICES[@]}"; do
    target_dir="services/${service}/src/main/java/org/workfitai/${service//-/}/constants"
    mkdir -p "$target_dir"
    
    # Copy and update package name
    sed "s/package org.workfitai.authservice/package org.workfitai.${service//-/}/g" \
        services/auth-service/src/main/java/org/workfitai/authservice/constants/LogType.java \
        > "${target_dir}/LogType.java"
    
    echo "   ✅ ${service}/constants/LogType.java"
done

# 2. Copy LogContext utility to all services
echo ""
echo "📝 Step 2: Copying LogContext utility..."
for service in "${SERVICES[@]}"; do
    target_dir="services/${service}/src/main/java/org/workfitai/${service//-/}/util"
    mkdir -p "$target_dir"
    
    # Copy and update package/import names
    sed -e "s/package org.workfitai.authservice/package org.workfitai.${service//-/}/g" \
        -e "s/import org.workfitai.authservice/import org.workfitai.${service//-/}/g" \
        services/auth-service/src/main/java/org/workfitai/authservice/util/LogContext.java \
        > "${target_dir}/LogContext.java"
    
    echo "   ✅ ${service}/util/LogContext.java"
done

# 3. Update LoggingMdcFilter in all services
echo ""
echo "📝 Step 3: Updating LoggingMdcFilter in all services..."
for service in "${SERVICES[@]}"; do
    filter_file="services/${service}/src/main/java/org/workfitai/${service//-/}/config/LoggingMdcFilter.java"
    
    if [ -f "$filter_file" ]; then
        # Update imports and logic
        sed -e "s/package org.workfitai.authservice/package org.workfitai.${service//-/}/g" \
            -e "s/import org.workfitai.authservice/import org.workfitai.${service//-/}/g" \
            services/auth-service/src/main/java/org/workfitai/authservice/config/LoggingMdcFilter.java \
            > "$filter_file"
        
        echo "   ✅ ${service}/config/LoggingMdcFilter.java"
    else
        echo "   ⚠️  ${service}/config/LoggingMdcFilter.java NOT FOUND"
    fi
done

# 4. Update logback-spring.xml in all services
echo ""
echo "📝 Step 4: Updating logback-spring.xml in all services..."
for service in "${SERVICES[@]}"; do
    logback_file="services/${service}/src/main/resources/logback-spring.xml"
    
    if [ -f "$logback_file" ]; then
        # Check if log_type is already included
        if ! grep -q "includeMdcKeyName>log_type" "$logback_file"; then
            # Add log classification fields after roles line
            sed -i.bak '/<includeMdcKeyName>roles<\/includeMdcKeyName>/a\
            \
            <!-- Log classification fields -->\
            <includeMdcKeyName>log_type<\/includeMdcKeyName>\
            <includeMdcKeyName>action<\/includeMdcKeyName>\
            <includeMdcKeyName>entity_type<\/includeMdcKeyName>\
            <includeMdcKeyName>entity_id<\/includeMdcKeyName>
' "$logback_file"
            rm -f "${logback_file}.bak"
            echo "   ✅ ${service}/logback-spring.xml (updated)"
        else
            echo "   ✅ ${service}/logback-spring.xml (already up-to-date)"
        fi
    else
        echo "   ⚠️  ${service}/logback-spring.xml NOT FOUND"
    fi
done

echo ""
echo "✨ Done! Log classification files propagated to all services."
echo ""
echo "📋 Next steps:"
echo "   1. Review changes in each service"
echo "   2. Restart services: ./dev.sh full restart"
echo "   3. Test log filtering: curl http://localhost:9086/api/logs/activity"
echo ""
