#!/bin/bash
# Monitor BCrypt → SHA-256 migration progress

LOG_GROUP="/aws/elasticbeanstalk/inviter-test/var/log/eb-engine.log"
START_TIME=$(date -u -d '24 hours ago' +%Y-%m-%dT%H:%M:%S 2>/dev/null || date -u -v-24H +%Y-%m-%dT%H:%M:%S)
END_TIME=$(date -u +%Y-%m-%dT%H:%M:%S)

echo "=== Refresh Token Migration Status ==="
echo "Date: $(date)"
echo ""

# Count BCrypt validations
BCRYPT_COUNT=$(aws logs filter-log-events \
  --log-group-name "$LOG_GROUP" \
  --start-time $(date -d "$START_TIME" +%s 2>/dev/null || date -j -f "%Y-%m-%dT%H:%M:%S" "$START_TIME" +%s)000 \
  --end-time $(date -d "$END_TIME" +%s 2>/dev/null || date -j -f "%Y-%m-%dT%H:%M:%S" "$END_TIME" +%s)000 \
  --filter-pattern "Validating legacy BCrypt" \
  --query 'events[*]' \
  --output json 2>/dev/null | jq 'length' 2>/dev/null || echo "0")

# Count SHA-256 validations
SHA256_COUNT=$(aws logs filter-log-events \
  --log-group-name "$LOG_GROUP" \
  --start-time $(date -d "$START_TIME" +%s 2>/dev/null || date -j -f "%Y-%m-%dT%H:%M:%S" "$START_TIME" +%s)000 \
  --end-time $(date -d "$END_TIME" +%s 2>/dev/null || date -j -f "%Y-%m-%dT%H:%M:%S" "$END_TIME" +%s)000 \
  --filter-pattern "Validating SHA-256" \
  --query 'events[*]' \
  --output json 2>/dev/null | jq 'length' 2>/dev/null || echo "0")

TOTAL=$((BCRYPT_COUNT + SHA256_COUNT))

if [ $TOTAL -eq 0 ]; then
  echo "No refresh token validations in last 24 hours"
else
  BCRYPT_PCT=$((BCRYPT_COUNT * 100 / TOTAL))
  SHA256_PCT=$((SHA256_COUNT * 100 / TOTAL))

  echo "BCrypt tokens:  $BCRYPT_COUNT ($BCRYPT_PCT%)"
  echo "SHA-256 tokens: $SHA256_COUNT ($SHA256_PCT%)"
  echo "Total:          $TOTAL"
  echo ""

  # Visual progress bar
  printf "Migration: ["
  for i in $(seq 1 $SHA256_PCT); do printf "#"; done
  for i in $(seq $((SHA256_PCT + 1)) 100); do printf " "; done
  printf "] $SHA256_PCT%%\n"
fi
