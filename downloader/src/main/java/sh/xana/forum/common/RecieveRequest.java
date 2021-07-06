package sh.xana.forum.common;

import com.amazonaws.services.sqs.model.Message;

public record RecieveRequest<T>(Message message, T obj) {}
