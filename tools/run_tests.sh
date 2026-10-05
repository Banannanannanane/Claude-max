#!/usr/bin/env bash
# Lance les tests du jeu et échoue à la moindre erreur de script ou du moteur.
#   tools/run_tests.sh [chemin/vers/godot]
set -u
GODOT="${1:-${GODOT:-godot}}"
DIR="$(cd "$(dirname "$0")/../game" && pwd)"
LOG="$(mktemp)"
status=0
"$GODOT" --headless --path "$DIR" --import >/dev/null 2>&1
for scenario in logic ui; do
	echo "=== tests : $scenario"
	timeout 1500 "$GODOT" --headless --path "$DIR" -- --qa=$scenario >"$LOG" 2>&1
	code=$?
	grep -E "^(OK|ÉCHEC)|RÉSULTAT" "$LOG"
	# erreurs du moteur, hors messages bénins de fin de programme et d'audio
	errors=$(grep -E "SCRIPT ERROR|^ERROR:|^WARNING:" "$LOG" | grep -vE "leaked at exit|still in use at exit|Pages in use exist|audio|ALSA|Could not set V-Sync|ObjectDB instances leaked" || true)
	if [ -n "$errors" ]; then
		echo "--- erreurs du moteur :"
		grep -E "SCRIPT ERROR|^ERROR:|^WARNING:" -A3 "$LOG" | grep -vE "leaked at exit|still in use at exit|Pages in use exist|ALSA" | head -60
		status=1
	fi
	if [ $code -ne 0 ]; then
		echo "--- code de sortie $code"
		status=1
	fi
done
rm -f "$LOG"
exit $status
