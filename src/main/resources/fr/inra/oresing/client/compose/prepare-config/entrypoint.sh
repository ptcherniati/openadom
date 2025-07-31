#!/bin/sh

set -e

CONFIG_JSON="/data/openAdom-client-configuration.json"
NGINX_TEMPLATE="/nginx.conf.template"
NGINX_OUT="/build/nginx.conf"

echo "Contenu du dossier /data :"
ls -l /data

if [ ! -f "$CONFIG_JSON" ]; then
    echo "Configuration $CONFIG_JSON introuvable !"
    exit 1
fi

# Extraire l'URL backend
BACKEND_URL=$(jq -r .instanceUrl "$CONFIG_JSON" | sed 's|/$||')
echo "Adresse backend extraite: $BACKEND_URL"

# Détecter l’environnement pour choisir l’IP de la machine hôte
if grep -qi 'microsoft\|wsl' /proc/version; then
    # Cas WSL2 (Windows Subsystem for Linux), on doit trouver l'IP de l'hôte
    HOST_DOCKER=$(cat /etc/resolv.conf | grep nameserver | awk '{print $2}')
elif grep -qi 'linux' /proc/version; then
    # Linux classique Docker
    HOST_DOCKER=172.17.0.1
else
    # Mac/Windows avec Docker Desktop
    HOST_DOCKER=host.docker.internal
fi

# Remplacer localhost ou 127.0.0.1 par l'adresse réseau de la machine hôte si besoin
if echo "$BACKEND_URL" | grep -Eq "localhost|127\.0\.0\.1"; then
    echo "Configuration locale détectée, redirection sur l'hôte Docker ($HOST_DOCKER)"
    BACKEND_URL=$(echo "$BACKEND_URL" | sed "s/localhost/$HOST_DOCKER/g" | sed "s/127\.0\.0\.1/$HOST_DOCKER/g")
    echo "Nouvelle adresse backend : $BACKEND_URL"
fi

# Générer la configuration nginx finale
sed "s|{{BACKEND_URL}}|$BACKEND_URL|g" "$NGINX_TEMPLATE" > "$NGINX_OUT"

echo "nginx.conf généré : $NGINX_OUT"