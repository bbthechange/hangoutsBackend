#!/bin/bash

echo "🔍 Running TDD verification..."
echo "===================================="

# Change to the project root directory
cd "$(dirname "$0")/.."

# Run the tests
echo "Executing: ./gradlew test"
./gradlew test

# Capture the exit code
TEST_EXIT_CODE=$?

echo ""
echo "===================================="

if [ $TEST_EXIT_CODE -eq 0 ]; then
    echo "✅ ALL TESTS PASS - TDD Step 1 Complete"
    echo "✅ BUILD SUCCESSFUL with 0 test failures"
    echo "✅ Ready to proceed to TDD Step 2"
    exit 0
else
    echo "❌ TESTS FAILING - Must continue TDD Step 1"
    echo "❌ BUILD FAILED or tests failed"
    echo "❌ Do NOT proceed to Step 2"
    echo ""
    echo "Required actions:"
    echo "- Fix compilation errors if any"
    echo "- Fix failing tests"
    echo "- Use debug-root-cause-analyst agent"
    echo "- Make one small change at a time"
    echo "- Run this script again"
    exit 1
fi