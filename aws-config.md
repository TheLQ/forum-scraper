server needs to be a regular long lived instance

* ec2 instance
* security group with SSH and HTTP
* IAM Role
** Trusted instance is ec2
** Add CloudwatchFullAccess

client

needs to be automatically built in a auto-boot ami

* Image builder
** custom pipeline
** component java16
*** add coretto java rpm key and repo
*** yum install java16
** component nodejs (built in)
** component forum-client
*** download systemd unit file to /usr/lib/systemd/system/forum-client.service
  

From someone who has some experience with azure but mostly uses ye olde gitlab-ci and docker-compose in production at work