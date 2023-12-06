#!/bin/bash

source config.sh

# Requesting an instance reboot.
aws ec2 reboot-instances --instance-ids $(cat service.id)
echo "Rebooting service to test web server auto-start."

# Letting the instance shutdown.
sleep 1

# Wait for port 8000 to become available.
while ! nc -z $(cat service.dns) 8000; do
	echo "Waiting for $(cat service.dns):8000..."
	sleep 0.5
done

# Sending a query!
echo "Sending a query!"
curl -i $(cat service.dns):8000