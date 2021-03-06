#!/bin/bash
#version：1.1
#kong
#serviceName:服务名
#nameSpaces：命名空间
#domainName：域名
#url_path: 子目录
#is_path：URL是否有目录
#Yml_path：yaml报错路径
set -x

serviceName=$1
nameSpaces=$2
domainName=$3
urlPath=$4
fjfuyu_net=$5
Yml_path=$6



cd ${Yml_path}



if [ n${fjfuyu_net} == n"true" ] ;then
if [ n${nameSpaces} == n"master" ];then
domainName=${domainName}
fjfuyu_secretName=`echo ${domainName}|awk -F . '{print  $2 "-" $3 "-" $4 }'`
fjfuyu_hosts=`echo ${domainName}|awk -F . '{ print "*." $2 "." $3 "." $4 }'`
else
domainName=${domainName}
fjfuyu_secretName=`echo ${domainName}|awk -F . '{print  $2 "-" $3 "-" $4 }'`
fjfuyu_hosts=`echo ${domainName}|awk -F . '{ print "*." $2 "." $3 "." $4 }'`
fi

cat >${serviceName}-${nameSpaces}-ingress-kong.yaml<<EOF
apiVersion: extensions/v1beta1
kind: Ingress
metadata:
  name: ${serviceName}-tls
  namespace: ${nameSpaces}
  annotations:
    cert-manager.io/cluster-issuer: letsencrypt-fjfuyu
    kubernetes.io/ingress.class: "kong"
spec:
  tls:
  - secretName: ${fjfuyu_secretName}
    hosts:
    - "${fjfuyu_hosts}"
  rules:
  - host: ${domainName}
    http:
      paths:
      - path: ${url_path}
        backend:
          serviceName: ${serviceName}
          servicePort: 80
EOF
else
if [ n${nameSpaces} == n"master" ];then
domainName=${domainName}
else
domainName="t"${domainName}
fi
cat >${serviceName}-${nameSpaces}-ingress-kong.yaml<<EOF
apiVersion: extensions/v1beta1
kind: Ingress
metadata:
  name: ${serviceName}-tls
  namespace: ${nameSpaces}
  annotations:
    kubernetes.io/ingress.class: "kong"
spec:
  tls:
  - secretName: xueerqin-cert
    hosts:
    - '*.xueerqin.net'
  rules:
  - host: ${domainName}
    http:
      paths:
      - path: ${url_path}
        backend:
          serviceName: ${serviceName}
          servicePort: 80
EOF
fi