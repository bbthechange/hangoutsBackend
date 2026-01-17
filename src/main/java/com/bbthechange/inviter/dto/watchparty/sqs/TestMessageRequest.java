package com.bbthechange.inviter.dto.watchparty.sqs;

/**
 * Request DTO for the test message endpoint.
 * Allows testing of SQS message processing in staging environments.
 */
public class TestMessageRequest {

    private String queueType;
    private String messageBody;

    public TestMessageRequest() {}

    public TestMessageRequest(String queueType, String messageBody) {
        this.queueType = queueType;
        this.messageBody = messageBody;
    }

    /**
     * Queue type: "tvmaze-updates" or "episode-actions"
     */
    public String getQueueType() {
        return queueType;
    }

    public void setQueueType(String queueType) {
        this.queueType = queueType;
    }

    /**
     * JSON message body to send to the queue.
     */
    public String getMessageBody() {
        return messageBody;
    }

    public void setMessageBody(String messageBody) {
        this.messageBody = messageBody;
    }

    @Override
    public String toString() {
        return "TestMessageRequest{" +
                "queueType='" + queueType + '\'' +
                ", messageBody='" + messageBody + '\'' +
                '}';
    }
}
