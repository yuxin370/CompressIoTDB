# CompressIoTDB
CompressIoTDB is a homomorphic compression query framework tailored for time series database, intergreted into Apache IoTDB. It supports homomorphic computation for key database operators with unified modular design, including filter, timestamp equivalence join, aggregation, group by sliding window, expression and slicing operators. System-level optimizations, such as late decompression and dynamic auxiliary restore, are employed to minimize decompression overhead and reduce I/O costs.

CompressIoTDB integrates seamlessly with the native database system, Apache IoTDB. It automatically determines whether to use direct computation on compressed data during query execution. Once integrated into the database, users do not need to specify additional parameters or configurations. By simply executing queries in the standard format, you can enjoy the performance improvements brought by the CompressIoTDB framework. :)

## Organization of Supplementary Materials
- **Appendix:** ``appendix.pdf``
- **Source Code:** ``/iotdb`` and ``/tsfile``
- **Example Test Dataset:** ``/data``
- **Bechmark:**``/benchamrk``

## Get Started

### Step 0: Environment Setup
Before installation, ensure that the device has a JDK version 1.8 or higher installed, and the `JAVA_HOME` environment variable is properly configured.

Set the maximum number of open files to 65,535.

### Step 1: Download CompressIoTDB Repository
To get started, install CompressIoTDB on either a server or a personal computer. 

Download the repository from the anonymized repository:
 
 ```shell
 https://anonymous.4open.science/r/CompressIoTDB-EEE8
```

### Step 2: Build CompressIoTDB

CompressIoTDB is integrated into Apache IoTDB seamlessly. You can build CompressIoTDB with the standard process of Apache IoTDB.

```shell
# install tsfile package locally
> cd your/path/to/CompressIoTDB/tsfile 
> mvn clean install -o -P with-java -DskipTests
# build iotdb
> cd your/path/to/CompressIoTDB/iotdb
> mvn clean package -o -pl distribution -am -DskipTests
```
For more information about system building, please refer to the official Apache IoTDB documentation.

### step 3: Start CompressIoTDB

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

## Run Example
A simple example of running CompressIoTDB is as follows.
### Step 1: Load Datasets
The example test dataset is a sythectic dataset (detailed in Section 7.1) with a length of $1 × 10^8$ and repetition rate of $0.5$.
```shell
# Ensure that you are in the sbin directory.
> ./tools/load-tsfile.sh -s your/path/to/CompressIoTDB/data/ -os none -of none
```
### Step 2: Execute Queries
After starting the client, you can execute queries using the interactive command-line interface. For SQL syntax, please refer to the [official SQL manual of Apache IoTDB](https://iotdb.apache.org/UserGuide/latest/SQL-Manual/SQL-Manual.html).
```shell
IoTDB> show devices
+-------------------+---------+--------+---+
|             Device|IsAligned|Template|TTL|
+-------------------+---------+--------+---+
|root.test_5.g_0.d_0|     true|    null|INF|
+-------------------+---------+--------+---+
Total line number = 1
It costs 0.091s
IoTDB> select variance(*) from root.test_5.g_0.d_0;
+---------------------------------+---------------------------------+---------------------------------+---------------------------------+---------------------------------+
|variance(root.test_5.g_0.d_0.s_1)|variance(root.test_5.g_0.d_0.s_0)|variance(root.test_5.g_0.d_0.s_3)|variance(root.test_5.g_0.d_0.s_2)|variance(root.test_5.g_0.d_0.s_4)|
+---------------------------------+---------------------------------+---------------------------------+---------------------------------+---------------------------------+
|              3.559513651408598E7|               2.95456119779592E7|             2.6568326185793143E7|              3.773923071965877E7|              4.468707258985075E7|
+---------------------------------+---------------------------------+---------------------------------+---------------------------------+---------------------------------+
Total line number = 1
It costs 11.482s
```

## IoT-Benchmark
We have extended [IoT-Benchmark](https://iotdb.apache.org/UserGuide/latest/Tools-System/Benchmark.html) to support *expression query with value filter*. The source code is in ``\benchmark`` directory, and the executable is in ``\benchmark\iot-benchmark-iotdb-1.3``. You can also download IoT-Benchmark from the open source [github repository]((https://github.com/thulab/iot-benchmark)), but it does not support *expression query with value filter*.

### Step 0: Build IoT-Benchmark
If you choose to use our executable jar package, skip this.
```shell
> git clone https://github.com/thulab/iot-benchmark.git
> cd iot-benchmark
> mvn clean package -Dmaven.test.skip=true
```

### Step 1: Configuration Setup

To run the example test dataset on IoT-Benchmark, a configuration example for the config file ``CompressIoTDB/benchmark/iot-benchmark-iotdb-1.3/conf/config.properties`` is as follows:

```shell
PORT=6667
DB_NAME=test_5
DEVICE_NUMBER=1
SENSOR_NUMBER=5
IS_CLIENT_BIND=false
CLIENT_NUMBER=2
OPERATION_PROPORTION=0:0:0:0:1:1:1:0:0:0:0:1 # Set to OPERATION_PROPORTION=0:0:0:0:1:1:1:0:0:0:0 if you use the benchmark built from the official repository, since expression queries with value filters are not supported.
START_TIME=2001-09-09T09:00:00+08:00
POINT_STEP=2500000000
QUERY_SENSOR_NUM=5
QUERY_DEVICE_NUM=1
QUERY_AGGREGATE_FUN=variance
QUERY_EXPRESSION=/ # If you use the benchmark built from the official repository, this field is not necessary.
QUERY_INTERVAL=25000000000
QUERY_LOWER_VALUE=7000
LOG_PRINT_INTERVAL=90
```

### Step 3: Run Benchmark
 Run the following statement to execute the benchmark; results can be found in ``CompressIoTDB/benchmark/iot-benchmark-iotdb-1.3/data/csvOutput``. Additionally, the original experimental results from Sections 7.3 and 7.4 of the paper can be found in ``CompressIoTDB/benchmark/iot-benchmark-iotdb-1.3/res_on_datasets_of_varying_size`` and ``CompressIoTDB/benchmark/iot-benchmark-iotdb-1.3/res_on_datasets_of_varying_size``.
```shell
> sh benchmark.sh -cf ./conf/config_modi/config.properties
```

## Copyright Notice

This project includes code from [Apache IoTDB](https://github.com/apache/iotdb) and [Apache TsFile](https://github.com/apache/tsfile), which is licensed under the [Apache License 2.0](http://www.apache.org/licenses/LICENSE-2.0).

