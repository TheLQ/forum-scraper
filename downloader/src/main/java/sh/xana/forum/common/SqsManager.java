package sh.xana.forum.common;

import com.amazon.sqs.javamessaging.AmazonSQSExtendedClient;
import com.amazon.sqs.javamessaging.ExtendedClientConfiguration;
import java.io.Closeable;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sh.xana.forum.common.ipc.IScraperRequest;
import sh.xana.forum.common.ipc.ScraperDownload;
import sh.xana.forum.common.ipc.ScraperUpload;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.retry.RetryPolicy;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;
import software.amazon.awssdk.services.sqs.model.DeleteMessageBatchRequest;
import software.amazon.awssdk.services.sqs.model.DeleteMessageBatchRequestEntry;
import software.amazon.awssdk.services.sqs.model.DeleteQueueRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesRequest;
import software.amazon.awssdk.services.sqs.model.ListQueuesRequest;
import software.amazon.awssdk.services.sqs.model.ListQueuesResponse;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;
import software.amazon.awssdk.services.sqs.model.QueueDoesNotExistException;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequestEntry;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

public class SqsManager implements Closeable {
  private static final Logger log = LoggerFactory.getLogger(SqsManager.class);

  public static final int QUEUE_SIZE = 10;
  public static final String QUEUE_DOWNLOAD_PREFIX = "forumDownload-";
  private final SqsClient sqsClient;
  private final URI uploadQueueUrl;

  private final List<URI> cachedDownloadQueueUrls = new ArrayList<>();

  public SqsManager(CommonConfig config) {
    if (!config.isDebugMode()) {
      ClientOverrideConfiguration clientConfiguration =
          ClientOverrideConfiguration.builder()
              .retryPolicy(
                  RetryPolicy.builder()
                      // RetryCondition.defaultRetryCondition() is... a thing
                      // .retryCondition(new MyRetryCondition())
                      // aggressive retry forever due to network errors
                      .numRetries(50)
                      .build())
              .build();

      S3Client s3 = S3Client.builder().overrideConfiguration(clientConfiguration).build();
      SqsClient sqsClientBackend =
          SqsClient.builder().overrideConfiguration(clientConfiguration).build();

      ExtendedClientConfiguration extendedClientConfig =
          new ExtendedClientConfiguration()
              .withPayloadSupportEnabled(s3, config.get(config.ARG_QUEUE_S3), true);

      sqsClient = new AmazonSQSExtendedClient(sqsClientBackend, extendedClientConfig);
    } else {
      sqsClient = null;
    }

    uploadQueueUrl = Utils.toURI(config.get(config.ARG_QUEUE_UPLOADNAME));
  }

  public List<URI> getDownloadQueueUrls() {
    if (cachedDownloadQueueUrls.isEmpty()) {
      updateDownloadQueueUrls();
    }
    synchronized (cachedDownloadQueueUrls) {
      return cachedDownloadQueueUrls;
    }
  }

  public void updateDownloadQueueUrls() {
    synchronized (cachedDownloadQueueUrls) {
      ListQueuesResponse listQueuesResult =
          sqsClient.listQueues(
              ListQueuesRequest.builder().queueNamePrefix(QUEUE_DOWNLOAD_PREFIX).build());
      cachedDownloadQueueUrls.clear();
      for (String queue : listQueuesResult.queueUrls()) {
        cachedDownloadQueueUrls.add(Utils.toURI(queue));
      }
    }
  }

  public Map<URI, Integer> getDownloadQueueSizes() {
    var result = new HashMap<URI, Integer>();
    boolean queueNeedsUpdating = false;
    for (URI queueUrl : getDownloadQueueUrls()) {
      int isize;
      try {
        isize =
            Integer.parseInt(
                sqsClient
                    .getQueueAttributes(
                        GetQueueAttributesRequest.builder()
                            .queueUrl(queueUrl.toString())
                            .attributeNames(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES)
                            .build())
                    .attributes()
                    .get(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES));
      } catch (QueueDoesNotExistException e) {
        // silently catch, deleted queues can appear in subsequent list
        log.warn("Failed to get queue " + queueUrl + " might of been deleted", e);
        queueNeedsUpdating = true;
        continue;
      }
      result.put(queueUrl, isize);
    }

    if (queueNeedsUpdating) {
      log.info("refreshing cached download queue urls");
      updateDownloadQueueUrls();
    }

    return result;
  }

  public String newDownloadQueue(String domain) {
    domain = SqsManager.getQueueNameSafe(domain);
    return sqsClient
        .createQueue(CreateQueueRequest.builder().queueName(QUEUE_DOWNLOAD_PREFIX + domain).build())
        .queueUrl();
  }

  public void deleteQueue(URI uri) {
    sqsClient.deleteQueue(DeleteQueueRequest.builder().queueUrl(uri.toString()).build());
  }

  public void sendDownloadRequests(URI queue, List<ScraperDownload> entries) {
    send(queue, entries, false);
  }

  public List<RecieveRequest<ScraperDownload>> receiveDownloadRequests(URI queueUrl) {
    return receive(queueUrl, ScraperDownload.class);
  }

  public void sendUploadRequests(List<ScraperUpload> entries) {
    send(uploadQueueUrl, entries, true);
  }

  public List<RecieveRequest<ScraperUpload>> receiveUploadRequests() {
    return receive(uploadQueueUrl, ScraperUpload.class);
  }

  public void deleteUploadRequests(List<RecieveRequest<ScraperUpload>> messages) {
    deleteQueueMessage(uploadQueueUrl, messages);
  }

  private void send(URI queueUrl, List<? extends IScraperRequest> sends, boolean singlate) {
    try {
      if (singlate) {
        for (IScraperRequest send : sends) {
          String json = Utils.jsonMapper.writeValueAsString(send);
          sqsClient.sendMessage(
              SendMessageRequest.builder().queueUrl(queueUrl.toString()).messageBody(json).build());
        }
      } else {
        List<SendMessageBatchRequestEntry> messages = new ArrayList<>();
        for (IScraperRequest entry : sends) {
          String json = Utils.jsonMapper.writeValueAsString(entry);
          messages.add(
              SendMessageBatchRequestEntry.builder()
                  .messageBody(json)
                  // TODO: Need to force unique id's because getMessageId is duplicated?
                  .id(UUID.randomUUID().toString())
                  .build());
          if (messages.size() == 10 || entry == sends.get(sends.size() - 1)) {
            sqsClient.sendMessageBatch(
                SendMessageBatchRequest.builder()
                    .entries(messages)
                    .queueUrl(queueUrl.toString())
                    .build());
            messages.clear();
          }
        }
      }
    } catch (Exception e) {
      throw new RuntimeException("Failed to process for queue " + queueUrl, e);
    }
  }

  private <T> List<RecieveRequest<T>> receive(URI queueUrl, Class<T> clazz) {
    try {
      ReceiveMessageResponse receiveMessageResult =
          sqsClient.receiveMessage(
              ReceiveMessageRequest.builder()
                  .queueUrl(queueUrl.toString())
                  .maxNumberOfMessages(10)
                  .waitTimeSeconds(20)
                  .build());

      List<RecieveRequest<T>> result = new ArrayList<>();
      for (Message message : receiveMessageResult.messages()) {
        result.add(
            new RecieveRequest<T>(message, Utils.jsonMapper.readValue(message.body(), clazz)));
      }
      return result;

    } catch (Exception e) {
      throw new RuntimeException("Failed to process for queue " + queueUrl, e);
    }
  }

  public void deleteQueueMessage(URI queueUrl, List<RecieveRequest<ScraperUpload>> message) {
    sqsClient.deleteMessageBatch(
        DeleteMessageBatchRequest.builder()
            .queueUrl(queueUrl.toString())
            .entries(
                message.stream()
                    .map(RecieveRequest::message)
                    .map(
                        e ->
                            DeleteMessageBatchRequestEntry.builder()
                                // TODO: Need to force unique id's because getMessageId is duplicated?
                                .id(UUID.randomUUID().toString())
                                .receiptHandle(e.receiptHandle())
                                .build())
                    .collect(Collectors.toList()))
            .build());
  }

  @Override
  public void close() {
    log.info("closing SqsManager");
    sqsClient.close();
  }

  public static String getQueueName(URI queueUrl) {
    String queueName = queueUrl.getPath();
    queueName = queueName.substring(queueName.lastIndexOf('/') + 1);
    return queueName;
  }

  public static String getQueueDomain(URI queueUrl) {
    String queueName = getQueueName(queueUrl);
    if (!queueName.startsWith(QUEUE_DOWNLOAD_PREFIX)) {
      throw new RuntimeException("Not a download queue " + queueUrl + " got " + queueName);
    }
    return queueName.substring(QUEUE_DOWNLOAD_PREFIX.length());
  }

  public static String getQueueNameSafe(String str) {
    return str.replace(".", "_");
  }

  public static String getQueueNameSafeOrig(String str) {
    return str.replace("_", ".");
  }

//  /** Extends PredefinedRetryPolicies.DEFAULT_RETRY_CONDITION */
//  public static class MyRetryCondition extends SDKDefaultRetryCondition {
//
//    @Override
//    public boolean shouldRetry(
//        AmazonWebServiceRequest originalRequest,
//        AmazonClientException exception,
//        int retriesAttempted) {
//      boolean result = super.shouldRetry(originalRequest, exception, retriesAttempted);
//      log.warn(
//          "Retry {} continue {} due to exception {}",
//          retriesAttempted,
//          result,
//          ExceptionUtils.getRootCauseMessage(exception));
//      return result;
//    }
//  }
}
