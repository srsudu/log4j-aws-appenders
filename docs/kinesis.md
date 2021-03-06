# Kinesis

The Kinesis Streams appender is intended to be an entry point for log analytics, either
as a direct feed to an analytics application, or via Kinesis Firehose to ElasticSearch
or other destinations (note that this can also be an easy way to back-up logs to S3).

The Kinesis appender provides the following features:

* Configurable destination stream, with substitution variables to specify stream name.
* Auto-creation of streams, with configurable number of shards.
* JSON messages (via [JsonLayout](jsonlayout.md)).
* Configurable discard in case of network connectivity issues.


## Configuration

The appender provides the following properties (also described in the JavaDoc):

Name                | Description
--------------------|----------------------------------------------------------------
`streamName`        | The name of the Kinesis stream that will receive messages; may use [substitutions](substitutions.md). No default value.
`partitionKey`      | A string used to assign messages to shards; see below for more information.
`autoCreate`        | If present and "true", the stream will be created if it does not already exist.
`shardCount`        | When creating a stream, specifies the number of shards to use. Defaults to 1.
`retentionPeriod`   | When creating a stream, specifies the retention period for messages in hours. Per AWS, the minimum is 24 (the default) and the maximum is 168 (7 days). Note that increasing retention time increases the per-hour shard cost.
`synchonous`        | If `true`, the appender will operate in [synchronous mode](design.md#synchronous-mode), sending messages from the invoking thread on every call to `append()`.
`batchDelay`        | The time, in milliseconds, that the writer will wait to accumulate messages for a batch. See the [design doc](design.md#message-batches) for more information.
`discardThreshold`  | The threshold count for discarding messages; default is 10,000. See the [design doc](design.md#message-discard) for more information.
`discardAction`     | Which messages will be discarded once the threshold is passed: `oldest` (the default), `newest`, or `none`.
`clientFactory`     | Specifies the fully-qualified name of a static method that will be invoked to create the AWS service client. See the [service client doc](service-client.md#client-creation) for more information.
`clientRegion`      | Specifies a non-default region for the client. See the [service client doc](service-client.md#endpoint-configuration) for more information.
`clientEndpoint`    | Specifies a non-default endpoint; only supported for clients created via constructor. See the [service client doc](service-client.md#endpoint-configuration) for more information.
`useShutdownHook`   | Controls whether the appender uses a shutdown hook to attempt to process outstanding messages when the JVM exits. This is `true` by default; set to `false` to disable. See [docs](design.md#shutdown-hooks) for more information.


### Example: Log4J 1.x

```
log4j.appender.kinesis=com.kdgregory.log4j.aws.KinesisAppender
log4j.appender.kinesis.streamName=logging-stream
log4j.appender.kinesis.partitionKey={pid}
log4j.appender.kinesis.batchDelay=500

log4j.appender.kinesis.layout=com.kdgregory.log4j.aws.JsonLayout
log4j.appender.kinesis.layout.enableHostname=true
log4j.appender.kinesis.layout.enableLocation=true
log4j.appender.kinesis.layout.tags=applicationName={env:APP_NAME}
```


### Example: Logback

```
<appender name="KINESIS" class="com.kdgregory.logback.aws.KinesisAppender">
    <streamName>logging-stream</streamName>
    <partitionKey>{pid}</partitionKey>
    <batchDelay>500</batchDelay>
    <layout class="com.kdgregory.logback.aws.JsonLayout">
        <enableHostname>true</enableHostname>
        <enableLocation>true</enableLocation>
        <tags>applicationName={env:APP_NAME}</tags>
    </layout>
</appender>
```


## Permissions

To use this appender you will need to grant the following IAM permissions:

* `kinesis:DescribeStream`
* `kinesis:PutRecords`

To auto-create a stream you must grant these additional permissions:

* `kinesis:CreateStream`
* `kinesis:IncreaseStreamRetentionPeriod`


## Stream management

You will normally pre-create the Kinesis stream and adjust its retention period and number of
shards based on your environment.

To support testing (and because it was the original behavior, copied from the CloudWatch appender),
you can optionally configure the appender to create the stream if it does not already exist. Unlike
the CloudWatch appender, if you delete the stream during use it will not be re-created even if you
specify this parameter.


## Partition Keys

Kinesis supports high-performance parallel writes via multiple shards per stream: each shard
can accept up to 1,000 records and/or 1 MB of data per second. To distribute data between
shards, Kinesis requires each record to have a partition key, and hashes that partition key
to determine which shard is used to store the record.

The Kinesis appender allows you to set an explicit partition key, which is applied to all
messages generated by a single appender. By default this key is the application startup
timestamp, which means that all messages from that application will go to the same shard.
In most cases this is sufficient, and there's no reason to configure this parameter.

If, however, you have an application that generates a high volume of log messages, you can
get improved performance (or at least reduce the likelihood of throttling) by generating a
per-record random partition key (note that you will also need to have multiple shards in
the stream). 

You enable this behavior by specifying `{random}` as the partition key value (note: this
value is case sensitive):

```
log4j.appender.kinesis.partitionKey={random}
```

For backwards compatibility, you may specify an empty partition key for Log4J 1.x (this was
the original behavior, but Logback doesn't support empty configuration values). Note that
`getPartitionKey()` returns whatever value you set (ie, it doesn't translate an empty string
to `"{random}"`).
