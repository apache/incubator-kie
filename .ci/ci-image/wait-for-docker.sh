#!/bin/bash
source wait-for-process.sh

# Wait for dockerd process to start
wait_for_process dockerd

# Wait for the Docker daemon socket and API to become ready
max_wait=30
waited=0

while ((waited < max_wait)); do
    if [ -S /var/run/docker.sock ]; then
        if [ ! -w /var/run/docker.sock ] && command -v sudo >/dev/null 2>&1; then
            sudo chown root:docker /var/run/docker.sock 2>/dev/null || sudo chmod 666 /var/run/docker.sock 2>/dev/null || true
        fi
        if docker info >/dev/null 2>&1 || sudo docker info >/dev/null 2>&1; then
            return 0 2>/dev/null || exit 0
        fi
    fi
    sleep 1
    ((waited=waited+1))
done

echo "ERROR: Timed out waiting for Docker daemon API to be ready (${max_wait}s)." >&2
return 1 2>/dev/null || exit 1