#!/bin/bash

echo "=== Rate Limiter Quick Test ==="
echo ""

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Initial tokens
initial_status=$(curl -s http://localhost:8080/gateway/rate-limit/status)
initial_tokens=$(echo "$initial_status" | grep -o '"availableTokens":[0-9]*' | grep -o '[0-9]*$')

# Make requests (parallel — all 12 at once)
echo "4. Making 12 requests (capacity is 10)..."
results=$(seq 1 12 | xargs -n1 -P12 -I{} curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/api/test)

success_count=0
blocked_count=0
i=0
for code in $results; do
    i=$((i+1))
    if [ "$code" = "200" ]; then
        echo -e "  Request $i: ${GREEN}✓ Allowed (200)${NC}"
        ((success_count++))
    elif [ "$code" = "429" ]; then
        echo -e "  Request $i: ${RED}✗ Blocked (429)${NC}"
        ((blocked_count++))
    else
        echo -e "  Request $i: ${YELLOW}? Status ($code)${NC}"
    fi
done
echo ""

# Final status
echo "5. Final Rate Limit Status:"
final_status=$(curl -s http://localhost:8080/gateway/rate-limit/status)
echo "$final_status"
final_tokens=$(echo "$final_status" | grep -o '"availableTokens":[0-9]*' | grep -o '[0-9]*$')
echo ""

# Summary
echo "=== Test Summary ==="
echo "Successful requests: $success_count"
echo "Blocked requests: $blocked_count"
echo "Initial tokens: $initial_tokens"
echo "Final tokens: $final_tokens"
echo ""

if [ "$success_count" -eq 10 ] && [ "$blocked_count" -eq 2 ]; then
    echo -e "${GREEN}✓ Test PASSED! Rate limiting is working correctly.${NC}"
else
    echo -e "${YELLOW}⚠ Test results unexpected. Check configuration.${NC}"
fi
