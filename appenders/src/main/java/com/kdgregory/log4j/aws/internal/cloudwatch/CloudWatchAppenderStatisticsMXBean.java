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

package com.kdgregory.log4j.aws.internal.cloudwatch;

import java.util.Date;
import java.util.List;

/**
 *  Defines the JMX Bean interface for {@link CloudWatchAppenderStatistics}.
 */
public interface CloudWatchAppenderStatisticsMXBean
{
    /**
     *  Returns the actual log group name for the appender, after substitutions.
     */
    String getActualLogGroupName();


    /**
     *  Returns the actual log stream name for the appender, after substitutions.
     */
    String getActualLogStreamName();


    /**
     *  Returns the most recent error from the writer. This will be null if there
     *  have been no errors.
     */
    String getLastErrorMessage();


    /**
     *  Returns the timestamp of the most recent error from the writer. This will be
     *  null if there have been no errors.
     */
    Date getLastErrorTimestamp();


    /**
     *  Returns the stack trace of the most recent error from the writer. This will be
     *  null if there have been no errors or if the error did not have an associated
     *  exception.
     */
    List<String> getLastErrorStacktrace();


    /**
     *  Returns the number of messages successfully sent to the logstream, by all
     *  writers.
     */
    int getMessagesSent();


    /**
     *  Returns the number of messages discarded by the current writer's message queue.
     *  Note that writer rotation (which can happen due to errors) will reset this.
     */
    int getMessagesDiscardedByCurrentWriter();
}
