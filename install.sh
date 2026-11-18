#!/usr/bin/env bash
# Builds how and installs it into ~/.local/share/how (program) and ~/.local/bin (command).
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"

mkdir -p out "$HOME/.local/share/how" "$HOME/.local/bin"
scala-cli --power package . --assembly -o out/how.jar -f
cp out/how.jar "$HOME/.local/share/how/how.jar"
cp launcher/how "$HOME/.local/bin/how"
chmod +x "$HOME/.local/bin/how"
case "$(uname -s)" in
  MINGW* | MSYS* | CYGWIN*) cp launcher/how.cmd "$HOME/.local/bin/how.cmd" ;;
esac
echo "Installed: $HOME/.local/bin/how"
