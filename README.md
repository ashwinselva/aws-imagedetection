
# CS 643 - Cloud Computing - Project 1
## AWS Image Recognition Pipeline

---
### Demo Video: https://youtu.be/jJ1wPHE60dk 

## Overview

This project builds a distributed image recognition pipeline on AWS using two EC2 instances running in parallel:

- **Instance A** — Scans images from S3, detects cars using Rekognition, sends results to SQS
- **Instance B** — Reads image indexes from SQS, performs text recognition, writes results to `output.txt`

```
S3 Bucket (cs643-njit-project1)
        |
        v
[EC2 Instance A] CarRecognition.java
        | detects cars (confidence > 80%)
        v
   SQS Queue (transferQueue)
        |
        v
[EC2 Instance B] TextRecognition.java
        | detects text (confidence > 80%)
        v
     output.txt
```


## AWS Console Setup

### Start the Learner Lab
1. Log into your course portal
2. Go to **Modules → Learner Lab → Start Lab**
3. Wait for the status dot to turn **green**
4. Click **AWS** to open the AWS Management Console


### Launch Two EC2 Instances

Repeat the following steps **twice** (once for Instance A, once for Instance B):

1. Go to **EC2 → Launch Instance**
2. Set the name:
   - First instance: `ec2a`
   - Second instance: `ec2b`
3. **AMI**: Amazon Linux 2023 (free tier)
4. **Instance Type**: `t2.micro`
5. **Key Pair**: Select `vockey`
6. **Security Group** — allow the following inbound rules:
   - SSH (port 22) — Source: **My IP**
   - HTTP (port 80) — Source: **My IP**
   - HTTPS (port 443) — Source: **My IP**
7. **Storage**: Default 8 GB EBS
8. Click **Launch Instance**

---

### SSH into the Instance

```bash
# On your local machine
chmod 400 labsuser.pem
ssh -i labsuser.pem ec2-user@<YOUR_INSTANCE_PUBLIC_IP>
```

### Install Java and Maven

```bash
sudo yum update -y
sudo yum install java-17-amazon-corretto-devel -y
sudo yum install maven -y

# Verify installations
java -version
mvn -version
```

### Configure AWS Credentials

1. In Learner Lab, click **AWS Details**
2. Click **Show** next to AWS CLI credentials
3. Copy the credentials block, then on the EC2 instance:

```bash
mkdir -p ~/.aws
nano ~/.aws/credentials
```

Paste the credentials (replace with your actual values):
```
[default]
aws_access_key_id=ASIA...
aws_secret_access_key=...
aws_session_token=...
```

Set the region:
```bash
nano ~/.aws/config
```
```
[default]
region=us-east-1
output=json
```

##  Run the Pipeline


### On Instance B:
```bash
git clone https://github.com/ashwinselva/aws-imagedetection
cd  aws-imagedetection/text-recognition
java -jar target/text-recognition-1.0-SNAPSHOT-jar-with-dependencies.jar
```

### On Instance A (start second, in a separate terminal):
```bash
git clone https://github.com/ashwinselva/aws-imagedetection
cd aws-imagedetection/image-recognition
java -jar target/car-recognition-1.0-SNAPSHOT-jar-with-dependencies.jar

```

Both instances will now run in parallel:
- Instance A processes images and sends car image indexes to SQS
- Instance B picks up indexes from SQS and runs text detection immediately

Instance A sends `-1` to SQS when done, which signals Instance B to stop and write results.

---

