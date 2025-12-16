#!/bin/bash
set -e

# Check if Grafana Alloy is already installed
if command -v /usr/bin/alloy &> /dev/null; then
    echo "Grafana Alloy already installed"
    exit 0
fi

echo "Installing Grafana Alloy..."

# Add Grafana GPG key
rpm --import https://rpm.grafana.com/gpg.key

# Add Grafana repository
cat > /etc/yum.repos.d/grafana.repo << 'EOF'
[grafana]
name=grafana
baseurl=https://rpm.grafana.com
repo_gpgcheck=1
enabled=1
gpgcheck=1
gpgkey=https://rpm.grafana.com/gpg.key
sslverify=1
sslcacert=/etc/pki/tls/certs/ca-bundle.crt
EOF

# Install Alloy
dnf install -y alloy

echo "Grafana Alloy installed successfully"
