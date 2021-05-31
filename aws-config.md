server needs to be a regular long lived instance

* ec2 instance
* security group with SSH and HTTP
* IAM Role
** Trusted instance is ec2
** Add CloudwatchFullAccess

client

needs to be automatically built in a auto-boot ami

* Setup
  * java16
    * rpm --import https://yum.corretto.aws/corretto.key
    * curl -L -o /etc/yum.repos.d/corretto.repo https://yum.corretto.aws/corretto.repo
    * yum install -y java-16-amazon-corretto-devel

  * scrape
    * mkdir -p /opt/app
    * cd /opt/app
    * curl -v -O http://forum.xana.sh:8080/file/dependencies.jar
    * curl -v -O http://forum.xana.sh:8080/file/scrape-download-1.0-SNAPSHOT.jar
  * scrape systemd
    * cd /usr/lib/systemd/system
    * curl -v -O http://forum.xana.sh:8080/file/forum-client.unit
    * systemctl daemon-reload
    * systemctl enable forum-client
* Image builder (non working)
  * custom pipeline
  * component java16
  * component scrape
  * VPC, VPC Subnet, Security Group all match


From someone who has some experience with azure but mostly uses ye olde gitlab-ci and docker-compose in production at work