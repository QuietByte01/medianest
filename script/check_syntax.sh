#!/bin/bash

echo "Checking for unmatched brackets..."
# Very basic check: count opening and closing brackets
check_brackets() {
    local file=$1
    local open
    local close
    open=$(grep -o "{" "$file" | wc -l)
    close=$(grep -o "}" "$file" | wc -l)
    if [ "$open" -ne "$close" ]; then
        echo "WARNING: Unmatched curly brackets in $file (Open: $open, Close: $close)"
    fi
}

export -f check_brackets
find app/src \( -name "*.kt" -o -name "*.java" -o -name "*.xml" \) -print0 | xargs -0 -I {} bash -c 'check_brackets "{}"'

echo "Checking for TODOs..."
grep -r "TODO" app/src --include="*.kt" --include="*.java"

echo "Checking for trailing whitespace..."
grep -r "[[:space:]]$" app/src --include="*.kt" --include="*.java" --include="*.xml"

echo "Syntax check complete."
