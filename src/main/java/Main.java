import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.GZIPOutputStream;

public class Main {
  private final static int PORT = 4221;
  private final static int THREAD_POOL_SIZE = 10;

  private final static String RESPONSE_VERSION = "HTTP/1.1 ";
  private final static String SUCCESS = "200 OK";
  private final static String NOT_FOUND = "404 Not Found";
  private final static String CREATED = "201 Created";

  private static final String POST = "POST";
  private static final String GET = "GET";

  private final static String USER_AGENT = "user-agent";
  private final static String CONTENT_LENGTH = "content-length";
  private final static String ACCEPT_ENCODING = "accept-encoding";
  private final static String CONTENT_ENCODING = "content-encoding";
  private final static String CONTENT_TYPE = "content-type";

  private final static String[] SUPPORTED_ENCODING = {"gzip"};

  private static String[] serverArgs;

  public static class ResponseBody {
    String httpVersion;
    String respStatus;
    Map<String, String> respHeaders;
    String respBody;

    ResponseBody(String version, String status, Map<String, String> headers, String response) {
      this.httpVersion = version;
      this.respStatus = status;
      this.respHeaders = headers;
      this.respBody = response;
    }
  }

  public static void main(String[] args) {
    serverArgs = args;
    try (ServerSocket serverSocket = new ServerSocket(PORT); ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE)) {
      // Since the tester restarts your program quite often, setting SO_REUSEADDR
      // ensures that we don't run into 'Address already in use' errors
      serverSocket.setReuseAddress(true);

      while (true) {
        Socket clientSocket = serverSocket.accept(); // Wait for connection from client.
        System.out.println("Accepted incoming socket connection: " + clientSocket.getRemoteSocketAddress());

        executor.submit(() -> handleIncomingConnection(clientSocket));
      }

    } catch (IOException e) {
      System.out.println("IOException: " + e.getMessage());
    }
  }

  public static void handleIncomingConnection(Socket clientSocket) {
    try (BufferedReader socketReadBuffer = new BufferedReader(new InputStreamReader(clientSocket.getInputStream())); PrintWriter socketWriter = new PrintWriter(clientSocket.getOutputStream(), true)) {
      String requestLine = socketReadBuffer.readLine();
      if (requestLine == null) {
        System.out.println("Received preflight / handshake request. Closing socket " + clientSocket.getRemoteSocketAddress());
        clientSocket.close();
        return;
      }
      String[] requestHTTPTokens = requestLine.split(" ");
      Map<String, String> headers = new HashMap<>();
      String nextLine;
      while ((nextLine = socketReadBuffer.readLine()) != null) {
        if (nextLine.isEmpty()) {
          break;
        }
        final int headerSplitIndex = nextLine.indexOf(": ");
        final String headerKey = nextLine.substring(0, headerSplitIndex).toLowerCase();
        final String headerValue = nextLine.substring(headerSplitIndex + 2);
        headers.put(headerKey, headerValue);
      }
      System.out.println("Request received: " + Arrays.toString(requestHTTPTokens) + "\n" + headers);

      if (requestHTTPTokens.length == 3) {
        String reqBody = null;
        if (requestHTTPTokens[0].equals(POST)) {
          if (headers.get(CONTENT_LENGTH) == null) {
            throw new IOException("POST requests must send a content-length header");
          }
          int reqBodySize = Integer.parseInt(headers.get(CONTENT_LENGTH));
          char[] reqBodyBuffer = new char[reqBodySize];
          socketReadBuffer.read(reqBodyBuffer, 0, reqBodySize);
          reqBody = new String(reqBodyBuffer);
        }
        final String path = requestHTTPTokens[1];
        if (path.equals("/")) {
          socketWriter.println(RESPONSE_VERSION + SUCCESS + "\r\n\r\n");
        } else if (path.startsWith("/echo/")) {
          final String response = createResponse(headers, reqBody, handleEchoPath(path));
          socketWriter.println(response);
        } else if (path.equals("/user-agent")) {
          final String response = createResponse(headers, reqBody, returnHeader(headers, USER_AGENT));
          socketWriter.println(response);
        } else if (path.startsWith("/files/")) {
          if (requestHTTPTokens[0].equals(GET)) {
            final String response = createResponse(headers, reqBody, returnFileContent(path));
            socketWriter.println(response);
          } else if (requestHTTPTokens[0].equals(POST)) {
            final String response = createResponse(headers, reqBody, createFile(path, reqBody));
            socketWriter.println(response);
          }
        } else {
          socketWriter.println(RESPONSE_VERSION + NOT_FOUND + "\r\n\r\n");
        }
        clientSocket.close();
      } else {
        throw new IOException("Invalid HTTP Request Format");
      }
    } catch (IOException e) {
      System.out.println("IOException: " + e.getMessage());
    }
  }

  public static ResponseBody handleEchoPath(final String path) throws IOException {
    final String echoResp = path.substring(6);
    Map<String, String> headers = new HashMap<>();
    headers.put(CONTENT_TYPE, "text/plain");
    return new ResponseBody(RESPONSE_VERSION, SUCCESS, headers, echoResp);
  }

  public static ResponseBody returnHeader(final Map<String, String> headerMap, final String respHeader) throws IOException {
    if (headerMap.get(respHeader) != null) {
      Map<String, String> headers = new HashMap<>();
      headers.put(CONTENT_TYPE, "text/plain");
      return new ResponseBody(RESPONSE_VERSION, SUCCESS, headers, headerMap.get(respHeader));
    }
    throw new IOException("User Agent Path request received without user-agent header");
  }

  public static ResponseBody returnFileContent(final String path) throws IOException {
    final String filename = path.substring(7);
    if (!filename.isEmpty()) {
      File file = new File(getBasePath(), filename);
      if (file.exists()) {
        byte[] fileData = Files.readAllBytes(file.toPath());
        Map<String, String> headers = new HashMap<>();
        headers.put(CONTENT_TYPE, "application/octet-stream");
        return new ResponseBody(RESPONSE_VERSION, SUCCESS, headers, new String(fileData));
      } else {
        return new ResponseBody(RESPONSE_VERSION, NOT_FOUND, null, "");
      }
    }
    throw new IOException("File path is malformed");
  }

  public static ResponseBody createFile(final String path, final String reqBody) throws IOException {
    final String filename = path.substring(7);
    if (!filename.isEmpty()) {
      File file = new File(getBasePath(), filename);
      if (!file.exists()) {
        Files.createFile(Path.of(getBasePath() + filename));
      }
      Files.writeString(file.toPath(), reqBody);
      return new ResponseBody(RESPONSE_VERSION, CREATED, null, "");
    }
    throw new IOException("File path is malformed");
  }

  private static String getBasePath() {
    for (int i = 0; i < serverArgs.length; ++i) {
      if (serverArgs[i].equals("--directory") && i + 1 < serverArgs.length) {
        return serverArgs[i + 1];
      }
    }
    return "/tmp/";
  }

  private static String createResponse(Map<String, String> reqHeaderMap, String reqBody, ResponseBody response) throws IOException {
    final String acceptEncoding = reqHeaderMap.get(ACCEPT_ENCODING);
    if (response.respHeaders == null) {
      response.respHeaders = new HashMap<>();
    }
    if (acceptEncoding != null) {
      String[] encodings = acceptEncoding.split(", ");
      final List<String> supportedEncodings = List.of(SUPPORTED_ENCODING);
      // OR: Stream.of(SUPPORTED_ENCODING).anyMatch(encoding::equals)
      String respEncoding = Arrays.stream(encodings).filter(supportedEncodings::contains).findFirst().orElse(null);
      if (respEncoding != null) {
        response.respHeaders.put(CONTENT_ENCODING, respEncoding);
//        if (respEncoding.equalsIgnoreCase("gzip")) {
//          var responseBuffer = new ByteArrayOutputStream(response.respBody.length());
//          var compressor = new GZIPOutputStream(responseBuffer);
//          System.out.println("Response size before compression: " + response.respBody.length());
//          compressor.write(response.respBody.getBytes(StandardCharsets.UTF_8));
//          response.respBody = responseBuffer.toString();
//          System.out.println("Response size after compression: " + response.respBody.length());
//        }
      }
    }
    response.respHeaders.put(CONTENT_LENGTH, String.valueOf(response.respBody.length()));
    System.out.println(response.httpVersion + response.respStatus + "\r\n" + getHeaderString(response.respHeaders) + "\r\n" + response.respBody);
    return response.httpVersion + response.respStatus + "\r\n" + getHeaderString(response.respHeaders) + "\r\n" + response.respBody;
  }

  private static StringBuilder getHeaderString(Map<String, String> headers) {
    StringBuilder headerBuilder = new StringBuilder();
    headers.keySet().forEach((headerKey) -> headerBuilder.append(headerKey).append(": ").append(headers.get(headerKey)).append("\r\n"));
    return headerBuilder;
  }
}
