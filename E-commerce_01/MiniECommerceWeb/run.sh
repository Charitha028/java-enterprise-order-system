#!/bin/sh
cd "$(dirname "$0")"
mkdir -p out
javac -d out src/*.java || exit 1
echo "Starting server at http://localhost:8080 (Ctrl+C to stop)"
java -cp out Main
