#!/bin/bash
set -e

# Configuration
APP_ID="com.searchlauncher.app"
OUTPUT_DIR="fdroid_submission"
SOURCE_SCREENSHOTS="screenshots"
METADATA_FILE="fdroid/$APP_ID.yml"

# Cleanup previous run
echo "Cleaning up $OUTPUT_DIR..."
rm -rf "$OUTPUT_DIR"

# Create directory structure matching fdroiddata
# Structure: metadata/package.id/locale/phoneScreenshots/
TARGET_SCREENSHOTS_DIR="$OUTPUT_DIR/metadata/$APP_ID/en-US/phoneScreenshots"
mkdir -p "$TARGET_SCREENSHOTS_DIR"

# Copy Metadata
if [ -f "$METADATA_FILE" ]; then
    echo "Copying metadata file..."
    cp "$METADATA_FILE" "$OUTPUT_DIR/metadata/"
else
    echo "Error: Metadata file $METADATA_FILE not found!"
    exit 1
fi

# Copy Screenshots
if [ -d "$SOURCE_SCREENSHOTS" ]; then
    echo "Copying screenshots..."
    # Copy all images (jpg, png)
    cp "$SOURCE_SCREENSHOTS"/*.{jpg,png} "$TARGET_SCREENSHOTS_DIR" 2>/dev/null || true

    FILE_COUNT=$(ls "$TARGET_SCREENSHOTS_DIR" | wc -l)
    echo "Copied $FILE_COUNT screenshots."
else
    echo "Warning: Screenshots directory $SOURCE_SCREENSHOTS not found."
fi

echo ""
echo "--------------------------------------------------------"
echo "Submission package prepared in: $OUTPUT_DIR"
echo "--------------------------------------------------------"
echo "Next steps:"
echo "1. Fork https://gitlab.com/fdroid/fdroiddata"
echo "2. Copy the contents to your fdroiddata fork:"
echo "   cp -R $OUTPUT_DIR/metadata/* /path/to/fdroiddata/metadata/"
echo "   (This merges your files into the existing metadata folder)"
echo "3. Create a Merge Request."
