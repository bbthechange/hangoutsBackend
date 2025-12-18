#!/bin/bash
set -e

echo "Configuring Grafana Alloy..."

# Determine environment from EB or default to production
ENVIRONMENT=$(/opt/elasticbeanstalk/bin/get-config environment -k ENVIRONMENT || echo "production")

# Get unique instance identifier (EC2 Instance ID)
# This is CRITICAL - without this, multiple EB instances will report metrics
# with the same "localhost:5000" instance label, causing counter values to
# be interleaved and rate() to show phantom traffic due to false "resets"
INSTANCE_ID=$(curl -s --connect-timeout 2 http://169.254.169.254/latest/meta-data/instance-id 2>/dev/null || hostname)
echo "Instance ID: ${INSTANCE_ID}"

# Get Grafana Cloud credentials from AWS Parameter Store
# Parameters should be stored at:
#   /inviter/grafana/remote-write-url
#   /inviter/grafana/remote-write-user
#   /inviter/grafana/remote-write-password (SecureString)
GRAFANA_URL=$(aws ssm get-parameter --name "/inviter/grafana/remote-write-url" --query 'Parameter.Value' --output text 2>/dev/null || echo "")
GRAFANA_USER=$(aws ssm get-parameter --name "/inviter/grafana/remote-write-user" --query 'Parameter.Value' --output text 2>/dev/null || echo "")
GRAFANA_PASS=$(aws ssm get-parameter --name "/inviter/grafana/remote-write-password" --with-decryption --query 'Parameter.Value' --output text 2>/dev/null || echo "")

# Skip configuration if credentials are not set
if [ -z "$GRAFANA_URL" ] || [ -z "$GRAFANA_USER" ] || [ -z "$GRAFANA_PASS" ]; then
    echo "Grafana Cloud credentials not configured. Skipping Alloy configuration."
    echo "Create these parameters in AWS Parameter Store:"
    echo "  /inviter/grafana/remote-write-url"
    echo "  /inviter/grafana/remote-write-user"
    echo "  /inviter/grafana/remote-write-password (SecureString)"
    exit 0
fi

# Create Alloy config directory
mkdir -p /etc/alloy

# Write Alloy configuration
cat > /etc/alloy/config.alloy << EOF
prometheus.scrape "spring_boot" {
    targets = [{"__address__" = "localhost:5000", "instance" = "${INSTANCE_ID}"}]
    forward_to = [prometheus.remote_write.grafana_cloud.receiver]
    scrape_interval = "15s"
    metrics_path = "/actuator/prometheus"
}

prometheus.remote_write "grafana_cloud" {
    endpoint {
        url = "${GRAFANA_URL}"
        basic_auth {
            username = "${GRAFANA_USER}"
            password = "${GRAFANA_PASS}"
        }
    }
    external_labels = {
        environment = "${ENVIRONMENT}",
        application = "inviter-backend",
        instance_id = "${INSTANCE_ID}",
    }
}
EOF

# Enable and start/restart Alloy service
systemctl enable alloy
systemctl restart alloy

echo "Grafana Alloy configured and started for environment: ${ENVIRONMENT}"
