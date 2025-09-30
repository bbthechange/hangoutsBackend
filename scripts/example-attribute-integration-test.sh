#!/bin/bash

# Image Path Feature Test Script
# Tests all image path functionality with a single execution

# set -e disabled to see all test results

BASE_URL="http://localhost:8080"
PHONE="+19285251044"
PASSWORD="mypass2"

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo "=========================================="
echo "Image Path Feature Test Suite"
echo "=========================================="
echo ""

# Test counter
TESTS_PASSED=0
TESTS_FAILED=0

# Function to print test result
pass_test() {
    echo -e "${GREEN}✓ PASS${NC}: $1"
    ((TESTS_PASSED++))
}

fail_test() {
    echo -e "${RED}✗ FAIL${NC}: $1"
    echo -e "${RED}  Response: $2${NC}"
    ((TESTS_FAILED++))
}

info() {
    echo -e "${YELLOW}ℹ${NC} $1"
}

# 1. Login to get JWT token
echo "1. Authenticating..."
LOGIN_RESPONSE=$(curl -s -X POST "$BASE_URL/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"phoneNumber\":\"$PHONE\",\"password\":\"$PASSWORD\"}")

JWT_TOKEN=$(echo "$LOGIN_RESPONSE" | grep -o '"accessToken":"[^"]*' | sed 's/"accessToken":"//')

if [ -z "$JWT_TOKEN" ]; then
    echo -e "${RED}Failed to extract JWT token${NC}"
    echo "Response: $LOGIN_RESPONSE"
    exit 1
fi

pass_test "Authentication successful"
info "JWT Token obtained: ${JWT_TOKEN:0:20}..."
echo ""

# 2. Get current profile
echo "2. Getting current profile..."
PROFILE_RESPONSE=$(curl -s "$BASE_URL/profile" \
  -H "Authorization: Bearer $JWT_TOKEN")

if echo "$PROFILE_RESPONSE" | grep -q "\"id\""; then
    pass_test "Get profile"
else
    fail_test "Get profile" "$PROFILE_RESPONSE"
fi
echo ""

# 3. Update profile with mainImagePath
echo "3. Updating profile with mainImagePath..."
UPDATE_PROFILE_RESPONSE=$(curl -s -X PUT "$BASE_URL/profile" \
  -H "Authorization: Bearer $JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "displayName": "Jeana Test Updated",
    "mainImagePath": "users/test-user-avatar.jpg"
  }')

if echo "$UPDATE_PROFILE_RESPONSE" | grep -q "users/test-user-avatar.jpg"; then
    pass_test "Update profile with mainImagePath"
else
    fail_test "Update profile with mainImagePath" "$UPDATE_PROFILE_RESPONSE"
fi
echo ""

# 4. Verify profile update
echo "4. Verifying profile update..."
PROFILE_CHECK=$(curl -s "$BASE_URL/profile" \
  -H "Authorization: Bearer $JWT_TOKEN")

if echo "$PROFILE_CHECK" | grep -q "users/test-user-avatar.jpg"; then
    pass_test "Profile mainImagePath persisted"
else
    fail_test "Profile mainImagePath persisted" "$PROFILE_CHECK"
fi
echo ""

# 5. Create group with image paths
echo "5. Creating group with image paths..."
CREATE_GROUP_RESPONSE=$(curl -s -X POST "$BASE_URL/groups" \
  -H "Authorization: Bearer $JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "groupName": "Test Group with Images",
    "public": false,
    "mainImagePath": "groups/test-group-avatar.jpg",
    "backgroundImagePath": "groups/test-group-background.jpg"
  }')

GROUP_ID=$(echo "$CREATE_GROUP_RESPONSE" | grep -o '"groupId":"[^"]*' | sed 's/"groupId":"//')

if [ -n "$GROUP_ID" ] && echo "$CREATE_GROUP_RESPONSE" | grep -q "groups/test-group-avatar.jpg"; then
    pass_test "Create group with image paths"
    info "Group ID: $GROUP_ID"
else
    fail_test "Create group with image paths" "$CREATE_GROUP_RESPONSE"
fi
echo ""

# 6. Verify group image paths
echo "6. Verifying group image paths..."
if [ -z "$GROUP_ID" ]; then
    fail_test "Group image paths persisted" "GROUP_ID is empty, skipping test"
else
    GROUP_DETAIL=$(curl -s "$BASE_URL/groups/$GROUP_ID" \
      -H "Authorization: Bearer $JWT_TOKEN")

    if echo "$GROUP_DETAIL" | grep -q "groups/test-group-avatar.jpg" && \
       echo "$GROUP_DETAIL" | grep -q "groups/test-group-background.jpg"; then
        pass_test "Group image paths persisted"
    else
        fail_test "Group image paths persisted" "$GROUP_DETAIL"
    fi
fi
echo ""

# 7. Verify user image denormalization to GroupMembership
echo "7. Verifying user mainImagePath denormalization to GroupMembership..."
USER_GROUPS=$(curl -s "$BASE_URL/groups" \
  -H "Authorization: Bearer $JWT_TOKEN")

if echo "$USER_GROUPS" | grep -q "userMainImagePath.*users/test-user-avatar.jpg"; then
    pass_test "User mainImagePath denormalized to GroupMembership"
else
    fail_test "User mainImagePath denormalized to GroupMembership" "$USER_GROUPS"
fi
echo ""

# 8. Update group image paths
echo "8. Updating group image paths..."
if [ -z "$GROUP_ID" ]; then
    fail_test "Update group image paths" "GROUP_ID is empty, skipping test"
else
    UPDATE_GROUP_RESPONSE=$(curl -s -X PATCH "$BASE_URL/groups/$GROUP_ID" \
      -H "Authorization: Bearer $JWT_TOKEN" \
      -H "Content-Type: application/json" \
      -d '{
        "mainImagePath": "groups/updated-avatar.jpg",
        "backgroundImagePath": "groups/updated-background.jpg"
      }')

    if echo "$UPDATE_GROUP_RESPONSE" | grep -q "groups/updated-avatar.jpg" && \
       echo "$UPDATE_GROUP_RESPONSE" | grep -q "groups/updated-background.jpg"; then
        pass_test "Update group image paths"
    else
        fail_test "Update group image paths" "$UPDATE_GROUP_RESPONSE"
    fi
fi
echo ""

# 9. Verify group image denormalization to GroupMembership
echo "9. Verifying group image denormalization to GroupMembership..."
if [ -z "$GROUP_ID" ]; then
    fail_test "Group image paths denormalized to GroupMembership" "GROUP_ID is empty, skipping test"
else
    sleep 1  # Give DynamoDB a moment to process
    USER_GROUPS_UPDATED=$(curl -s "$BASE_URL/groups" \
      -H "Authorization: Bearer $JWT_TOKEN")

    # Check specifically for the test group we just created/updated
    TEST_GROUP_DATA=$(echo "$USER_GROUPS_UPDATED" | grep -o "{\"groupId\":\"$GROUP_ID\"[^}]*}")

    if echo "$TEST_GROUP_DATA" | grep -q "mainImagePath.*groups/updated-avatar.jpg" && \
       echo "$TEST_GROUP_DATA" | grep -q "backgroundImagePath.*groups/updated-background.jpg" && \
       echo "$TEST_GROUP_DATA" | grep -q "userMainImagePath.*users/test-user-avatar.jpg"; then
        pass_test "Group image paths denormalized to GroupMembership"
    else
        fail_test "Group image paths denormalized to GroupMembership" "Test group: $TEST_GROUP_DATA"
    fi
fi
echo ""

# 10. Create hangout with mainImagePath
echo "10. Creating hangout with mainImagePath..."
# Calculate tomorrow at 3pm
TOMORROW=$(date -u -v+1d '+%Y-%m-%dT15:00:00Z' 2>/dev/null || date -u -d 'tomorrow 15:00:00' '+%Y-%m-%dT%H:%M:%SZ' 2>/dev/null || echo "2025-10-01T15:00:00Z")
CREATE_HANGOUT_RESPONSE=$(curl -s -X POST "$BASE_URL/hangouts" \
  -H "Authorization: Bearer $JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -d "{
    \"title\": \"Test Hangout with Image\",
    \"description\": \"Testing image path functionality\",
    \"timeInfo\": {
      \"startTime\": \"$TOMORROW\",
      \"periodGranularity\": \"day\"
    },
    \"associatedGroups\": [\"$GROUP_ID\"],
    \"mainImagePath\": \"events/test-hangout-image.jpg\"
  }")

HANGOUT_ID=$(echo "$CREATE_HANGOUT_RESPONSE" | grep -o '"hangoutId":"[^"]*' | sed 's/"hangoutId":"//')

if [ -n "$HANGOUT_ID" ] && echo "$CREATE_HANGOUT_RESPONSE" | grep -q "events/test-hangout-image.jpg"; then
    pass_test "Create hangout with mainImagePath"
    info "Hangout ID: $HANGOUT_ID"
else
    fail_test "Create hangout with mainImagePath" "$CREATE_HANGOUT_RESPONSE"
fi
echo ""

# 11. Verify hangout image
echo "11. Verifying hangout mainImagePath..."
if [ -z "$HANGOUT_ID" ]; then
    fail_test "Hangout mainImagePath persisted" "HANGOUT_ID is empty, skipping test"
else
    HANGOUT_DETAIL=$(curl -s "$BASE_URL/hangouts/$HANGOUT_ID" \
      -H "Authorization: Bearer $JWT_TOKEN")

    if echo "$HANGOUT_DETAIL" | grep -q "events/test-hangout-image.jpg"; then
        pass_test "Hangout mainImagePath persisted"
    else
        fail_test "Hangout mainImagePath persisted" "$HANGOUT_DETAIL"
    fi
fi
echo ""

# 12. Verify hangout image denormalization to HangoutPointer
echo "12. Verifying hangout mainImagePath denormalization to HangoutPointer..."
if [ -z "$GROUP_ID" ]; then
    fail_test "Hangout mainImagePath denormalized to HangoutPointer" "GROUP_ID is empty, skipping test"
else
    GROUP_FEED=$(curl -s "$BASE_URL/groups/$GROUP_ID/feed" \
      -H "Authorization: Bearer $JWT_TOKEN")

    if echo "$GROUP_FEED" | grep -q "events/test-hangout-image.jpg"; then
        pass_test "Hangout mainImagePath denormalized to HangoutPointer"
    else
        fail_test "Hangout mainImagePath denormalized to HangoutPointer" "$GROUP_FEED"
    fi
fi
echo ""

# 13. Update hangout image
echo "13. Updating hangout mainImagePath..."
if [ -z "$HANGOUT_ID" ]; then
    fail_test "Update hangout mainImagePath" "HANGOUT_ID is empty, skipping test"
else
    # PATCH returns empty body (204 No Content), so we check HTTP status
    HTTP_STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X PATCH "$BASE_URL/hangouts/$HANGOUT_ID" \
      -H "Authorization: Bearer $JWT_TOKEN" \
      -H "Content-Type: application/json" \
      -d '{
        "mainImagePath": "events/updated-hangout-image.jpg"
      }')

    if [ "$HTTP_STATUS" = "204" ] || [ "$HTTP_STATUS" = "200" ]; then
        # Verify update by fetching hangout detail
        UPDATED_HANGOUT=$(curl -s "$BASE_URL/hangouts/$HANGOUT_ID" \
          -H "Authorization: Bearer $JWT_TOKEN")

        if echo "$UPDATED_HANGOUT" | grep -q "events/updated-hangout-image.jpg"; then
            pass_test "Update hangout mainImagePath"
        else
            fail_test "Update hangout mainImagePath" "Update succeeded but verification failed: $UPDATED_HANGOUT"
        fi
    else
        fail_test "Update hangout mainImagePath" "HTTP $HTTP_STATUS"
    fi
fi
echo ""

# 14. Verify updated hangout image in pointer
echo "14. Verifying updated hangout image in HangoutPointer..."
if [ -z "$GROUP_ID" ]; then
    fail_test "Updated hangout mainImagePath denormalized to HangoutPointer" "GROUP_ID is empty, skipping test"
else
    sleep 1  # Give DynamoDB a moment
    GROUP_FEED_UPDATED=$(curl -s "$BASE_URL/groups/$GROUP_ID/feed" \
      -H "Authorization: Bearer $JWT_TOKEN")

    if echo "$GROUP_FEED_UPDATED" | grep -q "events/updated-hangout-image.jpg"; then
        pass_test "Updated hangout mainImagePath denormalized to HangoutPointer"
    else
        fail_test "Updated hangout mainImagePath denormalized to HangoutPointer" "$GROUP_FEED_UPDATED"
    fi
fi
echo ""

# 15. Create event series with images
echo "15. Creating event series from existing hangout..."
# Calculate next week at 3pm
NEXT_WEEK=$(date -u -v+7d '+%Y-%m-%dT15:00:00Z' 2>/dev/null || date -u -d 'next week 15:00:00' '+%Y-%m-%dT%H:%M:%SZ' 2>/dev/null || echo "2025-10-07T15:00:00Z")
CREATE_SERIES_RESPONSE=$(curl -s -X POST "$BASE_URL/series" \
  -H "Authorization: Bearer $JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -d "{
    \"initialHangoutId\": \"$HANGOUT_ID\",
    \"newMemberRequest\": {
      \"title\": \"Test Series Part 2\",
      \"description\": \"Second part of series\",
      \"timeInfo\": {
        \"startTime\": \"$NEXT_WEEK\",
        \"periodGranularity\": \"day\"
      },
      \"associatedGroups\": [\"$GROUP_ID\"],
      \"mainImagePath\": \"events/series-part2-image.jpg\"
    }
  }")

SERIES_ID=$(echo "$CREATE_SERIES_RESPONSE" | grep -o '"seriesId":"[^"]*' | sed 's/"seriesId":"//')

if [ -n "$SERIES_ID" ]; then
    pass_test "Create event series"
    info "Series ID: $SERIES_ID"
else
    fail_test "Create event series" "$CREATE_SERIES_RESPONSE"
fi
echo ""

# 16. Verify series mainImagePath copied from primary hangout
echo "16. Verifying EventSeries mainImagePath..."
if [ -n "$SERIES_ID" ]; then
    SERIES_DETAIL=$(curl -s "$BASE_URL/series/$SERIES_ID" \
      -H "Authorization: Bearer $JWT_TOKEN")

    if echo "$SERIES_DETAIL" | grep -q "events/updated-hangout-image.jpg"; then
        pass_test "EventSeries mainImagePath copied from primary hangout"
    else
        fail_test "EventSeries mainImagePath copied from primary hangout" "$SERIES_DETAIL"
    fi
else
    fail_test "EventSeries mainImagePath copied from primary hangout" "Series was not created"
fi
echo ""

# Summary
echo "=========================================="
echo "Test Summary"
echo "=========================================="
echo -e "${GREEN}Passed: $TESTS_PASSED${NC}"
echo -e "${RED}Failed: $TESTS_FAILED${NC}"
echo ""

if [ $TESTS_FAILED -eq 0 ]; then
    echo -e "${GREEN}All tests passed!${NC}"
    exit 0
else
    echo -e "${RED}Some tests failed. Check output above for details.${NC}"
    exit 1
fi
