#!/bin/sh
# Starts the Omarchy Remote host on this machine's Tailscale address.
# Usage: host/run.sh [--allow <machine>] [--port 8765]   (default: any of your own Tailscale devices)
# The first start makes its own Python environment (.venv) from requirements.txt.
set -e
cd "$(dirname "$0")"
if [ ! -x .venv/bin/python ]; then
    python3 -m venv .venv
    .venv/bin/pip install -q -r requirements.txt
fi
exec .venv/bin/python -m keypad_host "$@"
