FROM alpine:latest
RUN apk add --no-cache openssh sudo iproute2 && ssh-keygen -A \
 && adduser -D -s /bin/sh pi && echo 'pi:raspberry-test' | chpasswd \
 && echo 'pi ALL=(ALL) ALL' > /etc/sudoers.d/pi \
 && sed -i 's/^#\?PasswordAuthentication.*/PasswordAuthentication yes/' /etc/ssh/sshd_config \
 && echo 'PRETTY_NAME="Debian GNU/Linux 12 (bookworm) [Testcontainer]"' > /etc/os-release \
 && mkdir -p /home/pi/.ssh /var/run && touch /var/run/reboot-required && hostname
COPY vcgencmd apt /usr/bin/
COPY id_test.pub /home/pi/.ssh/authorized_keys
RUN chmod +x /usr/bin/vcgencmd /usr/bin/apt && chown -R pi:pi /home/pi/.ssh && chmod 700 /home/pi/.ssh && chmod 600 /home/pi/.ssh/authorized_keys
EXPOSE 22
CMD ["/usr/sbin/sshd", "-D", "-e"]
