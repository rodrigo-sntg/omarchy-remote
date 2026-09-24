#!/bin/bash
# Installs Omarchy Remote into Omarchy: CLI on PATH, bar widget plugin, Phone menu, theme hook.
# Safe to run again. Uses only Omarchy's documented extension points.
set -euo pipefail
repo=$(cd "$(dirname "$0")/.." && pwd -P)
config=${XDG_CONFIG_HOME:-$HOME/.config}

# 1. CLI used by the menu, the hook and the widget.
# (The package puts it in /usr/bin; a checkout of the repository links it into ~/.local/bin.)
if [[ -x /usr/bin/omarchy-remote ]]; then
  rm -f "$HOME/.local/bin/omarchy-remote"
else
  mkdir -p "$HOME/.local/bin"
  ln -sf "$repo/host/omarchy-remote" "$HOME/.local/bin/omarchy-remote"
fi
# The project was called android-keypad: its CLI and theme hook go (the menu block is replaced below).
rm -f "$HOME/.local/bin/android-keypad" "$HOME/.config/omarchy/hooks/theme-set.d/android-keypad"

# 2. Bar widget (a copy: the shell rejects symlinks inside plugins).
plugin="$config/omarchy/plugins/rodrigo.phone"
mkdir -p "$plugin"
cp "$repo/omarchy/plugin/manifest.json" "$repo/omarchy/plugin/BarWidget.qml" "$plugin/"
omarchy-plugin-validate "$plugin"
omarchy-shell -q shell rescanPlugins || true
# The shell needs a moment to see a new plugin; without a running shell (SSH, TTY) this is skipped.
for _ in 1 2 3 4 5; do
  omarchy-plugin-list --json | jq -e 'any(.[]; .id == "rodrigo.phone" and .enabled)' >/dev/null && break
  omarchy-plugin-enable rodrigo.phone right >/dev/null 2>&1 && break
  sleep 1
done
omarchy-plugin-list --json | jq -e 'any(.[]; .id == "rodrigo.phone" and .enabled)' >/dev/null ||
  echo "Aviso: ative o widget depois com: omarchy plugin enable rodrigo.phone right" >&2

# 3. Phone submenu in Super+Space: added right after the opening brace, or refreshed in place on
#    later runs; the result must parse, or the file is left as it was (backup kept either way).
menu="$config/omarchy/extensions/omarchy-menu.jsonc"
mkdir -p "$(dirname "$menu")"
[[ -f $menu ]] && cp "$menu" "$menu.bak-omarchy-remote"
python3 "$repo/omarchy/menu_merge.py" "$menu" "$repo/omarchy/menu.jsonc"

# 4. The phone follows theme changes.
# (omarchy-hook-install always writes under ~/.config, whatever XDG_CONFIG_HOME says.)
omarchy-hook-install theme-set "$repo/omarchy/theme-set" >/dev/null
mv -f "$HOME/.config/omarchy/hooks/theme-set.d/theme-set" "$HOME/.config/omarchy/hooks/theme-set.d/omarchy-remote"

omarchy-shell -q shell rescanPlugins || true
echo "Omarchy Remote instalado: ícone na barra, menu Super+Espaço → Phone, tema acompanhando."
