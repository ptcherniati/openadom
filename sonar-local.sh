#!/usr/bin/env bash
# =============================================================================
# Script d'analyse SonarQube locale — openADOM backend
# Serveur  : https://sonarqube.dev.gissol.fr
# Projet   : anaee-dev_openadom_backend_caf3d282-990e-4907-8f15-44424cf3c8b0
#
# Modes :
#   branch [nom_branche]         → analyse la branche indiquée (défaut: branche courante)
#   mr     [MR_IID] [base]       → analyse en mode Merge Request
#                                  MR_IID = numéro de MR GitLab (défaut: 1)
#                                  base   = branche cible       (défaut: develop)
#
# Options (après le mode) :
#   --skip-tests                 → n'exécute pas les tests (réutilise un rapport existant)
#   --enforce-gate               → fait échouer le script si le Quality Gate est KO
#   --with-docker-tests          → inclut aussi les tests nécessitant Docker
#
# Prérequis :
#   export SONAR_TOKEN=<votre_token_sonarqube>
#
# Exemples :
#   ./sonar-local.sh branch develop
#   ./sonar-local.sh branch                        # branche courante
#   ./sonar-local.sh mr 42 develop
#   ./sonar-local.sh branch --skip-tests           # réutilise le jacoco.xml existant
#   ./sonar-local.sh branch --enforce-gate         # bloque si Quality Gate KO
# =============================================================================
set -euo pipefail

SONAR_HOST_URL="https://sonarqube.dev.gissol.fr"
PROJECT_KEY="anaee-dev_openadom_backend_caf3d282-990e-4907-8f15-44424cf3c8b0"
CURRENT_BRANCH=$(git branch --show-current)

# ── Parsing des arguments ────────────────────────────────────────────────────
MODE="${1:-branch}"
shift || true

# Valeurs par défaut selon le mode
BRANCH_NAME="${CURRENT_BRANCH}"
MR_IID="1"
TARGET_BRANCH="develop"

if [ "${MODE}" = "branch" ]; then
  # 1er arg optionnel = nom de branche (si ce n'est pas une option)
  if [ -n "${1:-}" ] && [[ "${1}" != --* ]]; then
    BRANCH_NAME="$1"; shift || true
  fi
elif [ "${MODE}" = "mr" ]; then
  if [ -n "${1:-}" ] && [[ "${1}" != --* ]]; then
    MR_IID="$1"; shift || true
  fi
  if [ -n "${1:-}" ] && [[ "${1}" != --* ]]; then
    TARGET_BRANCH="$1"; shift || true
  fi
else
  echo "Usage: $0 [branch [nom_branche] | mr [MR_IID] [branche_cible]] [options]"
  echo "Options : --skip-tests  --enforce-gate  --with-docker-tests"
  exit 1
fi

# ── Options booléennes ───────────────────────────────────────────────────────
SKIP_TESTS=false
ENFORCE_GATE=false
WITH_DOCKER=false
for arg in "$@"; do
  case "${arg}" in
    --skip-tests)        SKIP_TESTS=true ;;
    --enforce-gate)      ENFORCE_GATE=true ;;
    --with-docker-tests) WITH_DOCKER=true ;;
    *) echo "Option inconnue : ${arg}" ; exit 1 ;;
  esac
done

# ── Vérification du token ────────────────────────────────────────────────────
if [ -z "${SONAR_TOKEN:-}" ]; then
  echo "❌  SONAR_TOKEN n'est pas défini."
  echo "    Exécutez : export SONAR_TOKEN=<votre_token_sonarqube>"
  exit 1
fi

JACOCO_XML="target/site/jacoco/jacoco.xml"

# ── Phase 1 : Tests + génération du rapport Jacoco ──────────────────────────
if [ "${SKIP_TESTS}" = true ]; then
  if [ ! -f "${JACOCO_XML}" ]; then
    echo "⚠️  --skip-tests actif mais ${JACOCO_XML} introuvable."
    echo "    Exécutez d'abord : mvn test -DexcludedGroups=docker-required"
    echo "    Ou relancez sans --skip-tests."
    exit 1
  fi
  echo "ℹ️  Tests ignorés, réutilisation de ${JACOCO_XML}"
else
  EXCLUDED_GROUPS="docker-required"
  if [ "${WITH_DOCKER}" = true ]; then
    EXCLUDED_GROUPS=""
    echo "ℹ️  Tests Docker inclus (Docker doit être disponible)"
  fi

  echo ""
  echo "══════════════════════════════════════════════════════════════════════"
  echo "  📋 Phase 1/2 — Compilation + Tests (génération rapport Jacoco)"
  if [ -n "${EXCLUDED_GROUPS}" ]; then
    echo "  ⚙️  Groupes exclus : ${EXCLUDED_GROUPS}"
  else
    echo "  ⚙️  Tous les tests inclus (Docker + non-Docker)"
  fi
  echo "══════════════════════════════════════════════════════════════════════"

  # Passer la propriété Maven pour que tous les profils respectent l'exclusion.
  # Avec -Dsurefire.excludedGroups="" les tests Docker sont inclus dans Jacoco.
  SUREFIRE_OPTS=("-Dsurefire.excludedGroups=${EXCLUDED_GROUPS}")

  # On capture le code de retour sans quitter (pour lancer Sonar quand même)
  set +e
  mvn --batch-mode test "${SUREFIRE_OPTS[@]}"
  TEST_EXIT_CODE=$?
  set -e

  if [ ${TEST_EXIT_CODE} -ne 0 ]; then
    echo ""
    echo "⚠️  Des tests ont échoué (code ${TEST_EXIT_CODE})."
    echo "    L'analyse Sonar sera quand même lancée pour identifier les"
    echo "    problèmes de qualité de code. Corrigez les tests avant la MR."
  fi
fi

# ── Phase 2 : Analyse SonarQube ──────────────────────────────────────────────
QUALITY_GATE_WAIT="false"
if [ "${ENFORCE_GATE}" = true ]; then
  QUALITY_GATE_WAIT="true"
fi

MVN_SONAR_OPTS=(
  --batch-mode
  sonar:sonar
  -DskipTests
  -Dsonar.host.url="${SONAR_HOST_URL}"
  -Dsonar.token="${SONAR_TOKEN}"
  -Dsonar.projectKey="${PROJECT_KEY}"
  -Dsonar.qualitygate.wait="${QUALITY_GATE_WAIT}"
  -Dsonar.scm.provider=git
)

if [ "${MODE}" = "branch" ]; then
  echo ""
  echo "══════════════════════════════════════════════════════════════════════"
  echo "  🔍 Phase 2/2 — Analyse SonarQube – Branche : ${BRANCH_NAME}"
  echo "  📡 Serveur     : ${SONAR_HOST_URL}"
  echo "  🚦 Quality Gate: $([ "${ENFORCE_GATE}" = true ] && echo 'BLOQUANT' || echo 'informatif')"
  echo "  ⚠️  Edition Community : pas de séparation par branche (tout"
  echo "      remonte sur le projet principal)"
  echo "══════════════════════════════════════════════════════════════════════"

  mvn "${MVN_SONAR_OPTS[@]}"

elif [ "${MODE}" = "mr" ]; then
  echo ""
  echo "══════════════════════════════════════════════════════════════════════"
  echo "  🔍 Phase 2/2 — Analyse SonarQube – Merge Request"
  echo "  📌 Branche source  : ${CURRENT_BRANCH}"
  echo "  🎯 Branche cible   : ${TARGET_BRANCH}"
  echo "  🔢 Numéro de MR    : ${MR_IID}"
  echo "  📡 Serveur         : ${SONAR_HOST_URL}"
  echo "  🚦 Quality Gate    : $([ "${ENFORCE_GATE}" = true ] && echo 'BLOQUANT' || echo 'informatif')"
  echo "  ⚠️  Edition Community : pas de décoration PR (tout remonte sur"
  echo "      le projet principal)"
  echo "══════════════════════════════════════════════════════════════════════"

  mvn "${MVN_SONAR_OPTS[@]}"
fi

echo ""
echo "✅  Analyse terminée."
echo "    📊 Tableau de bord : ${SONAR_HOST_URL}/dashboard?id=${PROJECT_KEY}"
if [ "${ENFORCE_GATE}" = false ]; then
  echo "    ℹ️  Quality Gate non bloquant — consultez le dashboard pour les problèmes détectés."
  echo "    💡 Pour un check strict : ./sonar-local.sh ${MODE} --enforce-gate"
fi