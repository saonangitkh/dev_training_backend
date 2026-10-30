#!/bin/sh
# Copies the documents at the repo root (CLAUDE.md, README.md, any *.md) into docs/workspace/.
# Only changed files are copied; a copy whose root file was deleted is removed.
# Run from anywhere: sh docs/workspace/sync.sh
set -eu

workspace=$(cd "$(dirname "$0")" && pwd)
root=$(cd "$workspace/../.." && pwd)

for source in "$root"/*.md; do
	[ -f "$source" ] || continue
	name=$(basename "$source")
	if ! cmp -s "$source" "$workspace/$name"; then
		cp "$source" "$workspace/$name"
		echo "synced  $name"
	fi
done

for copy in "$workspace"/*.md; do
	[ -f "$copy" ] || continue
	name=$(basename "$copy")
	if [ ! -f "$root/$name" ]; then
		rm "$copy"
		echo "removed $name"
	fi
done
