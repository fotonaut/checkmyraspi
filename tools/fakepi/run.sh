#!/bin/sh
# Startet einen Test-„Raspi“ (Alpine + sshd, gefälschtes vcgencmd/apt) auf localhost:2222.
# Login: pi / raspberry-test oder Schlüssel tools/fakepi/id_test. Im Emulator: Host 10.0.2.2, Port 2222.
set -e
cd "$(dirname "$0")"
[ -f id_test ] || ssh-keygen -q -t ed25519 -N '' -C fakepi -f id_test
docker build -q -t checkmyraspi-fakepi .
docker rm -f cmr-fakepi >/dev/null 2>&1 || true
docker run -d --name cmr-fakepi --hostname keller-pi -p 2222:22 checkmyraspi-fakepi >/dev/null
sleep 1
docker exec cmr-fakepi ssh-keygen -lf /etc/ssh/ssh_host_ed25519_key.pub
