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
                    //读取公共配置参数文件
                    Com_values="${env.WORKSPACE}/jenkinsfile/Values/com_value.yaml"
                    if(fileExists(Com_values) == true){
                        //提取公共参数
                        println  "构建公共参数读取"
                        def Env_com = readYaml  file: Com_values
                        //正式k8s
                        env.prod_k8sUrl=Env_com.prod_k8sUrl.trim()
                        env.prod_k8sCred=Env_com.prod_k8sCred.trim()
                        //harbor
                        env.HUB_Url=Env_com.HUB_Url.trim()
                        env.HUB_Cred=Env_com.HUB_Cred.trim()
                        //gitlab
                        env.GIT_Url=Env_com.GIT_Url.trim()+ "/${project_name}" + '.git'
                        env.GIT_Cred=Env_com.GIT_Cred.trim()
                    }else{
                        error("缺少公共配置文件！")
                    }
                    //读取项目配置文件
                    Project_values="${env.WORKSPACE}/jenkinsfile/Values/value.yaml"
                    if(fileExists(Com_values) == true){
                        //项目参数
                        println  Language+"项目参数"
                        def Env_proj=readYaml file: Project_values
                        //审批人
                        env.approver=Env_proj.approver.trim()
                    }
                }
            }
        }

        stage("Git检出"){
            when{
                expression { "${project_switch}" == 'deploy' }
            }
            steps{
                script{
                    println "GIT_Url:" + GIT_Url
                    // 检出GIT上的源代码
                    git.gitcheckout([project_name:"${project_name}",
                               GIT_Branch:"${project_branch}",
                               GIT_Cred:"${GIT_Cred}",
                               GIT_Url:"${GIT_Url}"])
                }
            }
        }





    }
}