#!/bin/bash
#dotnet部署k8s的yaml文件


serviceName=$1
image=$2
pod_num=$3
nameSpaces=$4
environment=$5
healthCheck=$6
work_path=$7


mkdir -p ${work_path}
cd ${work_path}


cat >${serviceName}-${nameSpaces}.yaml<<EOF
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: ${serviceName}
  namespace: ${nameSpaces}
spec:
  replicas: ${pod_num}
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxSurge: 2
      maxUnavailable: 0
  selector:
    matchLabels:
      app: ${serviceName}
  template:
    metadata:
      labels:
        app: ${serviceName}
    spec:
      affinity:
        nodeAffinity:
          preferredDuringSchedulingIgnoredDuringExecution:
          - weight: 100
            preference:
              matchExpressions:
              - key: environment
                operator: In
                values:
                - ${environment}
      imagePullSecrets:
      - name: hubsecret
      containers:
      - name: ${serviceName}
        image: ${image}
        imagePullPolicy: Always
        env:
          - name: TZ
            value: Asia/Shanghai
        resources:
          limits:
            cpu: 2000m
            memory: 4Gi
          requests:
            cpu: 50m
            memory: 50Mi
        ports:
        - containerPort: 80
          name: web
          protocol: TCP
        readinessProbe:
          httpGet:
            path: ${healthCheck}
            port: 80
            scheme: HTTP
          initialDelaySeconds: 30
          periodSeconds: 15
          timeoutSeconds: 5
        livenessProbe:
          httpGet:
            path: ${healthCheck}
            port: 80
            scheme: HTTP
          initialDelaySeconds: 30
          periodSeconds: 15
          timeoutSeconds: 5

---
kind: Service
apiVersion: v1
metadata:
  labels:
      app: ${serviceName}
  name: ${serviceName}
  namespace: ${nameSpaces}
spec:
  ports:
  - port: 80
    targetPort: 80
    name: web
  selector:
    app: ${serviceName}
EOF
