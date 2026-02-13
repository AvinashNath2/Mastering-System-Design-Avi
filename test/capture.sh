#!/bin/bash

set -e

DIR="/Users/avinashnath/test"

mkdir -p "$DIR"

FILE="$DIR/screen_$(date +%Y%m%d_%H%M%S).png"

/usr/sbin/screencapture -x "$FILE"

echo "$FILE"
