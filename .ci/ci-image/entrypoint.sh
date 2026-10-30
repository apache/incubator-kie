#!/bin/bash
# cgroup v2: enable nesting
if [ -f /sys/fs/cgroup/cgroup.controllers ]; then
	echo "in cgroupv2 branch"
	# move the processes from the root group to the /init group,
	# otherwise writing subtree_control fails with EBUSY.
	# An error during moving non-existent process (i.e., "cat") is ignored.
	sudo mkdir -p /sys/fs/cgroup/init
	sudo bash -c "xargs -rn1 < /sys/fs/cgroup/cgroup.procs > /sys/fs/cgroup/init/cgroup.procs || :"
	# enable controllers
	sudo bash -c "sed -e 's/ / +/g' -e 's/^/+/' < /sys/fs/cgroup/cgroup.controllers > /sys/fs/cgroup/cgroup.subtree_control"
fi

# Start docker and print logs
start-docker.sh

# process the command passed, after docker is started
if [ $# -gt 0 ]; then
	exec "$@"
fi