#!/bin/bash

source config.sh

# Step 1: create the image for the webserver's vm instances.
$DIR/create-image.sh

# Step 2: register a lambda function.
$DIR/lambda-register.sh

# Step 3: launch a vm instance for the loadbalancer/autoscaler.
$DIR/launch-vm.sh

# Step 4: install software in the VM instance.
$DIR/install-service.sh
