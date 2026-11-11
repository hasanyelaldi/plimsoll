#!/usr/bin/env bash
# Runs `plimsoll analyze --format=SARIF` for the GitHub Action (#76). Called by action.yml with the inputs in the
# environment. Exits 0 when the analysis ran, whatever it found; the exit code of the analysis goes to the step's
# `exit-code` output so that action.yml can upload the SARIF file before failing on findings. Exits non-zero only when
# the analysis could not run: bad input (2), no version, or a jar that fails its checksum or signature.
set -euo pipefail

# The release signing key, also on keys.openpgp.org. The key file next to this script is the only key trusted.
SIGNING_KEY=CBFEEF57067799F155B117A344F145F2D5D160C1
CENTRAL=https://repo1.maven.org/maven2/io/github/hasanyelaldi/plimsoll-cli

fail() {
  echo "::error title=plimsoll::$1" >&2
  exit 1
}

work="${RUNNER_TEMP:-$(mktemp -d)}/plimsoll"
mkdir -p "$work"

jar="${PLIMSOLL_JAR:-}"
if [[ -z "$jar" ]]; then
  version="${PLIMSOLL_VERSION:-}"
  if [[ -z "$version" && "${ACTION_REF:-}" =~ ^v([0-9]+\.[0-9]+\.[0-9]+)$ ]]; then
    version="${BASH_REMATCH[1]}"
  fi
  [[ "$version" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] \
    || fail "no Plimsoll version: use the action at a release tag (hasanyelaldi/plimsoll@v0.2.0) or set 'version'"

  jar="$work/plimsoll-$version.jar"
  url="$CENTRAL/$version/plimsoll-cli-$version.jar"
  curl -fsSL --retry 3 -o "$jar" "$url" || fail "cannot download $url"
  curl -fsSL --retry 3 -o "$jar.sha256" "$url.sha256" || fail "cannot download $url.sha256"
  curl -fsSL --retry 3 -o "$jar.asc" "$url.asc" || fail "cannot download $url.asc"

  if command -v sha256sum >/dev/null; then
    actual=$(sha256sum "$jar" | cut -d' ' -f1)
  else
    actual=$(shasum -a 256 "$jar" | cut -d' ' -f1)
  fi
  [[ "$actual" == "$(cut -d' ' -f1 < "$jar.sha256" | tr -d '[:space:]')" ]] || fail "checksum mismatch for $url"

  # A short path of its own: gpg's agent socket fails under a long directory.
  GNUPGHOME=$(mktemp -d)
  export GNUPGHOME
  trap 'gpgconf --kill all >/dev/null 2>&1 || true; rm -rf "$GNUPGHOME"' EXIT
  gpg --batch --quiet --import "$ACTION_PATH/action/plimsoll-signing-key.asc" \
    || fail "cannot import the signing key"
  status=$(gpg --batch --status-fd 1 --verify "$jar.asc" "$jar" 2>/dev/null) || true
  [[ "$status" == *"[GNUPG:] VALIDSIG $SIGNING_KEY "* ]] || fail "signature of $url is not valid for key $SIGNING_KEY"
  echo "plimsoll $version: checksum and signature verified"
fi

sarif="${SARIF_FILE:-plimsoll.sarif}"
set +e
java -jar "$jar" analyze "$PLIMSOLL_PATH" --format=SARIF --max-amp="$MAX_AMP" --fail-on="$FAIL_ON" > "$sarif"
code=$?
set -e
if [[ $code -ne 0 && $code -ne 1 ]]; then
  fail "plimsoll analyze exited with $code (invalid input)"
fi

# SARIF paths are relative to the analysed directory (or the spec's directory); code scanning reads them relative to
# the repository root (ADR 0013). Prefix them with that directory, relative to the workspace.
root="$PLIMSOLL_PATH"
[[ -f "$root" ]] && root=$(dirname "$root")
root=$(cd "$root" && pwd -P)
workspace=$(cd "${GITHUB_WORKSPACE:-.}" && pwd -P)
case "$root/" in
  "$workspace/"*) prefix="${root#"$workspace"}"; prefix="${prefix#/}" ;;
  *) fail "$PLIMSOLL_PATH is outside the workspace $workspace" ;;
esac
if [[ -n "$prefix" ]]; then
  jq --arg p "$prefix/" '
    ($p | split("/") | map(@uri) | join("/")) as $enc
    | walk(if type == "object" and has("artifactLocation")
           then .artifactLocation.uri = $enc + .artifactLocation.uri else . end)' "$sarif" > "$sarif.tmp"
  mv "$sarif.tmp" "$sarif"
fi

echo "sarif-file=$sarif" >> "${GITHUB_OUTPUT:-/dev/null}"
echo "exit-code=$code" >> "${GITHUB_OUTPUT:-/dev/null}"
echo "plimsoll analyze exited with $code; SARIF written to $sarif"
