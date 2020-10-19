#!/bin/sh
#sub_domain 三级域名
#projectName 项目名称
#group 开发组名

APPHOME=$!
sub_domain=$2
projectName=$3
deployEnv=$4
healthCheck=$5
group=$6

APPHOME="${APPHOME}"
consul_url='http://172.16.0.94:8500/v1/agent/service/register?replace-existing-checks=1'


json_file="${APPHOME}/${projectName}.json"


cat>${json_file}<<EOF
{
"id": "${projectName}",
"name": "${group}",
"address": "https://${sub_domain}.xueerqin.net${healthCheck}",
"port": 80,
"meta":{
        "Group": "${group}",
        "Project":"${projectName}"
},
"tags": ["${deployEnv}"],
"checks": [
{"http": "https://${sub_domain}.xueerqin.net${healthCheck}",
"interval": "60s"}]
}
EOF

curl --request PUT --data @${json_file} ${consul_url}
