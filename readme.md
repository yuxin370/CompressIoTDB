# CompressIoTDB
CompressIoTDB is a homomorphic compression query framework tailored for time series database, intergreted into Apache IoTDB. It supports homomorphic computation for key database operators with unified modular design, including filter, timestamp equivalence join, aggregation, group by sliding window, expression and slicing operators. System-level optimizations, such as late decompression and dynamic auxiliary restore, are employed to minimize decompression overhead and reduce I/O costs.

CompressIoTDB integrates seamlessly with the native database system, Apache IoTDB. It automatically determines whether to use direct computation on compressed data during query execution. Once integrated into the database, users do not need to specify additional parameters or configurations. By simply executing queries in the standard format, you can enjoy the performance improvements brought by the CompressIoTDB framework. :)


# Get Started

## Step 0: Environment Setup
Before installation, ensure that the device has a JDK version 1.8 or higher installed, and the `JAVA_HOME` environment variable is properly configured.

Set the maximum number of open files to 65,535.

## Step 1: Download CompressIoTDB Repository
To get started, install CompressIoTDB on either a server or a personal computer. 

Download the repository from the anonymized repository:
 
 ```shell
 https://anonymous.4open.science/r/CompressIoTDB-EEE8
```

## Step 2: Build CompressIoTDB

CompressIoTDB is built on Apache IoTDB seamlessly. You can build CompressIoTDB with the standard process of Apache IoTDB.

```shell
# install tsfile package with offline mode
> cd your/path/to/CompressIoTDB/tsfile 
> mvn clean install -o -P with-java -DskipTests
# package iotdb
> cd your/path/to/CompressIoTDB/iotdb
> mvn clean package -o -pl distribution -am -DskipTests
```
For more information about system building, please refer to the official Apache IoTDB documentation.

## step 3: Start CompressIoTDB

Start CompressIoTDB server and client.

```shell
# enter sbin path
> cd your/path/to/CompressIoTDB/iotdb/distribution/target/apache-iotdb-1.3.3-SNAPSHOT-all-bin/apache-iotdb-1.3.3-SNAPSHOT-all-bin
# start server
> ./sbin/start-standalone.sh
# start client
> ./sbin/start-cli.sh -h 127.0.0.1 -p 6667 -u root -pw root
```
The command line cli is interactive, so you should see the welcome logo and statements if everything is ready:
```plain
---------------------
Starting IoTDB Cli
---------------------
 _____       _________  ______   ______    
|_   _|     |  _   _  ||_   _ `.|_   _ \   
  | |   .--.|_/ | | \_|  | | `. \ | |_) |  
  | | / .'`\ \  | |      | |  | | |  __'.  
 _| |_| \__. | _| |_    _| |_.' /_| |__) | 
|_____|'.__.' |_____|  |______.'|_______/  version 1.3.3-SNAPSHOT 

Successfully login at 127.0.0.1:6667
IoTDB> 
```