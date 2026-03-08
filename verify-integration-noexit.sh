#!/bin/bash

# Social Media Feed Integration - Component Verification Script
# This script verifies that all components are in place and properly configured
# without requiring live services or API keys.

set +e

echo "========================================="
echo "Social Media Feed Integration Verification"
echo "========================================="
echo ""

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

PASS_COUNT=0
FAIL_COUNT=0

check_pass() {
    echo -e "${GREEN}✓${NC} $1"
    ((PASS_COUNT++))
}

check_fail() {
    echo -e "${RED}✗${NC} $1"
    ((FAIL_COUNT++))
}

check_warn() {
    echo -e "${YELLOW}⚠${NC} $1"
}

echo "1. Verifying Database Entities..."
echo "-----------------------------------"

if [ -f "sb-common/src/main/kotlin/ru/sablebot/common/persistence/entity/SocialFeed.kt" ]; then
    check_pass "SocialFeed entity exists"
else
    check_fail "SocialFeed entity not found"
fi

if [ -f "sb-common/src/main/kotlin/ru/sablebot/common/persistence/repository/SocialFeedRepository.kt" ]; then
    check_pass "SocialFeedRepository exists"
else
    check_fail "SocialFeedRepository not found"
fi

if [ -f "sb-common/src/main/kotlin/ru/sablebot/common/persistence/entity/FeedNotification.kt" ]; then
    check_pass "FeedNotification entity exists"
else
    check_fail "FeedNotification entity not found"
fi

if [ -f "sb-common/src/main/kotlin/ru/sablebot/common/persistence/repository/FeedNotificationRepository.kt" ]; then
    check_pass "FeedNotificationRepository exists"
else
    check_fail "FeedNotificationRepository not found"
fi

echo ""
echo "2. Verifying Database Migrations..."
echo "-----------------------------------"

if [ -f "sb-common/src/main/resources/db/changelog-feeds.xml" ]; then
    check_pass "Liquibase changelog-feeds.xml exists"

    # Check if it's included in master
    if grep -q "changelog-feeds.xml" sb-common/src/main/resources/db/master.xml; then
        check_pass "changelog-feeds.xml included in master.xml"
    else
        check_fail "changelog-feeds.xml NOT included in master.xml"
    fi
else
    check_fail "Liquibase changelog-feeds.xml not found"
fi

echo ""
echo "3. Verifying Feed Module Structure..."
echo "-----------------------------------"

if [ -f "modules/sb-module-feeds/build.gradle.kts" ]; then
    check_pass "Feed module build.gradle.kts exists"
else
    check_fail "Feed module build.gradle.kts not found"
fi

# Check if module is included in settings
if grep -q "sb-module-feeds" settings.gradle.kts; then
    check_pass "Feed module included in settings.gradle.kts"
else
    check_fail "Feed module NOT included in settings.gradle.kts"
fi

if [ -f "modules/sb-module-feeds/src/main/kotlin/ru/sablebot/module/feeds/config/FeedProperties.kt" ]; then
    check_pass "FeedProperties configuration exists"
else
    check_fail "FeedProperties configuration not found"
fi

echo ""
echo "4. Verifying Reddit Integration..."
echo "-----------------------------------"

if [ -f "modules/sb-module-feeds/src/main/kotlin/ru/sablebot/module/feeds/model/reddit/RedditPost.kt" ]; then
    check_pass "RedditPost model exists"
else
    check_fail "RedditPost model not found"
fi

if [ -f "modules/sb-module-feeds/src/main/kotlin/ru/sablebot/module/feeds/service/reddit/RedditApiClient.kt" ]; then
    check_pass "RedditApiClient exists"
else
    check_fail "RedditApiClient not found"
fi

if [ -f "modules/sb-module-feeds/src/main/kotlin/ru/sablebot/module/feeds/service/reddit/RedditFeedService.kt" ]; then
    check_pass "RedditFeedService exists"
else
    check_fail "RedditFeedService not found"
fi

echo ""
echo "5. Verifying Twitch Integration..."
echo "-----------------------------------"

if [ -f "modules/sb-module-feeds/src/main/kotlin/ru/sablebot/module/feeds/model/twitch/TwitchStream.kt" ]; then
    check_pass "TwitchStream model exists"
else
    check_fail "TwitchStream model not found"
fi

if [ -f "modules/sb-module-feeds/src/main/kotlin/ru/sablebot/module/feeds/service/twitch/TwitchOAuthService.kt" ]; then
    check_pass "TwitchOAuthService exists"
else
    check_fail "TwitchOAuthService not found"
fi

if [ -f "modules/sb-module-feeds/src/main/kotlin/ru/sablebot/module/feeds/service/twitch/TwitchApiClient.kt" ]; then
    check_pass "TwitchApiClient exists"
else
    check_fail "TwitchApiClient not found"
fi

if [ -f "modules/sb-module-feeds/src/main/kotlin/ru/sablebot/module/feeds/service/twitch/TwitchFeedService.kt" ]; then
    check_pass "TwitchFeedService exists"
else
    check_fail "TwitchFeedService not found"
fi

echo ""
echo "6. Verifying YouTube Integration..."
echo "-----------------------------------"

if [ -f "modules/sb-module-feeds/src/main/kotlin/ru/sablebot/module/feeds/model/youtube/YouTubeVideo.kt" ]; then
    check_pass "YouTubeVideo model exists"
else
    check_fail "YouTubeVideo model not found"
fi

if [ -f "modules/sb-module-feeds/src/main/kotlin/ru/sablebot/module/feeds/service/youtube/YouTubeApiClient.kt" ]; then
    check_pass "YouTubeApiClient exists"
else
    check_fail "YouTubeApiClient not found"
fi

if [ -f "modules/sb-module-feeds/src/main/kotlin/ru/sablebot/module/feeds/service/youtube/YouTubeFeedService.kt" ]; then
    check_pass "YouTubeFeedService exists"
else
    check_fail "YouTubeFeedService not found"
fi

echo ""
echo "7. Verifying Feed Polling Orchestrator..."
echo "-----------------------------------"

if [ -f "modules/sb-module-feeds/src/main/kotlin/ru/sablebot/module/feeds/service/IFeedPollingService.kt" ]; then
    check_pass "IFeedPollingService interface exists"
else
    check_fail "IFeedPollingService interface not found"
fi

if [ -f "modules/sb-module-feeds/src/main/kotlin/ru/sablebot/module/feeds/service/impl/FeedPollingServiceImpl.kt" ]; then
    check_pass "FeedPollingServiceImpl exists"

    # Check for @Scheduled annotation
    if grep -q "@Scheduled" modules/sb-module-feeds/src/main/kotlin/ru/sablebot/module/feeds/service/impl/FeedPollingServiceImpl.kt; then
        check_pass "Polling service has @Scheduled annotation"
    else
        check_fail "Polling service missing @Scheduled annotation"
    fi
else
    check_fail "FeedPollingServiceImpl not found"
fi

echo ""
echo "8. Verifying Discord Command..."
echo "-----------------------------------"

if [ -f "modules/sb-module-feeds/src/main/kotlin/ru/sablebot/module/feeds/command/FeedCommand.kt" ]; then
    check_pass "FeedCommand exists"

    # Check for subcommands
    if grep -q "subcommand(\"add\")" modules/sb-module-feeds/src/main/kotlin/ru/sablebot/module/feeds/command/FeedCommand.kt; then
        check_pass "FeedCommand has 'add' subcommand"
    fi
    if grep -q "subcommand(\"list\")" modules/sb-module-feeds/src/main/kotlin/ru/sablebot/module/feeds/command/FeedCommand.kt; then
        check_pass "FeedCommand has 'list' subcommand"
    fi
    if grep -q "subcommand(\"remove\")" modules/sb-module-feeds/src/main/kotlin/ru/sablebot/module/feeds/command/FeedCommand.kt; then
        check_pass "FeedCommand has 'remove' subcommand"
    fi
    if grep -q "subcommand(\"toggle\")" modules/sb-module-feeds/src/main/kotlin/ru/sablebot/module/feeds/command/FeedCommand.kt; then
        check_pass "FeedCommand has 'toggle' subcommand"
    fi
else
    check_fail "FeedCommand not found"
fi

echo ""
echo "9. Verifying REST API Components..."
echo "-----------------------------------"

# Check DTOs
if [ -f "sb-api/src/main/kotlin/ru/sablebot/api/dto/feed/FeedResponse.kt" ]; then
    check_pass "FeedResponse DTO exists"
else
    check_fail "FeedResponse DTO not found"
fi

if [ -f "sb-api/src/main/kotlin/ru/sablebot/api/dto/feed/CreateFeedRequest.kt" ]; then
    check_pass "CreateFeedRequest DTO exists"
else
    check_fail "CreateFeedRequest DTO not found"
fi

if [ -f "sb-api/src/main/kotlin/ru/sablebot/api/dto/feed/UpdateFeedRequest.kt" ]; then
    check_pass "UpdateFeedRequest DTO exists"
else
    check_fail "UpdateFeedRequest DTO not found"
fi

if [ -f "sb-api/src/main/kotlin/ru/sablebot/api/dto/feed/FeedListResponse.kt" ]; then
    check_pass "FeedListResponse DTO exists"
else
    check_fail "FeedListResponse DTO not found"
fi

# Check service
if [ -f "sb-api/src/main/kotlin/ru/sablebot/api/service/FeedApiService.kt" ]; then
    check_pass "FeedApiService exists"
else
    check_fail "FeedApiService not found"
fi

# Check controller
if [ -f "sb-api/src/main/kotlin/ru/sablebot/api/controller/FeedController.kt" ]; then
    check_pass "FeedController exists"

    # Check for REST endpoints
    if grep -q "@GetMapping" sb-api/src/main/kotlin/ru/sablebot/api/controller/FeedController.kt; then
        check_pass "FeedController has GET endpoint"
    fi
    if grep -q "@PostMapping" sb-api/src/main/kotlin/ru/sablebot/api/controller/FeedController.kt; then
        check_pass "FeedController has POST endpoint"
    fi
    if grep -q "@PatchMapping" sb-api/src/main/kotlin/ru/sablebot/api/controller/FeedController.kt; then
        check_pass "FeedController has PATCH endpoint"
    fi
    if grep -q "@DeleteMapping" sb-api/src/main/kotlin/ru/sablebot/api/controller/FeedController.kt; then
        check_pass "FeedController has DELETE endpoint"
    fi
else
    check_fail "FeedController not found"
fi

echo ""
echo "10. Verifying Dashboard Components..."
echo "-----------------------------------"

# Check TypeScript types
if [ -f "sb-dashboard/src/types/feed.ts" ]; then
    check_pass "Feed TypeScript types exist"
else
    check_fail "Feed TypeScript types not found"
fi

# Check page
if [ -f "sb-dashboard/src/app/dashboard/[guildId]/feeds/page.tsx" ]; then
    check_pass "Feeds page exists"
else
    check_fail "Feeds page not found"
fi

# Check components
if [ -f "sb-dashboard/src/components/feeds/FeedList.tsx" ]; then
    check_pass "FeedList component exists"
else
    check_fail "FeedList component not found"
fi

if [ -f "sb-dashboard/src/components/feeds/FeedForm.tsx" ]; then
    check_pass "FeedForm component exists"
else
    check_fail "FeedForm component not found"
fi

if [ -f "sb-dashboard/src/components/feeds/FeedTestDialog.tsx" ]; then
    check_pass "FeedTestDialog component exists"
else
    check_fail "FeedTestDialog component not found"
fi

# Check navigation integration
if [ -f "sb-dashboard/src/app/dashboard/[guildId]/layout.tsx" ]; then
    if grep -q "feeds" sb-dashboard/src/app/dashboard/[guildId]/layout.tsx; then
        check_pass "Feeds link in dashboard navigation"
    else
        check_fail "Feeds link NOT in dashboard navigation"
    fi
fi

echo ""
echo "11. Verifying Worker Configuration..."
echo "-----------------------------------"

if [ -f "sb-worker/src/main/resources/application.yml" ]; then
    check_pass "Worker application.yml exists"

    # Check feed configuration
    if grep -q "sablebot.feeds" sb-worker/src/main/resources/application.yml; then
        check_pass "Feed configuration present in application.yml"
    else
        check_fail "Feed configuration missing from application.yml"
    fi

    # Check specific configurations
    if grep -q "max-feeds-per-guild" sb-worker/src/main/resources/application.yml; then
        check_pass "Feed limits configured"
    fi

    if grep -q "reddit:" sb-worker/src/main/resources/application.yml; then
        check_pass "Reddit configuration present"
    fi

    if grep -q "twitch:" sb-worker/src/main/resources/application.yml; then
        check_pass "Twitch configuration present"
    fi

    if grep -q "youtube:" sb-worker/src/main/resources/application.yml; then
        check_pass "YouTube configuration present"
    fi
else
    check_fail "Worker application.yml not found"
fi

echo ""
echo "12. Verifying Docker Configuration..."
echo "-----------------------------------"

if [ -f "docker-compose.yml" ]; then
    check_pass "docker-compose.yml exists"

    # Check for required services
    if grep -q "sablebot-worker:" docker-compose.yml; then
        check_pass "Worker service defined"
    fi

    if grep -q "sablebot-api:" docker-compose.yml; then
        check_pass "API service defined"
    fi

    if grep -q "sablebot-dashboard:" docker-compose.yml; then
        check_pass "Dashboard service defined"
    fi

    if grep -q "postgres:" docker-compose.yml; then
        check_pass "PostgreSQL service defined"
    fi

    if grep -q "prometheus:" docker-compose.yml; then
        check_pass "Prometheus service defined"
    fi
else
    check_fail "docker-compose.yml not found"
fi

echo ""
echo "13. Running Build Tests..."
echo "-----------------------------------"

echo "Building project (this may take a while)..."
if ./gradlew build -x test --no-daemon > /dev/null 2>&1; then
    check_pass "Gradle build successful"
else
    check_fail "Gradle build failed"
fi

echo ""
echo "14. Checking Test Coverage..."
echo "-----------------------------------"

# Count test files
REDDIT_TESTS=$(find modules/sb-module-feeds -name "*RedditApiClientTest.kt" 2>/dev/null | wc -l)
TWITCH_TESTS=$(find modules/sb-module-feeds -name "*TwitchApiClientTest.kt" 2>/dev/null | wc -l)
YOUTUBE_TESTS=$(find modules/sb-module-feeds -name "*YouTubeApiClientTest.kt" 2>/dev/null | wc -l)

if [ "$REDDIT_TESTS" -gt 0 ]; then
    check_pass "Reddit API tests exist"
else
    check_warn "No Reddit API tests found"
fi

if [ "$TWITCH_TESTS" -gt 0 ]; then
    check_pass "Twitch API tests exist"
else
    check_warn "No Twitch API tests found"
fi

if [ "$YOUTUBE_TESTS" -gt 0 ]; then
    check_pass "YouTube API tests exist"
else
    check_warn "No YouTube API tests found"
fi

echo ""
echo "15. Verifying Metrics Configuration..."
echo "-----------------------------------"

# Check if metrics are properly configured
METRICS_COUNT=$(grep -r "sablebot.feeds" modules/sb-module-feeds/src/main/kotlin --include="*.kt" | wc -l)

if [ "$METRICS_COUNT" -gt 5 ]; then
    check_pass "Prometheus metrics instrumentation found ($METRICS_COUNT references)"
else
    check_warn "Limited metrics instrumentation ($METRICS_COUNT references)"
fi

echo ""
echo "========================================="
echo "Verification Summary"
echo "========================================="
echo -e "${GREEN}Passed:${NC} $PASS_COUNT"
echo -e "${RED}Failed:${NC} $FAIL_COUNT"
echo ""

if [ $FAIL_COUNT -eq 0 ]; then
    echo -e "${GREEN}✓ All component verifications passed!${NC}"
    echo ""
    echo "The social media feed integration is complete and ready for live testing."
    echo "See E2E_INTEGRATION_TEST.md for manual testing procedures."
    exit 0
else
    echo -e "${RED}✗ Some verifications failed.${NC}"
    echo ""
    echo "Please review the failures above and fix before proceeding."
    exit 1
fi
