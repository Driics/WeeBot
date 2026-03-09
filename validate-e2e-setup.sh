#!/bin/bash
# Validation script for Reaction Roles E2E Testing Setup
# Run this script before performing E2E tests

set -e

echo "=================================="
echo "Reaction Roles E2E Setup Validator"
echo "=================================="
echo ""

# Color codes for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

pass() {
    echo -e "${GREEN}✓${NC} $1"
}

fail() {
    echo -e "${RED}✗${NC} $1"
    exit 1
}

warn() {
    echo -e "${YELLOW}⚠${NC} $1"
}

info() {
    echo "ℹ $1"
}

echo "Step 1: Checking build status..."
if ./gradlew build -x test > /dev/null 2>&1; then
    pass "All Gradle modules compile successfully"
else
    fail "Gradle build failed. Run './gradlew build' to see errors."
fi

echo ""
echo "Step 2: Checking Docker availability..."
if command -v docker &> /dev/null && command -v docker-compose &> /dev/null; then
    pass "Docker and docker-compose are installed"
else
    fail "Docker or docker-compose not found. Please install Docker Desktop."
fi

echo ""
echo "Step 3: Checking Docker daemon..."
if docker info > /dev/null 2>&1; then
    pass "Docker daemon is running"
else
    fail "Docker daemon is not running. Please start Docker Desktop."
fi

echo ""
echo "Step 4: Checking environment variables..."
if [ -f .env ]; then
    pass ".env file exists"

    # Check required variables
    if grep -q "DISCORD_TOKEN=" .env; then
        pass "DISCORD_TOKEN is set"
    else
        warn "DISCORD_TOKEN not found in .env (required for bot)"
    fi

    if grep -q "DISCORD_CLIENT_ID=" .env; then
        pass "DISCORD_CLIENT_ID is set"
    else
        warn "DISCORD_CLIENT_ID not found in .env (required for OAuth2)"
    fi

    if grep -q "DISCORD_CLIENT_SECRET=" .env; then
        pass "DISCORD_CLIENT_SECRET is set"
    else
        warn "DISCORD_CLIENT_SECRET not found in .env (required for OAuth2)"
    fi
else
    warn ".env file not found. Copy .env.example to .env and fill in values."
fi

echo ""
echo "Step 5: Checking database migrations..."
if [ -f "sb-common/src/main/resources/db/changelog-8.3-08032026.xml" ]; then
    pass "Reaction roles migration file exists"
else
    fail "Migration file not found"
fi

echo ""
echo "Step 6: Checking service configuration..."
if [ -f "docker-compose.yml" ]; then
    pass "docker-compose.yml exists"
else
    fail "docker-compose.yml not found"
fi

echo ""
echo "Step 7: Checking service status..."
if docker-compose ps > /dev/null 2>&1; then
    POSTGRES_STATUS=$(docker-compose ps postgres | grep -c "Up" || echo "0")
    KAFKA_STATUS=$(docker-compose ps kafka | grep -c "Up" || echo "0")
    WORKER_STATUS=$(docker-compose ps sablebot-worker | grep -c "Up" || echo "0")
    API_STATUS=$(docker-compose ps sablebot-api | grep -c "Up" || echo "0")
    DASHBOARD_STATUS=$(docker-compose ps sablebot-dashboard | grep -c "Up" || echo "0")

    if [ "$POSTGRES_STATUS" -eq "1" ]; then
        pass "PostgreSQL is running"
    else
        warn "PostgreSQL is not running. Start with: docker-compose up -d postgres"
    fi

    if [ "$KAFKA_STATUS" -eq "1" ]; then
        pass "Kafka is running"
    else
        warn "Kafka is not running. Start with: docker-compose up -d kafka"
    fi

    if [ "$WORKER_STATUS" -eq "1" ]; then
        pass "sb-worker is running"
    else
        warn "sb-worker is not running. Start with: docker-compose up -d sablebot-worker"
    fi

    if [ "$API_STATUS" -eq "1" ]; then
        pass "sb-api is running"
    else
        warn "sb-api is not running. Start with: docker-compose up -d sablebot-api"
    fi

    if [ "$DASHBOARD_STATUS" -eq "1" ]; then
        pass "sb-dashboard is running"
    else
        warn "sb-dashboard is not running. Start with: docker-compose up -d sablebot-dashboard"
    fi
else
    info "No services running yet. This is normal for first-time setup."
fi

echo ""
echo "Step 8: Checking database tables..."
if docker-compose ps postgres | grep -q "Up"; then
    info "Checking for reaction role tables..."

    if docker-compose exec -T postgres psql -U sablebot -d sablebot -c "\dt reaction_role*" 2>/dev/null | grep -q "reaction_role_menu"; then
        pass "reaction_role_menu table exists"
        pass "reaction_role_menu_item table exists"
        pass "reaction_role_group table exists"
    else
        warn "Reaction role tables not found. They will be created on first worker startup."
    fi
else
    info "PostgreSQL not running, skipping table check"
fi

echo ""
echo "Step 9: Checking dashboard build..."
if [ -d "sb-dashboard/node_modules" ]; then
    pass "Dashboard dependencies installed"
else
    warn "Dashboard dependencies not installed. Run: cd sb-dashboard && npm install"
fi

if [ -f "sb-dashboard/.next/BUILD_ID" ]; then
    pass "Dashboard has been built"
else
    warn "Dashboard not built yet. Run: cd sb-dashboard && npm run build"
fi

echo ""
echo "=================================="
echo "Validation Summary"
echo "=================================="
echo ""
echo "Next steps for E2E testing:"
echo ""
echo "1. Start all services:"
echo "   $ docker-compose up -d"
echo ""
echo "2. Wait for services to be healthy (check with 'docker-compose ps')"
echo ""
echo "3. Open dashboard:"
echo "   http://localhost:3000"
echo ""
echo "4. Follow the E2E test procedure in E2E_TEST_PROCEDURE.md"
echo ""
echo "5. Check logs if issues occur:"
echo "   $ docker-compose logs -f sablebot-worker"
echo "   $ docker-compose logs -f sablebot-api"
echo ""
echo "=================================="
