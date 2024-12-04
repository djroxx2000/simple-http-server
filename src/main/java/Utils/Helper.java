package Utils;

import java.util.Map;

public final class Helper {
  private Helper() throws Exception {
    throw new Exception("Helper cannot be instantiated");
  }

  public static String getBasePath(final String[] serverArgs) {
    for (int i = 0; i < serverArgs.length; ++i) {
      if (serverArgs[i].equals("--directory") && i + 1 < serverArgs.length) {
        return serverArgs[i + 1];
      }
    }
    return "/tmp/";
  }

  public static StringBuilder getHeaderString(Map<String, String> headers) {
    StringBuilder headerBuilder = new StringBuilder();
    headers.keySet().forEach((headerKey) -> headerBuilder.append(headerKey).append(": ").append(headers.get(headerKey)).append("\r\n"));
    return headerBuilder;
  }
}