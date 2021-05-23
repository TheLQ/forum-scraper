package sh.xana.forum.client;

public class ClientMain {
  public static void main(String[] args) {
    DownloadNode downloader = new DownloadNode(null);
    //        downloader.startThread();
    downloader.refillQueue();
  }
}
