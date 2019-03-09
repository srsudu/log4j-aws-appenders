// Copyright (c) Keith D Gregory
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.kdgregory.logging.testhelpers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;

import static org.junit.Assert.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.services.logs.AWSLogs;
import com.amazonaws.services.logs.model.*;

import com.kdgregory.aws.utils.logs.CloudWatchLogsReader;
import com.kdgregory.aws.utils.logs.CloudWatchLogsUtil;


/**
 *  A collection of utility methods to support integration tests. This is an
 *  instantiable class, preserving the AWS client.
 *  <p>
 *  This is not intended for production use outside of this library.
 */
public class CloudWatchTestHelper
{
    private final static long WAIT_FOR_READY_TIMEOUT_MS     = 60000;
    private final static long WAIT_FOR_DELETED_TIMEOUT_MS   = 60000;

    private Logger localLogger = LoggerFactory.getLogger(getClass());

    private AWSLogs client;
    private String logGroupName;


    public CloudWatchTestHelper(AWSLogs client, String logGroupName)
    {
        this.client = client;
        this.logGroupName = logGroupName;
    }


    /**
     *  Asserts that the stream contains the expected number of messages, and that
     *  they're in order (including case where writes are from different threads,
     *  as long as they follow the standard pattern).
     *  <p>
     *  Message count assertion is approximate, +/- 5 messages, to compensate for
     *  logstream rotation testing. This function returns the number of messages
     *  from the given stream so that they can be aggregated in multi-thread tests.
     */
    public int assertMessages(String logStreamName, int expectedMessageCount) throws Exception
    {
        List<OutputLogEvent> events = retrieveAllMessages(logStreamName, expectedMessageCount);

        assertEquals("number of events in " + logStreamName, expectedMessageCount, events.size());

        Map<Integer,Integer> lastMessageByThread = new HashMap<Integer,Integer>();
        for (OutputLogEvent event : events)
        {
            String message = event.getMessage().trim();
            Matcher matcher = MessageWriter.PATTERN.matcher(message);
            assertTrue("message matches pattern: " + message, matcher.matches());

            Integer threadNum = MessageWriter.getThreadId(matcher);
            Integer messageNum = MessageWriter.getMessageNumber(matcher);
            Integer prevMessageNum = lastMessageByThread.get(threadNum);
            if (prevMessageNum == null)
            {
                lastMessageByThread.put(threadNum, messageNum);
            }
            else
            {
                assertTrue("previous message (" + prevMessageNum + ") lower than current (" + messageNum + ")",
                           prevMessageNum.intValue() < messageNum.intValue());
            }
        }
        return events.size();
    }


    /**
     *  Reads all messages from a stream.
     *  <p>
     *  To work around eventual consistency, you can pass in an expected message
     *  count, and the function will make several attempts to read that number of
     *  messages, with a sleep in the middle.
     */
    public List<OutputLogEvent> retrieveAllMessages(String logStreamName, int expectedMessageCount)
    throws Exception
    {
        CloudWatchLogsReader reader = new CloudWatchLogsReader(client, logGroupName, logStreamName)
                                      .withRetrieveExitLogging(true);
        return reader.retrieve(expectedMessageCount, 60000);
    }


    /**
     *  Waits until the named logstream is available, throwing if it isn't available
     *  after one minute. If the log group isn't available, that's considered equal
     *  to the stream not being ready.
     */
    public void ensureLogStreamAvailable(String logStreamName)
    throws Exception
    {
        localLogger.debug("waiting for stream {} to be available", logStreamName);

        long timeoutAt = System.currentTimeMillis() + WAIT_FOR_READY_TIMEOUT_MS;
        while (System.currentTimeMillis() < timeoutAt)
        {
            if (isLogStreamAvailable(logStreamName))
                return;

            Thread.sleep(1000);
        }
        fail("stream \"" + logGroupName + "/" + logStreamName + "\" wasn't ready within " + WAIT_FOR_READY_TIMEOUT_MS/1000 + " seconds");
    }


    /**
     *  We leave the log group for post-mortem analysis, but want to ensure
     *  that it's gone before starting a new test.
     */
    public void deleteLogGroupIfExists()
    throws Exception
    {        
        CloudWatchLogsUtil.deleteLogGroup(client, logGroupName, WAIT_FOR_DELETED_TIMEOUT_MS);
        assertNull("log group " + logGroupName + " still exists", 
                   CloudWatchLogsUtil.describeLogGroup(client, logGroupName));
    }


    /**
     *  Deletes the log stream, waiting for it to be gone.
     */
    public void deleteLogStream(String logStreamName)
    throws Exception
    {
        CloudWatchLogsUtil.deleteLogStream(client, logGroupName, logStreamName, WAIT_FOR_DELETED_TIMEOUT_MS);
        assertNull("log stream " +  logGroupName + "/" + logStreamName  + " still exists", 
                   CloudWatchLogsUtil.describeLogStream(client, logGroupName, logStreamName));
    }


    /**
     *  Determines whether the named log stream is available.
     */
    public boolean isLogStreamAvailable(String logStreamName)
    {
        return CloudWatchLogsUtil.describeLogStream(client, logGroupName, logStreamName) != null;
    }


    /**
     *  Returns a description of the log group.
     */
    public LogGroup describeLogGroup()
    {
        DescribeLogGroupsRequest request = new DescribeLogGroupsRequest()
                                           .withLogGroupNamePrefix(logGroupName);
        DescribeLogGroupsResult response = client.describeLogGroups(request);
        for (LogGroup group : response.getLogGroups())
        {
            if (group.getLogGroupName().equals(logGroupName))
                return group;
        }
        throw new IllegalStateException("log group does not exist: " + logGroupName);
    }
}
