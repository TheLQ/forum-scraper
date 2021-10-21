package sh.xana.forum.common;

import software.amazon.awssdk.services.sqs.model.Message;

public record RecieveRequest<T>(Message message, T obj) {}
