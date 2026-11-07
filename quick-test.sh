#!/bin/bash

echo "=== Rate Limiter Quick Test ==="
echo ""

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 1. Check gateway health
echo "1. Checking gateway health..."
curl -s http://localhost:8080/gateway/health
echo ""

# 2. Check mock server
echo "2. Checking mock server..."
curl -s http://localhost:8081/
echo ""

# 3. Initial tokens
echo "3. Initial Rate Limit Status:"
initial_status=$(curl -s http://localhost:8080/gateway/rate-limit/status)
echo "$initial_status" | jq . 2>/dev/null || echo "$initial_status"
initial_tokens=$(echo "$initial_status" | jq -r '.availableTokens' 2>/dev/null || echo "unknown")
echo ""

# 4. Fire 100 requests in parallel
echo "4. Making 100 requests in parallel (capacity is 10)..."
results=$(seq 1 100 | xargs -n1 -P30 -I{} curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/api/test)

success_count=$(echo "$results" | grep -c "^200$")
blocked_count=$(echo "$results" | grep -c "^429$")
failed_count=$(echo "$results" | grep -c "^000$")

i=0
for code in $results; do
    i=$((i+1))
    if [ "$code" = "200" ]; then
        echo -e "  Request $i: ${GREEN}✓ Allowed (200)${NC}"
    elif [ "$code" = "429" ]; then
        echo -e "  Request $i: ${RED}✗ Blocked (429)${NC}"
    else
        echo -e "  Request $i: ${YELLOW}? Status ($code)${NC}"
    fi
done
echo ""

# 5. Final status
echo "5. Final Rate Limit Status:"
final_status=$(curl -s http://localhost:8080/gateway/rate-limit/status)
echo "$final_status" | jq . 2>/dev/null || echo "$final_status"
final_tokens=$(echo "$final_status" | jq -r '.availableTokens' 2>/dev/null || echo "unknown")
echo ""

# Summary
echo "=== Test Summary ==="
echo "Successful requests: $success_count"
echo "Blocked requests: $blocked_count"
echo "Failed requests (no response): $failed_count"
echo "Initial tokens: $initial_tokens"
echo "Final tokens: $final_tokens"
echo ""

if [ "$blocked_count" -gt 0 ]; then
    echo -e "${GREEN}✓ Test PASSED! Rate limiting is working correctly.${NC}"
else
    echo -e "${YELLOW}⚠ Test results unexpected. Check configuration.${NC}"
fi
