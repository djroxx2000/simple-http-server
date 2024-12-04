package Models;

import java.util.Map;

public final class ResponseBody {
  public String httpVersion;
  public String respStatus;
  public Map<String, String> respHeaders;
  public byte[] respBody;

  public ResponseBody(String version, String status, Map<String, String> headers, byte[] response) {
    this.httpVersion = version;
    this.respStatus = status;
    this.respHeaders = headers;
    this.respBody = response;
  }
}