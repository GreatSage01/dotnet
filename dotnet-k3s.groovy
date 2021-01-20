//Active Choices
//https://plugins.jenkins.io/uno-choice/
//Generate with "pipeline-syntax" > "properties: Set job properties"
properties([
    parameters([choice(choices: ['deploy', 'rollback'], description: 'deploy------部署<br>rollback-----回滚', name: 'project_switch'),
        [$class: 'ChoiceParameter',
        choiceType: 'PT_SINGLE_SELECT',
        description: '项目',
        filterLength: 1,
        filterable: false,
        name: 'project_name',
        randomName: 'choice-parameter-17521565359649409',
        script: [
            $class: 'GroovyScript',
            fallbackScript: [classpath: [],
                sandbox: false,
                script: 'return [\'error\']'],
            script: [classpath: [],
                sandbox: false,
                script: 'return [\'curl\', \'http://172.16.0.94:9991/v1/gitlab?token=9jAzgpnqiXgxqbKu1KF2&groupname=server-side\'].execute().text.readLines()']
        ]],
        choice(choices: ['dev', 'master'], description: 'dev---------测试<br>master-----正式', name: 'project_branch'),
        string(defaultValue: '', description: '接收gitlab webhook触发用户（注：无需填写）', name: 'user_name', trim: true)
    ])
])

@Library('Jenkins-fuyu') _
def email = new org.email.email()
def git = new org.devops.git()
def dotnet_tools = new org.devops.dotnet()
def public_mod = new org.devops.public_mod()


pipeline{
    agent{
        kubernetes {
            yamlFile "jenkinsfile/config/jenkins-slave.yaml"
        }
    }
    options {
        // The Timestamper plugin adds timestamps to the console output of Jenkins jobs
        // https://plugins.jenkins.io/timestamper/
        timestamps()
        buildDiscarder(logRotator(daysToKeepStr: '30'))
    }

    environment{

        //技术栈
        Language='dotnet'
    }

    stages{
        stage("配置读取"){
            steps{
                script{
                    com.Read_com_values("${env.WORKSPACE}/jenkinsfile/Values/com_values.yaml")
                    com.Read_proj_values("${env.WORKSPACE}/jenkinsfile/Values/project_values.yaml")
                }
            }
        }


        stage("Git检出"){
            when{
                expression { "${project_switch}" == 'deploy' }
            }
            steps{
                script{

                    // 检出GIT上的源代码
                    git.gitcheckout([project_name:"${project_name}",
                               Gitlab_Branch:"${project_branch}",
                               Gitlab_Cred:"${Gitlab_Cred}",
                               Gitlab_Url:"${Gitlab_Url}"])
                
                    //git子模块更新
                    // dir(path:"./${project_name}"){
                    //     withEnv(["project_branch=${project_branch}"]){
                    //         sh '''
                    //             echo 'http://Jenkins:wdCyF6PDz7HVx8hJBfri@git.fjfuyu.net'> ~/.git-credentials
                    //             git config --global credential.helper store
                    //             git submodule foreach --recursive "(git checkout ${project_branch} && git pull --ff origin ${project_branch} ) || true" 
                    //             git submodule update --init --recursive
                    //         '''        
                        }
                    }
                }
            }
        }

        stage("初始化"){
            steps{
                script{
                    //项目名处理，全部小写,取第二位
                    def my_split = "${project_name}".toLowerCase().tokenize(".")
                    def projectName = my_split[1]
                    env.projectName="${projectName}"

                    //发布环境
                    env.deployEnv="${project_branch}"
                    
                    //部署k8s认证信息
                    k8s.KubeConfig("${deployEnv}")

                    //项目yaml文件保存路径
                    env.Yml_path="/home/jenkins/deployment/${deployEnv}/${project_name}"

                    //dotnet部署参数
                    dot.build_values("${env.WORKSPACE}/${project_name}/deploy-config/${deployEnv}-values.yaml")

                    //docker镜像,IMAGE_Name
                    public_mod.Harbor_tag([deployEnv: "${deployEnv}",projectName:"${serviceName}",HUB_Url:"${HUB_Url}"])

                    //k8s资源确认
                    public_mod.K8s_exist([Language:"${env.Language}",serviceName:"${serviceName}",nameSpaces:"${nameSpaces}"])
                    
                    //左侧展示
                    public_mod.Wrap([user_name: "${user_name}",projectName:"${serviceName}",reversion:"${tag_reversion}",deployEnv:"${deployEnv}"])
                }
            }
        }

        stage("构建"){
            when {
                expression { "${project_switch}" == 'deploy' }
            }
            steps{
                script{
                    def csproj_path="${env.WORKSPACE}/${project_name}/src/${projectName_cs}"
                    def app_path="${env.WORKSPACE}/${project_name}/app/publish"
                    dot.Build([projectName_cs:"${projectName_cs}",build_path:"${csproj_path}",app_path:"${app_path}"])
                }
            }
        }

        stage("Docker Image"){
            when{
                environment name: 'project_switch',value: 'deploy'
            }
            steps{
                script{
                    if( env.deployEnv == 'master' ){
                        timeout(time: 30, unit: 'MINUTES') {
                            input message:'deploy to master?', submitter: "${approver}"
                            echo "master docker image build"
                        }
                    }else if( env.deployEnv == 'dev' ){
                        echo "dev docker image build"
                    }
                    dot.BuildImage([project_name:"${project_name}",deployEnv:"${deployEnv}",IMAGE_Name:"${IMAGE_Name}"])
                }
            }
        }

        stage("部署k8s"){
            when{
                environment name: 'project_switch',value: 'deploy'
            }
            steps{
                script{
                    dot.Deploy()
                }
            }           
        }

        stage("创建ingress"){
            when{
                allOf {
                    expression { "${project_switch}" == 'deploy' };
                    not {expression { "${env.ingress_exist}" == "${env.serviceName}-tls" }}
                }
            }
            steps{
                script{
                    dot.Cread_ingress()
                    if( env.deployEnv == 'master' ){
                        com.Create_consul()
                    }
                }
            }
        }

        // 测试
        //stage('测试'){
        //    when{
        //        expression { "${project_switch}" == 'deploy' }
        //    }
        //    steps{
        //        timeout(time: 5, unit: 'SECONDS') {
        //            waitUntil{
        //                script{
        //                    def r_result=sh script:"curl http://172.16.0.94:9110/v1/deployment?namespace=${deployEnv}\\&project_name=${serviceName}",returnStdout: true
        //                    println r_result.result
        //                    def http_status=sh script:"curl https://${domainName}${healthCheck}",returnStdout: true
        //                    println http_status
        //                }
        //            } 
        //        }
        //    }
        //}

        //清理环境
        stage('清理'){
            when{
                expression { "${project_switch}" == 'deploy' }
            }
            steps{
                script{
                    sh '''/bin/bash
                        #docker rmi -f ${IMAGE_Name}
                        #rm -rf ${WORKSPACE}/${project_name}/app
                    '''
                }
            }
        }
        
        stage("验证"){
            steps{
                println "k8s_url:" + k8s_url
                println "k8s_credentials:"+k8s_credentials
                println "HUB_Url:"+HUB_Url
                println "HUB_Cred:"+HUB_Cred
                println "Gitlab_Url:"+ Gitlab_Url
                println "Gitlab_Cred: "+ Gitlab_Cred
                println "projectName:" + projectName
                println "dotnet 部署参数"
                println "deployEnv:"+deployEnv
                println "nameSpaces:"+nameSpaces
                println "projectName_cs:"+projectName_cs
                println "domainName:"+domainName
                println "serviceName:"+serviceName
                println "urlPath:"+urlPath
                println "healthCheck:"+healthCheck
                println "podNum:"+podNum
                println "Canary:"+Canary
                println "CanaryProd:"+CanaryProd
                println "headersKey:"+headersKey
                println "headersValue:"+headersValue
                println "其他："
                println "IMAGE_Name:"+IMAGE_Name
                println "svc_exist:"+svc_exist
                println "ingress_exist:"+ingress_exist
                println "approver:"+approver

            }
        }

        stage("回滚"){
           when{
               allOf{
                   expression { "${project_switch}" == "rollback" }
               }
            }
            steps {
                timeout(time: 30, unit: 'MINUTES') {
                    script {
                        com.Roll_back()
                    }
                }
            }
        }
    }
}