#!/bin/bash

source config.sh

# Install java.
cmd="sudo yum update -y; sudo yum install java-11-amazon-corretto.x86_64 -y;"
ssh -o StrictHostKeyChecking=no -i $AWS_EC2_SSH_KEYPAR_PATH ec2-user@$(cat instance.dns) $cmd

#Install maven.
cmd="sudo wget http://repos.fedorapeople.org/repos/dchen/apache-maven/epel-apache-maven.repo -O /etc/yum.repos.d/epel-apache-maven.repo && sudo sed -i 's#\$releasever#7#g' /etc/yum.repos.d/epel-apache-maven.repo && sudo yum install -y apache-maven"
ssh -o StrictHostKeyChecking=no -i $AWS_EC2_SSH_KEYPAR_PATH ec2-user@$(cat instance.dns) $cmd

# Install web server.
scp -o StrictHostKeyChecking=no -i $AWS_EC2_SSH_KEYPAR_PATH -r $DIR/../../Cloud-Computing-and-Virtualization ec2-user@$(cat instance.dns):

# Build web server.
cmd="cd Cloud-Computing-and-Virtualization/ && mvn clean install"
ssh -o StrictHostKeyChecking=no -i $AWS_EC2_SSH_KEYPAR_PATH ec2-user@$(cat instance.dns) $cmd 

# Setup web server to start on instance launch.
cmd="echo \"java -cp /home/ec2-user/Cloud-Computing-and-Virtualization/webserver/target/webserver-1.0.0-SNAPSHOT-jar-with-dependencies.jar -javaagent:/home/ec2-user/Cloud-Computing-and-Virtualization/webserver/target/webserver-1.0.0-SNAPSHOT-jar-with-dependencies.jar=ICount:pt.ulisboa.tecnico.cnv.insectwar,pt.ulisboa.tecnico.cnv.foxrabbit,pt.ulisboa.tecnico.cnv.compression:output pt.ulisboa.tecnico.cnv.webserver.WebServer\" | sudo tee -a /etc/rc.local; sudo chmod +x /etc/rc.local"
ssh -o StrictHostKeyChecking=no -i $AWS_EC2_SSH_KEYPAR_PATH ec2-user@$(cat instance.dns) $cmd
