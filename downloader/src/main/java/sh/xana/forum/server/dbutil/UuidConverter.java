package sh.xana.forum.server.dbutil;

import java.nio.ByteBuffer;
import java.util.UUID;
import org.jooq.Converter;

/**
 * For some reason, forcing the type to UUID doesn't make jOOq automatically convert, so need our
 * own converter.
 */
public class UuidConverter implements Converter<byte[], UUID> {

  @Override
  public UUID from(byte[] databaseObject) {
    // Avoid breaking ByteBuffer.wrap
    // We never have null UUIDs, maybe it's from object init?
    if (databaseObject == null) {
      return null;
    }
    return uuidFromBytes(databaseObject);
  }

  @Override
  public byte[] to(UUID userObject) {
    // Avoid breaking ByteBuffer.wrap
    // This REALLY should never be null, wtf jOOq
    if (userObject == null) {
      return null;
    }
    return uuidAsBytes(userObject);
  }

  @Override
  public Class<byte[]> fromType() {
    return byte[].class;
  }

  @Override
  public Class<UUID> toType() {
    return UUID.class;
  }

  private static UUID uuidFromBytes(byte[] bytes) {
    ByteBuffer bb = ByteBuffer.wrap(bytes);
    long firstLong = bb.getLong();
    long secondLong = bb.getLong();
    return new UUID(firstLong, secondLong);
  }

  private static byte[] uuidAsBytes(UUID uuid) {
    ByteBuffer bb = ByteBuffer.wrap(new byte[16]);
    bb.putLong(uuid.getMostSignificantBits());
    bb.putLong(uuid.getLeastSignificantBits());
    return bb.array();
  }
}
