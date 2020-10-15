#!/bin/sh
#sub_domain 三级域名
#projectName 项目名称
#group 开发组名

sub_domain=$1
projectName=$2
group=$3

APPHOME="/root/k8s-template/json/data/${group}"
consul_url='http://172.16.0.94:8500/v1/agent/service/register?replace-existing-checks=1'


`mkdir -p "${APPHOME}"`
json_file="${APPHOME}/${projectName}.json"


cat>${json_file}<<EOF
{
"id": "${projectName}",
"name": "${group}",
"address": "https://${sub_domain}.xueerqin.net/${projectName}/health",
"port": 80,
"meta":{
        "Group": "${group}",
        "Project":"${projectName}"
},
"tags": ["master"],
"checks": [
{"http": "https://${sub_domain}.xueerqin.net/${projectName}/health",
"interval": "60s"}]
}
EOF

curl --request PUT --data @${json_file} ${consul_url}
