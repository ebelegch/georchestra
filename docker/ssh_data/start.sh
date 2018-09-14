#!/bin/bash
set -e

if [ "$USER_PASS" ]; then
    echo geoserver:"$USER_PASS" |chpasswd
fi

chown -R 999:999 /mnt/geoserver_geodata &

# start openssh server
exec /usr/sbin/sshd -D