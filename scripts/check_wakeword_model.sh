#!/usr/bin/env bash

set -e

MODEL="app/src/main/assets/hey_poxi.onnx"

if [ ! -f "$MODEL" ]; then
    echo "ERROR: hey_poxi.onnx is missing."
    echo "Place the trained model at:"
    echo "$MODEL"
    exit 1
fi

SIZE=$(wc -c < "$MODEL")

if [ "$SIZE" -le 1000 ]; then
    echo "ERROR: hey_poxi.onnx looks invalid or empty."
    exit 1
fi

echo "Wake-word model found."
echo "Size: ${SIZE} bytes"
