#!/bin/bash

# Test script to verify Gradle wrapper setup
echo "🔍 Testing Gradle wrapper setup..."

# Check if gradlew exists and is executable
if [ -f "./gradlew" ] && [ -x "./gradlew" ]; then
    echo "✅ gradlew exists and is executable"
else
    echo "❌ gradlew missing or not executable"
    exit 1
fi

# Check if gradle-wrapper.jar exists
if [ -f "gradle/wrapper/gradle-wrapper.jar" ]; then
    echo "✅ gradle-wrapper.jar exists"
    # Check if it's a valid JAR file
    if file gradle/wrapper/gradle-wrapper.jar | grep -q "Zip archive"; then
        echo "✅ gradle-wrapper.jar is a valid JAR file"
    else
        echo "❌ gradle-wrapper.jar is not a valid JAR file"
        exit 1
    fi
else
    echo "❌ gradle-wrapper.jar missing"
    exit 1
fi

# Check gradle-wrapper.properties
if [ -f "gradle/wrapper/gradle-wrapper.properties" ]; then
    echo "✅ gradle-wrapper.properties exists"
    echo "📋 Gradle version: $(grep distributionUrl gradle/wrapper/gradle-wrapper.properties | cut -d'-' -f3 | cut -d'.' -f1-2)"
else
    echo "❌ gradle-wrapper.properties missing"
    exit 1
fi

echo ""
echo "🎉 Gradle wrapper setup looks good!"
echo "💡 Note: You need Java installed locally to run './gradlew' commands"
echo "🚀 But GitHub Actions will work fine since it has Java pre-installed"
