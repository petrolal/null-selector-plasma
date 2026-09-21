#!/usr/bin/env bash
# ==============================================================================
# backup.sh: Alias/Wrapper for harvest.sh
# Scrapes active configs from local system into repository.
# ==============================================================================
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
exec "$SCRIPT_DIR/harvest.sh" "$@"
