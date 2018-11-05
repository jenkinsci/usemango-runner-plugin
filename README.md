# useMango Runner for Jenkins 2.x

This plugin allows useMango tests to be executed as a Jenkins job on Jenkins 2.x.

Main features:
- Execute useMango tests on Jenkins
- Filter tests using account settings
- Run tests in parallel across multiple nodes
- Supports JUnit reporting
 
## Installation
 
1) Clone this repository from github

2) Build the plugin:
```
mvn clean package
```

3) Install the plugin:
 - Copy to your _%JENKINS_HOME%\plugins_ directory, OR
 - Login to Jenkins and upload your plugin (_Jenkins -> Manage Jenkins -> Manage Plugins -> Advanced_)

## Usage

###### Configuring

1) Navigate to _Jenkins -> Manage Jenkins -> Configure System_

2) Locate the _useMango Location_ section and enter the _useMango URL_ and your account credentials (as [Credentials](https://wiki.jenkins-ci.org/display/JENKINS/Credentials+Plugin))

###### Creating a Job

1) Create a new Freestyle project and configure:

- Add the build step _Run useMango tests_ 
- Enter your _Project ID_ (i.e. the name of your project in your useMango account)
- Add further filtering where desired
- Click the _Validate_ button to validate your settings

2) Optional:  Add the post-build action _Publish JUnit test result report_ and enter _results/*.xml_ in the _Test report XMLs_ input box.

## Dependencies

 - [Credentials Plugin](https://wiki.jenkins-ci.org/display/JENKINS/Credentials+Plugin)
 - [Test Results Analyzer Plugin](https://wiki.jenkins.io/display/JENKINS/Test+Results+Analyzer+Plugin)
 - Windows node(s) with useMango installed: To run useMango tests you must have Windows slave nodes configured in your Jenkins setup, with useMango installed on each node.  To run useMango tests on the Windows nodes, check _Execute tests on labelled nodes_ and enter the relevant label in the _Label Expression_ input box.