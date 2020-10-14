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
                Com.read_com_values("${env.WORKSPACE}/jenkinsfile/Values/com_value.yaml")
                script{
                    //读取项目配置文件
                    Project_values="${env.WORKSPACE}/jenkinsfile/Values/value.yaml"
                    if(fileExists(Com_values) == true){
                        //项目参数
                        println  Language+"项目参数"
                        def Env_proj=readYaml file: Project_values
                        //审批人
                        env.approver=Env_proj.approver.trim()
                    }else{
                         error("缺少项目参数文件！")
                    }
                }
            


            }
        }

        stage("验证"){
            steps{
                println "Prod_k8sUrl:" + Prod_k8sUrl
                println "Prod_k8sCred:"+Prod_k8sCred
                println "HUB_Url:"+HUB_Url
                println "HUB_Cred:"+HUB_Cred
                println "Gitlab_Url:"+ Gitlab_Url
                println "Gitlab_Cred: "+ Gitlab_Cred
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





    }
}