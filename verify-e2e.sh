#!/bin/bash
set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}SableBot E2E Verification Script${NC}"
echo -e "${BLUE}Task: Fix Voice Connection Race Condition${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""

# Function to print test results
pass() {
    echo -e "${GREEN}✓ PASS:${NC} $1"
}

fail() {
    echo -e "${RED}✗ FAIL:${NC} $1"
}

warn() {
    echo -e "${YELLOW}⚠ WARN:${NC} $1"
}

info() {
    echo -e "${BLUE}ℹ INFO:${NC} $1"
}

# Step 1: Check prerequisites
info "Step 1: Checking prerequisites..."
if ! command -v docker &> /dev/null; then
    fail "Docker is not installed or not in PATH"
    exit 1
fi
pass "Docker is installed"

if ! command -v docker compose &> /dev/null; then
    fail "Docker Compose is not available"
    exit 1
fi
pass "Docker Compose is available"

# Step 2: Build verification
info "Step 2: Building project..."
if ./gradlew :modules:sb-module-audio:build --console=plain > /tmp/build.log 2>&1; then
    pass "Project builds successfully"
else
    fail "Project build failed. Check /tmp/build.log"
    exit 1
fi

# Step 3: Check if services are already running
info "Step 3: Checking existing services..."
if docker compose ps | grep -q "sablebot-worker.*Up"; then
    warn "Services are already running. Restarting..."
    docker compose down
    sleep 5
fi

# Step 4: Start the full stack
info "Step 4: Starting full stack (docker compose up -d)..."
if docker compose up -d --build > /tmp/docker-up.log 2>&1; then
    pass "Docker Compose started successfully"
else
    fail "Failed to start Docker Compose. Check /tmp/docker-up.log"
    exit 1
fi

# Step 5: Wait for services to initialize
info "Step 5: Waiting 30 seconds for services to initialize..."
for i in {30..1}; do
    echo -ne "${YELLOW}   Waiting... ${i}s remaining\r${NC}"
    sleep 1
done
echo -e "${GREEN}   Services initialized${NC}                    "

# Step 6: Check service health
info "Step 6: Checking service health..."

# Check if worker is running
if docker compose ps | grep -q "sablebot-worker.*Up"; then
    pass "sablebot-worker is running"
else
    fail "sablebot-worker is not running"
fi

# Check if lavalink is running
if docker compose ps | grep -q "sablebot-lavalink.*Up"; then
    pass "sablebot-lavalink is running"
else
    fail "sablebot-lavalink is not running"
fi

# Step 7: Check worker logs for "Lavalink nodes ready" message
info "Step 7: Checking worker logs for 'Lavalink nodes ready' message..."
sleep 5  # Give a bit more time for logging
if docker compose logs sablebot-worker 2>&1 | grep -q "Lavalink nodes ready"; then
    NODE_MSG=$(docker compose logs sablebot-worker 2>&1 | grep "Lavalink nodes ready" | tail -1)
    pass "Found node readiness message: ${NODE_MSG##*: }"
else
    fail "Did not find 'Lavalink nodes ready' message in worker logs"
    warn "Recent worker logs:"
    docker compose logs --tail=20 sablebot-worker
fi

# Step 8: Check Lavalink logs for "Bad Request" errors
info "Step 8: Checking Lavalink logs for 'Bad Request' errors..."
BAD_REQUEST_COUNT=$(docker compose logs lavalink 2>&1 | grep -c "Bad Request" || echo "0")
if [ "$BAD_REQUEST_COUNT" -eq 0 ]; then
    pass "Zero 'Bad Request' errors in Lavalink logs"
else
    fail "Found $BAD_REQUEST_COUNT 'Bad Request' error(s) in Lavalink logs"
    warn "Check logs: docker compose logs lavalink | grep 'Bad Request'"
fi

# Step 9: Check metrics endpoint (worker runs on port 9090)
info "Step 9: Checking metrics endpoint..."
if curl -s http://localhost:9090/actuator/health > /dev/null 2>&1; then
    pass "Metrics endpoint is accessible"

    # Try to check for lavalink.ready metric
    if curl -s http://localhost:9090/actuator/prometheus 2>&1 | grep -q "sablebot_audio_lavalink_ready"; then
        READY_VALUE=$(curl -s http://localhost:9090/actuator/prometheus 2>&1 | grep "sablebot_audio_lavalink_ready" | grep -v "#" | awk '{print $2}')
        if [ "$READY_VALUE" == "1.0" ]; then
            pass "sablebot.audio.lavalink.ready metric = 1.0 (ready)"
        else
            warn "sablebot.audio.lavalink.ready metric = $READY_VALUE (expected 1.0)"
        fi
    else
        warn "Could not find sablebot.audio.lavalink.ready metric"
    fi
else
    warn "Metrics endpoint not accessible (this is expected if worker failed to start)"
fi

# Step 10: Check for initialization errors
info "Step 10: Checking for initialization errors..."
if docker compose logs sablebot-worker 2>&1 | grep -i "error\|exception" | grep -v "DEBUG" > /tmp/worker-errors.log; then
    ERROR_COUNT=$(wc -l < /tmp/worker-errors.log)
    if [ "$ERROR_COUNT" -gt 0 ]; then
        warn "Found $ERROR_COUNT error/exception lines in worker logs"
        warn "Check /tmp/worker-errors.log for details"
    fi
else
    pass "No obvious errors in worker logs"
fi

echo ""
echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}Automated Verification Complete${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""

# Manual testing instructions
echo -e "${YELLOW}========================================${NC}"
echo -e "${YELLOW}MANUAL TESTING REQUIRED${NC}"
echo -e "${YELLOW}========================================${NC}"
echo ""
echo "The following steps require manual interaction with Discord:"
echo ""
echo "1. Join a Discord voice channel in a server where the bot is present"
echo ""
echo "2. Test basic /play command:"
echo "   - Execute: /play [song name or URL]"
echo "   - Expected: Bot joins voice channel and starts playback within 2s"
echo "   - Expected: No 'Bad Request' errors in Lavalink logs"
echo ""
echo "3. Test rapid-fire commands:"
echo "   - Execute /play [song1]"
echo "   - Immediately execute /play [song2]"
echo "   - Expected: Both songs queued, no connection errors"
echo ""
echo "4. Test Lavalink restart recovery:"
echo "   - While music is playing: docker compose restart lavalink"
echo "   - Wait 10 seconds"
echo "   - Execute /play [song]"
echo "   - Expected: Graceful error during restart, successful connection after recovery"
echo ""
echo "5. Test existing commands:"
echo "   - /pause - should pause playback"
echo "   - /skip - should skip to next track"
echo "   - /stop - should stop playback and leave voice"
echo "   - /queue - should show current queue"
echo ""
echo "6. Check Grafana metrics (optional):"
echo "   - Open http://localhost:3001 (admin/admin)"
echo "   - Navigate to metrics dashboard"
echo "   - Search for 'sablebot.audio'"
echo "   - Verify: lavalink.ready=1, connection.attempts incrementing"
echo ""
echo -e "${YELLOW}========================================${NC}"
echo ""

# Utility commands
echo "Useful commands for debugging:"
echo "  View worker logs:    docker compose logs -f sablebot-worker"
echo "  View lavalink logs:  docker compose logs -f lavalink"
echo "  Stop services:       docker compose down"
echo "  Restart services:    docker compose restart"
echo ""
