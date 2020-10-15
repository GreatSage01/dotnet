//Active Choices
//https://plugins.jenkins.io/uno-choice/
//Generate with "pipeline-syntax" > "properties: Set job properties"
properties([
    parameters([choice(choices: ['deploy', 'rollback'], description: 'deploy------部署<br>rollback-----回滚', name: 'project_switch'),
        [$class: 'ChoiceParameter',
        choiceType: 'PT_MULTI_SELECT',
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
                script: 'return [\'curl\', \'http://172.16.0.94:8888/v1/gitlab?token=9jAzgpnqiXgxqbKu1KF2\'].execute().text.readLines()']
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
        buildDiscarder(logRotator(numToKeepStr: '30'))
    }

    environment{

        //技术栈
        Language='dotnet'
    }

    stages{
        stage("配置读取"){
            steps{
                script{
                    com.Read_com_values("${env.WORKSPACE}/jenkinsfile/Values/com_value.yaml")
                    com.Read_proj_values("${env.WORKSPACE}/jenkinsfile/Values/value.yaml")
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
                }
            }
        }

        stage("初始化"){
            steps{
                script{
                    dotnet_tools.ProjectName_small("${project_name}")

                    //发布环境
                    env.Deploy_env="${project_branch}"
                    
                    //部署k8s认证信息
                    k8s.KubeConfig("${Deploy_env}")

                    //项目yaml文件保存路径
                    env.Yml_path="/home/jenkins/deployment/${Deploy_env}/${project_name}"

                    //dotnet部署参数
                    dot.build_values("${env.WORKSPACE}/${project_name}/deploy-config/${Deploy_env}-values.yaml")

                    //docker镜像
                    public_mod.Harbor_tag([Deploy_env: "${Deploy_env}",projectName:"${serviceName}"])

                    //k8s资源确认
                    public_mod.K8s_exist([Language:"java",serviceName:"${serviceName}",nameSpaces:"${nameSpaces}"])
                    
                    //左侧展示
                    public_mod.Wrap([user_name: "${user_name}",projectName:"${projectName}",reversion:"${tag_reversion}",Deploy_env:"${Deploy_env}"])
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
                    def app_path="${WORKSPACE}/${project_name}/app/publish"
                    //build
                    dot.Build([projectName_cs:"${projectName_cs}",csproj_path:"${csproj_path}",app_path:"${app_path}",])
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
                println "isCanary:"+isCanary
                println "CanaryProd:"+CanaryProd
                println "headersKey:"+headersKey
                println "headersValue:"+headersValue
                println "其他："
                println "IMAGE_Name:"+IMAGE_Name
                println "svc_exist:"+svc_exist
                println "ingress_exist:"+ingress_exist

            }
        }




    }
}