AWS substrate
===

Thin CloudFormation stack for the two Empathy PaaS machines: a public proxy
and a test/build host. Docker, Jenkins, Caddy, and Tailscale stay in
`test/` and `proxy/` Ansible.

This does **not** manage the live click-ops boxes
(`i-061ba2aaec81a3b28` / `i-0bb50e0082e7485c5` in `eu-west-2`).
`delete-stack` only touches resources this template created.


What it creates
---

* New VPC `10.0.0.0/16` (not the account default VPC)
* One public subnet, internet gateway, default route
* RSA key pair (PEM stored in SSM, not as a stack output)
* Proxy (`t3.micro`): SSH, HTTP 80, HTTPS 443, Tailscale UDP 41641, **Elastic IP**
* Test (`t3.small`): SSH, Tailscale UDP 41641, ephemeral public IP
* Current Ubuntu 24.04 AMI via SSM (live boxes used 26.04)


Prerequisites
---

* AWS CLI configured (`aws sts get-caller-identity`)
* No existing key pair required — the stack generates one


Create
---

<pre><code class="language-bash">aws cloudformation create-stack \
  --region eu-west-2 \
  --stack-name empathy-paas \
  --template-body file://infra/aws/template.yml \
  --parameters \
    ParameterKey=SshCidr,ParameterValue=0.0.0.0/0

aws cloudformation wait stack-create-complete \
  --region eu-west-2 \
  --stack-name empathy-paas

aws cloudformation describe-stacks \
  --region eu-west-2 \
  --stack-name empathy-paas \
  --query 'Stacks[0].Outputs'

bash infra/aws/fetch-keypair.sh empathy-paas
</code></pre>

`fetch-keypair.sh` writes `~/.config/base-docker/empathy-paas.pem` (mode 0600).
Put that PEM in Ansible vault as both `pem.proxy` and `pem.test` (same key
is installed on both instances).

Tighten `SshCidr` to `YOUR.IP/32` when you know it.

To match the live Ubuntu 26.04 image:

<pre><code class="language-bash">ParameterKey=AmiParameter,ParameterValue=/aws/service/canonical/ubuntu/server/26.04/stable/current/amd64/hvm/ebs-gp3/ami-id
</code></pre>


Map outputs into Ansible
---

Write stack outputs into `~/.config/base-docker/settings.yml`:

| Output | Settings key |
|---|---|
| `ProxyPublicIp` | `hosts.proxy_ip` |
| `TestPublicIp` | `hosts.test_ip` |

SSH user on these AMIs is `ubuntu` (`hosts.proxy_user` / `hosts.test_user`).
`hosts.test_ts_ip` is filled after Tailscale joins (Ansible, not this stack).

Then the existing playbooks:

<pre><code class="language-bash">cd proxy && ansible-playbook init.yml && ansible-playbook boot.yml
cd ../test && ansible-playbook init.yml && ansible-playbook boot.yml
</code></pre>


Tear down
---

<pre><code class="language-bash">aws cloudformation delete-stack --region eu-west-2 --stack-name empathy-paas
aws cloudformation wait stack-delete-complete --region eu-west-2 --stack-name empathy-paas
</code></pre>

That removes the VPC, instances, EIP, security groups, **and the generated
key pair** (including the SSM parameter). Keep a local copy of the PEM if
you still need SSH to boxes that outlive the stack. It does not terminate
the older default-VPC instances.
