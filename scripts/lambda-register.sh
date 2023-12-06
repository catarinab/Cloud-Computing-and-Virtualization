#!/bin/bash

source config.sh

cd ../server
mvn clean install

cd ../scripts

aws iam create-role \
	--role-name lambda-role \
	--assume-role-policy-document '{"Version": "2012-10-17","Statement": [{ "Effect": "Allow", "Principal": {"Service": "lambda.amazonaws.com"}, "Action": "sts:AssumeRole"}]}'

sleep 5

aws iam attach-role-policy \
	--role-name lambda-role \
	--policy-arn arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole

sleep 5

aws lambda create-function \
	--function-name foxes-rabbits \
	--zip-file fileb://../server/foxes-rabbits/target/foxrabbit-1.0.0-SNAPSHOT-jar-with-dependencies.jar \
	--handler pt.ulisboa.tecnico.cnv.foxrabbit.SimulationHandler \
	--runtime java11 \
	--timeout 5 \
	--memory-size 256 \
	--role arn:aws:iam::$AWS_ACCOUNT_ID:role/lambda-role

aws lambda create-function \
	--function-name insect-war \
	--zip-file fileb://../server/insect-war/target/insectwar-1.0.0-SNAPSHOT-jar-with-dependencies.jar \
	--handler pt.ulisboa.tecnico.cnv.insectwar.WarSimulationHandler \
	--runtime java11 \
	--timeout 5 \
	--memory-size 256 \
	--role arn:aws:iam::$AWS_ACCOUNT_ID:role/lambda-role

aws lambda create-function \
	--function-name compression \
	--zip-file fileb://../server/compression/target/compression-1.0.0-SNAPSHOT-jar-with-dependencies.jar \
	--handler pt.ulisboa.tecnico.cnv.compression.CompressImageHandlerImpl \
	--runtime java11 \
	--timeout 5 \
	--memory-size 256 \
	--role arn:aws:iam::$AWS_ACCOUNT_ID:role/lambda-role

cd ../server
mvn clean
