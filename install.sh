#!/usr/bin/env bash
# Installs how into ~/.local/share/how (program) and ~/.local/bin (command).
# Uses how.jar next to this script (release download); otherwise builds it with scala-cli (git clone).
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"

if [[ -f how.jar ]]; then
  jar=how.jar
else
  mkdir -p out
  scala-cli --power package . --assembly -o out/how.jar -f
  jar=out/how.jar
fi
launchers=launcher
[[ -d "$launchers" ]] || launchers=.

mkdir -p "$HOME/.local/share/how" "$HOME/.local/bin"
cp "$jar" "$HOME/.local/share/how/how.jar"
cp "$launchers/how" "$HOME/.local/bin/how"
chmod +x "$HOME/.local/bin/how"
case "$(uname -s)" in
  MINGW* | MSYS* | CYGWIN*) cp "$launchers/how.cmd" "$HOME/.local/bin/how.cmd" ;;
esac
echo "Installed: $HOME/.local/bin/how"
