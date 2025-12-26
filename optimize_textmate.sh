#!/bin/bash

# Keep only essential languages - saves ~8MB
KEEP_LANGUAGES=(
    "text"
    "markdown" 
    "json"
    "xml"
    "html"
    "css"
    "javascript"
    "python"
    "java"
    "kotlin"
    "cpp"
    "shellscript"
)

cd app/src/main/assets/textmate/

# Backup original
cp -r . ../textmate_backup/

# Remove all language directories
for dir in */; do
    if [[ ! " ${KEEP_LANGUAGES[@]} " =~ " ${dir%/} " ]]; then
        echo "Removing $dir"
        rm -rf "$dir"
    fi
done

echo "Kept languages: ${KEEP_LANGUAGES[@]}"
echo "Size after optimization:"
du -sh .