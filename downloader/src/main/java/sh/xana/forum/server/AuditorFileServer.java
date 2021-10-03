package sh.xana.forum.server;

import com.github.luben.zstd.Zstd;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AuditorFileServer {
  private static final Logger log = LoggerFactory.getLogger(AuditorFileServer.class);
  private static final int UUID_STRING_LENGTH = UUID.randomUUID().toString().length();
  private static final int PORT = 6666;

  public static void main(String[] args) throws IOException {
    new AuditorFileServer().start();
  }

  private final ServerSocket socket;
  private final ServerConfig config;

  public AuditorFileServer() throws IOException {
    config = new ServerConfig();
    socket = new ServerSocket(PORT);
  }

  public void start() throws IOException {
    log.info("running on port " + PORT);
    int clientCounter = 0;
    while (true) {
      Socket client = socket.accept();
      log.info("New client {}", client.getInetAddress().getHostAddress());

      Thread thread = new Thread(() -> handleClient(client));
      thread.setName("client" + (clientCounter++));
      thread.setDaemon(true);
      thread.start();
    }
  }

  public void handleClient(Socket client) {
    try {
      InputStream in = client.getInputStream();
      OutputStream out = client.getOutputStream();

      DataOutputStream dataOut = new DataOutputStream(out);
      while (true) {
        String requestIdStr =
            new String(IOUtils.readFully(in, UUID_STRING_LENGTH), StandardCharsets.UTF_8);
        UUID requestId;
        try {
          requestId = UUID.fromString(requestIdStr);
        } catch (IllegalArgumentException e) {
          log.error("failed on '{}'", requestIdStr);
          throw e;
        }
        Path requestPath = config.getPagePath(requestId);

        byte[] responseRaw = Files.readAllBytes(requestPath);
        byte[] responseCompressed = Zstd.compress(responseRaw);

        dataOut.writeInt(responseRaw.length);
        dataOut.writeInt(responseCompressed.length);
        dataOut.write(responseCompressed);
      }
    } catch (Exception e) {
      throw new RuntimeException("fail", e);
    }
  }

  public static class Client implements AutoCloseable {
    private final Socket socket;
    private final DataInputStream dataIn;
    private final OutputStream out;

    public Client() throws IOException {
      socket = new Socket("192.168.66.41", PORT);

      dataIn = new DataInputStream(socket.getInputStream());
      out = socket.getOutputStream();
    }

    public byte[] request(UUID id) throws IOException {
      out.write(id.toString().getBytes(StandardCharsets.UTF_8));

      int rawLength = dataIn.readInt();
      int compressedLength = dataIn.readInt();
      // log.info("raw {} compressed {} for {}", rawLength, compressedLength, id);

      byte[] compressedData = new byte[compressedLength];
      dataIn.readFully(compressedData);
      byte[] rawData = Zstd.decompress(compressedData, rawLength);
      return rawData;
    }

    @Override
    public void close() throws Exception {
      socket.close();
    }
  }
}
