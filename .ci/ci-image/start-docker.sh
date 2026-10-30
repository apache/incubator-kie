#!/bin/bash
source /opt/bash-utils/logger.sh

INFO "Starting supervisor"
sudo bash -c "/usr/bin/supervisord >> /dev/null 2>&1" &

INFO "Starting docker"
bash -c "source wait-for-docker.sh > /dev/null && sudo chown root:docker /var/run/docker.sock" &
