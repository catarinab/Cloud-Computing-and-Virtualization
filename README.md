# Cloud-Computing-and-Virtualization

## 1. Set-up:
To connect to your Amazon account before compiling the project you need to execute following steps with your AWS credentials:
- #### create file `config.sh` and save it to the `/scripts` folder with following content:
  ```
  #!/bin/bash

  DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"

  export PATH=<path-to-aws>
  export AWS_DEFAULT_REGION=<region>
  export AWS_ACCOUNT_ID=<your-account-id>
  export AWS_ACCESS_KEY_ID=<your-access-key-id>
  export AWS_SECRET_ACCESS_KEY=<your-secret-access-key>
  export AWS_EC2_SSH_KEYPAIR_PATH=<your-path-to-key>
  export AWS_SECURITY_GROUP=<security-group-name>
  export AWS_KEYPAIR_NAME=<your-keypair-name>

  ```

- #### create file `instance.properties` and save it to the `/service/src/main/java/pt/ulisboa/tecnico/cnv/webservice` folder with following content:
  ```
  instanceType=<instance-type e.g. t2.micro>
  keyName=<name-of-your-key>
  securityGroup=<security-group-name>

  ```

- #### create file `profile.credentials` and save it to the `/service/src/main/java/pt/ulisboa/tecnico/cnv/webservice` folder with following content:
  ```
  [default]
  aws_access_key_id = <your-access-key-id>
  aws_secret_access_key = <your-secret-access-key>
  ```

## 2. Compilation:

- to launch the project on AWS execute following command in the `/scripts` directory:
  ```
  source create-environment.sh

  ```

- to terminate the project with all of its AWS services execute following command in the `/scripts` directory:
  ```
  source delete-environment.sh

  ```

### 3. Request:
For sending the requests you can execute any of the HTTP requests for some of the 3 endpoints with specified parameters, e.g.:
  ```
  curl <service.dns>:8000/simulate\?generations=5\&world=1\&scenario=1
  ```
The `service.dns` is DNS of the EC2 instance running LoadBalancer and is printed to the console after the `create-environment` script is finished.

- #### Health-check:
  ```
  curl <service.dns>:8000/test
  ```
