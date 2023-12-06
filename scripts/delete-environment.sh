#!/bin/bash

source config.sh

# Step 1: terminate the vm instance.
aws ec2 terminate-instances --instance-ids $(aws ec2 describe-instances --filters  "Name=instance-state-name,Values=pending,running,stopped,stopping" --query "Reservations[].Instances[].[InstanceId]" --output text | tr '\n' ' ')

# Step 2: deregister the image for the webserver's vm instances.
$DIR/deregister-image.sh

# Step 3: deregister the lambda function.
$DIR/lambda-deregister.sh
