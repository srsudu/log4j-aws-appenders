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

package com.kdgregory.logging.testhelpers.cloudwatch;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Semaphore;

import com.amazonaws.services.logs.AWSLogs;
import com.amazonaws.services.logs.model.*;

import com.kdgregory.logging.aws.cloudwatch.CloudWatchWriterStatistics;
import com.kdgregory.logging.aws.cloudwatch.CloudWatchLogWriter;
import com.kdgregory.logging.aws.cloudwatch.CloudWatchWriterConfig;

import com.kdgregory.logging.common.LogWriter;
import com.kdgregory.logging.common.factories.ClientFactory;
import com.kdgregory.logging.common.factories.WriterFactory;
import com.kdgregory.logging.common.util.InternalLogger;


/**
 *  A proxy-based mock for the CloudWatch client that allows deep testing of
 *  writer behavior. Configure as desired, then use {@link #newWriterFactory}
 *  to create a <code>CloudWatchLogWriter</code> that uses this mock.
 *  <p>
 *  Most tests will need to verify that the writer correctly operates when
 *  running on a background thread. To coordinate that thread with the main
 *  (test) thread, the method {@link #allowWriterThread} blocks the main
 *  thread until the writer thread is able to execute {@link #putLogEvents}.
 *  That function in turn calls {@link #putLogEvents0}, which returns a
 *  default response. To perform some test-specific action such as throwing
 *  during write, override that latter method.
 */
public class MockCloudWatchClient
extends com.kdgregory.aws.utils.testhelpers.mocks.MockAWSLogs
{
    // the default list of names
    public static List<String> NAMES = Arrays.asList("foo", "bar", "barglet", "arglet",
                                                     "baz", "bargle", "argle", "fribble");

    // these semaphores coordinate the calls to PutLogEvents with the assertions
    // that we make in the main thread; note that both start unacquired
    private Semaphore allowMainThread = new Semaphore(0);
    private Semaphore allowWriterThread = new Semaphore(0);


    /**
     *  Default constructor. May not need another.
     */
    public MockCloudWatchClient()
    {
        super();
    }


    // FIXME - this moves to MockAWSLogs
    protected PutRetentionPolicyResult putRetentionPolicy(PutRetentionPolicyRequest request)
    {
        return new PutRetentionPolicyResult();
    }

//----------------------------------------------------------------------------
//  Public API
//----------------------------------------------------------------------------

    /**
     *  Creates a new WriterFactory, with the stock CloudWatch writer.
     */
    public WriterFactory<CloudWatchWriterConfig,CloudWatchWriterStatistics> newWriterFactory()
    {
        return new WriterFactory<CloudWatchWriterConfig,CloudWatchWriterStatistics>()
        {
            @Override
            public LogWriter newLogWriter(CloudWatchWriterConfig config, CloudWatchWriterStatistics stats, InternalLogger internalLogger)
            {
                return new CloudWatchLogWriter(config, stats, internalLogger, new ClientFactory<AWSLogs>()
                {
                    @Override
                    public AWSLogs createClient()
                    {
                        return MockCloudWatchClient.this.getInstance();
                    }
                });
            }
        };
    }


    /**
     *  Used for synchronous invocation tests: grants an "infinite" number of
     *  permits for the writer to proceed.
     */
    public void disableThreadSynchronization()
    {
        allowMainThread = new Semaphore(32768);
        allowWriterThread = new Semaphore(32768);
    }


    /**
     *  Pauses the main thread and allows the writer thread to proceed.
     */
    public void allowWriterThread() throws Exception
    {
        allowWriterThread.release();
        Thread.sleep(100);
        allowMainThread.acquire();
    }

//----------------------------------------------------------------------------
//  Methods to access invocation arguments
//----------------------------------------------------------------------------

    public CreateLogGroupRequest getMostRecentCreateLogGroupRequest()
    {
        return getMostRecentInvocationArg("createLogGroup", 0, CreateLogGroupRequest.class);
    }

    public CreateLogStreamRequest getMostRecentCreateLogStreamRequest()
    {
        return getMostRecentInvocationArg("createLogStream", 0, CreateLogStreamRequest.class);
    }

//----------------------------------------------------------------------------
//  PutLogEvents handler
//----------------------------------------------------------------------------

    @Override
    public final PutLogEventsResult putLogEvents(PutLogEventsRequest request)
    {
        try
        {
            allowWriterThread.acquire();
            return putLogEvents0(request);
        }
        catch (InterruptedException ex)
        {
            throw new RuntimeException("putLogEvents interrupted waiting for semaphore");
        }
        finally
        {
            allowMainThread.release();
        }
    }


    protected PutLogEventsResult putLogEvents(PutLogEventsRequest request)
    {
        return super.putLogEvents(request);
    }

}
