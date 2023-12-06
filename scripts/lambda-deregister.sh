#!/bin/bash

source config.sh

aws lambda delete-function --function-name foxes-rabbits
aws lambda delete-function --function-name insect-war
aws lambda delete-function --function-name compression

aws iam detach-role-policy \
	--role-name lambda-role \
	--policy-arn arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole

aws iam delete-role --role-name lambda-role