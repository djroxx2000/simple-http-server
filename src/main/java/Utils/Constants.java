package Utils;

public final class Constants {
  private Constants() throws Exception {
    throw new Exception("Constants cannot be instantiated");
  }

  public final static int PORT = 4221;
  public final static int THREAD_POOL_SIZE = 10;

  public final static String RESPONSE_VERSION = "HTTP/1.1 ";
  public final static String SUCCESS = "200 OK";
  public final static String NOT_FOUND = "404 Not Found";
  public final static String CREATED = "201 Created";

  public static final String POST = "POST";
  public static final String GET = "GET";

  public final static String USER_AGENT = "user-agent";
  public final static String CONTENT_LENGTH = "content-length";
  public final static String ACCEPT_ENCODING = "accept-encoding";
  public final static String CONTENT_ENCODING = "content-encoding";
  public final static String CONTENT_TYPE = "content-type";

  public final static String[] SUPPORTED_ENCODING = {"gzip"};
}