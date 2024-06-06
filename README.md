# jcc2c

A tool written in Java to convert [JaCoCo](https://github.com/jacoco/jacoco) XML 
coverage reports to [Cobertura](https://github.com/cobertura/cobertura) XML 
coverage reports. We also provide the same tool written in Nashorn Javascript (
found [here](https://github.com/cjmach/jcc2c/tree/main/src/main/javascript/)), 
but bear in mind that Nashorn engine was deprecated in JDK 11 (and removed in 
JDK 15), in favor of GraalVM Javascript.

`jcc2c` is still a necessity in case you are using tools or services that only 
support Cobertura XML reports.

# Usage

```console
$ java -jar jcc2c.jar -h
Usage: jcc2c [-hv] -i=FILE -o=FILE [SOURCE DIR...]
      [SOURCE DIR...]   One or more source directories.
  -h, --help            Print help and exit.
  -i, --input=FILE      Path to JaCoCo XML coverage report input file. If set
                          to '-', input will be read from stdin. Required 
                          option.
  -o, --output=FILE     Path to Cobertura XML coverage report output file. If
                          set to '-', output will be writen to stdout. Required 
                          option.
  -v, --version         Print version and exit.

```

Example:

```console
$ java -jar jcc2c.jar -i jacoco-report.xml -o cobertura-report.xml src/main/java
```

# Building

To build this project you need:

- Java Development Kit 11
- Apache Maven 3.6.x or above

Assuming all the tools can be found on the PATH, simply go to the project 
directory and run the following command:

```console
$ mvn -B package
```

# Releasing

Go to the project directory and run the following commands:

```console
$ mvn -B release:prepare
$ mvn -B release:perform -Darguments='-Dmaven.deploy.skip=true' 
```

It will automatically assume the defaults for each required parameter, namely,
`releaseVersion` and `developmentVersion`. If it's necessary to control the values 
of each version, the `release:prepare` command can be run as follows:

```console
$ mvn -B release:prepare -DreleaseVersion={a release version} -DdevelopmentVersion={next version}-SNAPSHOT
```