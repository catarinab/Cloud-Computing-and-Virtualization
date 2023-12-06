#!/bin/bash

source config.sh

# Install java.
cmd="sudo yum update -y; sudo yum install java-11-amazon-corretto.x86_64 -y;"
ssh -o StrictHostKeyChecking=no -i $AWS_EC2_SSH_KEYPAIR_PATH ec2-user@$(cat instance.dns) $cmd

#Install maven.
cmd="sudo wget http://repos.fedorapeople.org/repos/dchen/apache-maven/epel-apache-maven.repo -O /etc/yum.repos.d/epel-apache-maven.repo && sudo sed -i 's#\$releasever#7#g' /etc/yum.repos.d/epel-apache-maven.repo && sudo yum install -y apache-maven"
ssh -o StrictHostKeyChecking=no -i $AWS_EC2_SSH_KEYPAIR_PATH ec2-user@$(cat instance.dns) $cmd

# Install web server.
scp -o StrictHostKeyChecking=no -i $AWS_EC2_SSH_KEYPAIR_PATH -r $DIR/../service ec2-user@$(cat instance.dns):

# Build web server.
cmd="cd service/ && mvn clean install"
ssh -o StrictHostKeyChecking=no -i $AWS_EC2_SSH_KEYPAIR_PATH ec2-user@$(cat instance.dns) $cmd 

# Setup web server to start on instance launch.
cmd="echo \"java -jar /home/ec2-user/service/target/webservice-1.0.0-SNAPSHOT-jar-with-dependencies.jar\" | sudo tee -a /etc/rc.local; sudo chmod +x /etc/rc.local"
ssh -o StrictHostKeyChecking=no -i $AWS_EC2_SSH_KEYPAIR_PATH ec2-user@$(cat instance.dns) $cmd

cmd="java -jar /home/ec2-user/service/target/webservice-1.0.0-SNAPSHOT-jar-with-dependencies.jar"
ssh -o StrictHostKeyChecking=no -i $AWS_EC2_SSH_KEYPAIR_PATH ec2-user@$(cat instance.dns) $cmd

