#!/bin/bash
set -euo pipefail
set -x

# This script is hardcoded into the AMI image at /opt/app/client-init.sh
# It is started by systemd in /usr/lib/systemd/system/forum-client.service
#
# This downloads the latest runner

# download with built in aws client that handles the IAM Roles
# wget -o client-run.sh https://s3.us-east-2.amazonaws.com/xana.sh/client-run.sh 
aws s3 cp s3://xana.sh/client-run.sh .

chmod +x client-run.sh
./client-run.sh
