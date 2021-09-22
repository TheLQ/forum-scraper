package sh.xana.forum.common;

import com.amazon.sqs.javamessaging.AmazonSQSExtendedClient;
import com.amazon.sqs.javamessaging.ExtendedClientConfiguration;
import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.AmazonWebServiceRequest;
import com.amazonaws.ClientConfiguration;
import com.amazonaws.retry.PredefinedRetryPolicies;
import com.amazonaws.retry.PredefinedRetryPolicies.SDKDefaultRetryCondition;
import com.amazonaws.retry.RetryPolicy;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.AmazonS3Exception;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;
import com.amazonaws.services.sqs.model.CreateQueueRequest;
import com.amazonaws.services.sqs.model.DeleteMessageBatchRequestEntry;
import com.amazonaws.services.sqs.model.ListQueuesResult;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.QueueDoesNotExistException;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import com.amazonaws.services.sqs.model.ReceiveMessageResult;
import com.amazonaws.services.sqs.model.SendMessageBatchRequestEntry;
import java.io.Closeable;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sh.xana.forum.common.ipc.IScraperRequest;
import sh.xana.forum.common.ipc.ScraperDownload;
import sh.xana.forum.common.ipc.ScraperUpload;

public class SqsManager implements Closeable {
  private static final Logger log = LoggerFactory.getLogger(SqsManager.class);

  public static final int QUEUE_SIZE = 10;
  public static final String QUEUE_DOWNLOAD_PREFIX = "forumDownload-";
  private final AmazonSQSExtendedClient sqsClient;
  private final URI uploadQueueUrl;

  private final List<URI> cachedDownloadQueueUrls = new ArrayList<>();

  public SqsManager(CommonConfig config) {
    if (!config.isDebugMode()) {
      ClientConfiguration clientConfiguration =
          new ClientConfiguration()
              .withRetryPolicy(
                  new RetryPolicy(
                      new MyRetryCondition(),
                      PredefinedRetryPolicies.DEFAULT_BACKOFF_STRATEGY,
                      // aggressive retry forever due to network errors
                      50,
                      true));
      AmazonS3 s3 =
          AmazonS3ClientBuilder.standard().withClientConfiguration(clientConfiguration).build();
      AmazonSQS sqsClientBackend =
          AmazonSQSClientBuilder.standard().withClientConfiguration(clientConfiguration).build();

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
      ListQueuesResult listQueuesResult = sqsClient.listQueues(QUEUE_DOWNLOAD_PREFIX);
      cachedDownloadQueueUrls.clear();
      for (String queue : listQueuesResult.getQueueUrls()) {
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
                    .getQueueAttributes(queueUrl.toString(), List.of("ApproximateNumberOfMessages"))
                    .getAttributes()
                    .get("ApproximateNumberOfMessages"));
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
    final CreateQueueRequest createQueueRequest =
        new CreateQueueRequest().withQueueName(QUEUE_DOWNLOAD_PREFIX + domain);
    return sqsClient.createQueue(createQueueRequest).getQueueUrl();
  }

  public void deleteQueue(URI uri) {
    sqsClient.deleteQueue(uri.toString());
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
          sqsClient.sendMessage(queueUrl.toString(), json);
        }
      } else {
        List<SendMessageBatchRequestEntry> messages = new ArrayList<>();
        for (IScraperRequest entry : sends) {
          String json = Utils.jsonMapper.writeValueAsString(entry);
          messages.add(
              new SendMessageBatchRequestEntry()
                  .withMessageBody(json)
                  // TODO: Need to force unique id's because getMessageId is duplicated?
                  .withId(UUID.randomUUID().toString()));
          if (messages.size() == 10) {
            sqsClient.sendMessageBatch(queueUrl.toString(), messages);
            messages.clear();
          }
        }
        if (!messages.isEmpty()) {
          sqsClient.sendMessageBatch(queueUrl.toString(), messages);
        }
      }
    } catch (Exception e) {
      throw new RuntimeException("Failed to process for queue " + queueUrl, e);
    }
  }

  private <T> List<RecieveRequest<T>> receive(URI queueUrl, Class<T> clazz) {
    try {
      Exception receiveException = null;
      ReceiveMessageResult receiveMessageResult = null;
      for (int i = 0; i < 5; i++) {
        try {
          // given numbers are maximum supported
          ReceiveMessageRequest receiveMessageRequest =
              new ReceiveMessageRequest()
                  .withQueueUrl(queueUrl.toString())
                  .withMaxNumberOfMessages(10)
                  .withWaitTimeSeconds(20);

          receiveMessageResult = sqsClient.receiveMessage(receiveMessageRequest);
          break;
        } catch (AmazonServiceException e) {
          receiveException = e;
          if (e.getCause() instanceof AmazonS3Exception) {
            log.warn(
                "S3 failure for queue "
                    + queueUrl
                    + ", retry "
                    + i
                    + " - "
                    + e.getMessage()
                    + " - "
                    + e.getCause().getMessage());
          } else {
            throw e;
          }
        }
      }
      if (receiveMessageResult == null) {
        throw new RuntimeException("Final fail", receiveException);
      }

      List<RecieveRequest<T>> result = new ArrayList<>();
      for (Message message : receiveMessageResult.getMessages()) {
        result.add(
            new RecieveRequest<>(message, Utils.jsonMapper.readValue(message.getBody(), clazz)));
      }
      return result;
    } catch (Exception e) {
      throw new RuntimeException("Failed to process for queue " + queueUrl, e);
    }
  }

  public void deleteQueueMessage(URI queueUrl, List<RecieveRequest<ScraperUpload>> message) {
    sqsClient.deleteMessageBatch(
        queueUrl.toString(),
        message.stream()
            .map(RecieveRequest::message)
            // TODO: Need to force unique id's because getMessageId is duplicated?
            .map(
                e ->
                    new DeleteMessageBatchRequestEntry(
                        UUID.randomUUID().toString(), e.getReceiptHandle()))
            .collect(Collectors.toList()));
  }

  @Override
  public void close() {
    log.info("closing SqsManager");
    sqsClient.shutdown();
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

  /** Extends PredefinedRetryPolicies.DEFAULT_RETRY_CONDITION */
  public static class MyRetryCondition extends SDKDefaultRetryCondition {

    @Override
    public boolean shouldRetry(
        AmazonWebServiceRequest originalRequest,
        AmazonClientException exception,
        int retriesAttempted) {
      boolean result = super.shouldRetry(originalRequest, exception, retriesAttempted);
      log.warn(
          "Retry {} continue {} due to exception {}",
          retriesAttempted,
          result,
          ExceptionUtils.getRootCauseMessage(exception));
      return result;
    }
  }
}
