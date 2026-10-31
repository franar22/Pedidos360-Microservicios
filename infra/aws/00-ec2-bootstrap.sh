#!/usr/bin/env bash
# Ejecutar DENTRO de la instancia EC2 (Amazon Linux 2023) para dejarla lista: Docker + Docker Compose.
# Uso:  bash 00-ec2-bootstrap.sh   (luego cerrar y abrir la sesion SSH para que aplique el grupo docker)
set -euo pipefail

sudo dnf install -y docker git
sudo systemctl enable --now docker
sudo usermod -aG docker "$USER"

sudo mkdir -p /usr/local/lib/docker/cli-plugins
sudo curl -fsSL "https://github.com/docker/compose/releases/latest/download/docker-compose-linux-$(uname -m)" \
  -o /usr/local/lib/docker/cli-plugins/docker-compose
sudo chmod +x /usr/local/lib/docker/cli-plugins/docker-compose

docker --version || true
echo "Listo. Cierra y vuelve a abrir la sesion, sube el proyecto y ejecuta: docker compose up -d --build"
