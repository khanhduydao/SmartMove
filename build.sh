#!/bin/bash
# SmartMove Build Script

SRC_DIR="src/main/java"
OUT_DIR="out"
MAIN_CLASS="com.smartmove.Main"

echo "=== SmartMove Build ==="

# Create output directory
mkdir -p "$OUT_DIR"

# Find all Java files
SOURCES=$(find "$SRC_DIR" -name "*.java" | tr '\n' ' ')

# Compile
echo "Compiling..."
javac -d "$OUT_DIR" --enable-preview --release 16 $SOURCES 2>&1

if [ $? -eq 0 ]; then
    echo "Build SUCCESS"
    echo ""
    echo "=== Running SmartMove ==="
    mkdir -p data
    java -cp "$OUT_DIR" --enable-preview "$MAIN_CLASS" 2>&1
else
    echo "Build FAILED"
    exit 1
fi
